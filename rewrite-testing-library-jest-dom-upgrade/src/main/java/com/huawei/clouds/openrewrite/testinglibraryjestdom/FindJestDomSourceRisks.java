package com.huawei.clouds.openrewrite.testinglibraryjestdom;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Marks source constructs whose runner, export, type, or matcher semantics need an owner decision. */
public final class FindJestDomSourceRisks extends Recipe {
    private static final Map<String, String> DEPRECATED_MATCHERS = Map.of(
            "toBeEmpty", "toBeEmptyDOMElement after checking whitespace semantics",
            "toBeInTheDOM", "toBeInTheDocument, toContainElement, or an HTMLElement assertion after checking detached-node semantics",
            "toHaveDescription", "toHaveAccessibleDescription",
            "toHaveErrorMessage", "toHaveAccessibleErrorMessage");

    @Override
    public String getDisplayName() {
        return "Find @testing-library/jest-dom 6 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unpublished deep entries, invalid public-entry import shapes, runner-entry mismatches, " +
               "JavaScript setup leaves with TypeScript augmentation risk, and deprecated matcher semantics.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean active;
            private boolean executableConfig;
            private boolean javaScriptSetup;
            private boolean vitestRuntime;
            private boolean jestGlobalsRuntime;
            private Set<String> sideEffectEntries = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean oldActive = active;
                boolean oldConfig = executableConfig;
                boolean oldSetup = javaScriptSetup;
                boolean oldVitest = vitestRuntime;
                boolean oldJestGlobals = jestGlobalsRuntime;
                Set<String> oldEntries = sideEffectEntries;
                active = JestDomSupport.isProjectPath(cu.getSourcePath());
                executableConfig = JestDomSupport.isExecutableConfig(cu.getSourcePath());
                javaScriptSetup = JestDomSupport.isJavaScriptSetupLeaf(cu.getSourcePath());
                vitestRuntime = false;
                jestGlobalsRuntime = false;
                sideEffectEntries = new HashSet<>();
                if (active) scanRunners(cu, ctx);
                JS.CompilationUnit visited = active ? super.visitJsCompilationUnit(cu, ctx) : cu;
                active = oldActive;
                executableConfig = oldConfig;
                javaScriptSetup = oldSetup;
                vitestRuntime = oldVitest;
                jestGlobalsRuntime = oldJestGlobals;
                sideEffectEntries = oldEntries;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!active || !(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                String module = JestDomSupport.moduleName(visited);
                if (!JestDomSupport.isPackageModule(module)) return visited;
                String message = importShapeRisk(visited, module);
                if (message == null && javaScriptSetup && visited.getImportClause() == null &&
                    JestDomSupport.PUBLIC_ENTRIES.contains(module) && !module.endsWith("/matchers") &&
                    !module.endsWith("/package.json")) {
                    message = "If this project relies on global matcher types, the official v6 setup must be a .ts file included by tsconfig; this JavaScript setup leaf preserves runtime behavior only";
                }
                return message == null ? visited :
                        visited.withModuleSpecifier(SearchResult.found(literal, message));
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                if (!active || !DEPRECATED_MATCHERS.containsKey(JestDomSupport.importedName(visited))) return visited;
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                if (declaration == null || !(JestDomSupport.PACKAGE + "/matchers")
                        .equals(JestDomSupport.moduleName(declaration))) return visited;
                String matcher = JestDomSupport.importedName(visited);
                return SearchResult.found(visited,
                        "This named jest-dom matcher remains deprecated in 6.9.1; prefer " +
                        DEPRECATED_MATCHERS.get(matcher) + " after preserving its observable semantics");
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!active || !(visited.getValue() instanceof String module) ||
                    !JestDomSupport.isPackageModule(module) || !moduleReference(visited)) return visited;
                if (!module.equals(JestDomSupport.migratedModule(module))) {
                    return SearchResult.found(visited,
                            "This jest-dom v5 entry is removed or hidden by the v6 export map; run the deterministic entry migration and then select root, /jest-globals, /vitest, or /matchers by ownership");
                }
                if (!JestDomSupport.PUBLIC_ENTRIES.contains(module)) {
                    return SearchResult.found(visited,
                            "jest-dom 6 exports only root, /jest-globals, /matchers, /vitest, and package.json; replace this deep/internal path with an owned public entry and verify matcher behavior");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!active || !DEPRECATED_MATCHERS.containsKey(visited.getSimpleName()) ||
                    !originatesFromExpect(visited.getSelect())) return visited;
                return SearchResult.found(visited,
                        "This jest-dom matcher remains deprecated in 6.9.1; prefer " +
                        DEPRECATED_MATCHERS.get(visited.getSimpleName()) + " and add positive, negative, detached-node, ARIA, and whitespace assertions before changing semantics");
            }

            private String importShapeRisk(JS.Import declaration, String module) {
                JS.ImportClause clause = declaration.getImportClause();
                if (clause == null && sideEffectEntries.size() > 1 &&
                    (JestDomSupport.PACKAGE.equals(module) || module.endsWith("/jest-globals") ||
                     module.endsWith("/vitest"))) {
                    return "This file loads multiple jest-dom runner side-effect entries; select exactly one expect owner (Jest globals, @jest/globals, or Vitest)";
                }
                if ((JestDomSupport.PACKAGE.equals(module) || module.endsWith("/jest-globals") ||
                     module.endsWith("/vitest")) && clause != null) {
                    return "This jest-dom entry is side-effect-only in the v6 published bundle; remove runtime bindings or import named matchers from /matchers explicitly";
                }
                if (module.endsWith("/matchers") && (clause == null || clause.getName() != null)) {
                    return "The v6 /matchers entry has named exports and no default side effect; use a namespace or named import before calling expect.extend";
                }
                if (JestDomSupport.PACKAGE.equals(module) && vitestRuntime != jestGlobalsRuntime) {
                    return vitestRuntime ?
                            "This file owns Vitest expect; use @testing-library/jest-dom/vitest instead of the Jest-global root entry" :
                            "This file owns @jest/globals expect; use @testing-library/jest-dom/jest-globals instead of the Jest-global root entry";
                }
                if ((JestDomSupport.PACKAGE.equals(module) || module.endsWith("/vitest") ||
                     module.endsWith("/jest-globals")) && vitestRuntime && jestGlobalsRuntime) {
                    return "This file imports both Vitest and @jest/globals runtimes; choose one expect owner before selecting a jest-dom side-effect entry";
                }
                if (module.endsWith("/vitest") && jestGlobalsRuntime && !vitestRuntime) {
                    return "This file owns @jest/globals but loads the Vitest entry; select /jest-globals";
                }
                if (module.endsWith("/jest-globals") && vitestRuntime && !jestGlobalsRuntime) {
                    return "This file owns Vitest but loads the @jest/globals entry; select /vitest";
                }
                return null;
            }

            private boolean moduleReference(J.Literal literal) {
                JS.Import imported = getCursor().firstEnclosing(JS.Import.class);
                if (imported != null && imported.getModuleSpecifier() != null &&
                    imported.getModuleSpecifier().getId().equals(literal.getId())) return true;
                JS.ExportDeclaration exported = getCursor().firstEnclosing(JS.ExportDeclaration.class);
                if (exported != null && exported.getModuleSpecifier() != null &&
                    exported.getModuleSpecifier().getId().equals(literal.getId())) return true;
                J.MethodInvocation method = getCursor().firstEnclosing(J.MethodInvocation.class);
                if (method != null && method.getSelect() == null &&
                    Set.of("require", "import").contains(method.getSimpleName()) &&
                    method.getArguments().stream().anyMatch(argument -> argument.getId().equals(literal.getId()))) {
                    return true;
                }
                JS.FunctionCall function = getCursor().firstEnclosing(JS.FunctionCall.class);
                if (function != null && Set.of("require", "import").contains(function.getFunction().toString().trim()) &&
                    function.getArguments().stream().anyMatch(argument -> argument.getId().equals(literal.getId()))) {
                    return true;
                }
                JS.ImportType importType = getCursor().firstEnclosing(JS.ImportType.class);
                if (importType != null && importType.getArgumentAndAttributes().stream()
                        .anyMatch(argument -> argument.getId().equals(literal.getId()))) return true;
                return executableConfig;
            }

            private boolean originatesFromExpect(Expression expression) {
                if (expression instanceof J.MethodInvocation method) {
                    return method.getSelect() == null && "expect".equals(method.getSimpleName());
                }
                if (expression instanceof J.FieldAccess field) return originatesFromExpect(field.getTarget());
                if (expression instanceof J.Parentheses<?> parentheses &&
                    parentheses.getTree() instanceof Expression nested) return originatesFromExpect(nested);
                return false;
            }

            private void scanRunners(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (!JestDomSupport.runtimeImport(visited)) return visited;
                        String module = JestDomSupport.moduleName(visited);
                        runner(module);
                        if (visited.getImportClause() == null && JestDomSupport.isPackageModule(module) &&
                            (JestDomSupport.PACKAGE.equals(module) || module.endsWith("/jest-globals") ||
                             module.endsWith("/vitest"))) sideEffectEntries.add(module);
                        return visited;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation,
                                                                    ExecutionContext scanCtx) {
                        J.MethodInvocation visited = super.visitMethodInvocation(invocation, scanCtx);
                        runner(JestDomSupport.requireModule(visited));
                        return visited;
                    }

                    private void runner(String module) {
                        if ("vitest".equals(module)) vitestRuntime = true;
                        else if ("@jest/globals".equals(module)) jestGlobalsRuntime = true;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
