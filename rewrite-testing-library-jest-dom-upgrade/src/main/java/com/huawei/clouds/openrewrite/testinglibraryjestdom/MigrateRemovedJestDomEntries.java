package com.huawei.clouds.openrewrite.testinglibraryjestdom;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Cursor;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Rewrites only exact v5 entry points with published v6 equivalents. */
public final class MigrateRemovedJestDomEntries extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate removed @testing-library/jest-dom v5 entries";
    }

    @Override
    public String getDescription() {
        return "Normalizes removed extend-expect, dist/index, dist/matchers, and extension-suffixed public " +
               "entries; same-file Vitest and @jest/globals ownership selects the corresponding v6 entry.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean active;
            private boolean vitestRuntime;
            private boolean jestGlobalsRuntime;
            private boolean executableConfig;
            private boolean vitestConfig;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean oldActive = active;
                boolean oldVitest = vitestRuntime;
                boolean oldJestGlobals = jestGlobalsRuntime;
                boolean oldConfig = executableConfig;
                boolean oldVitestConfig = vitestConfig;
                active = JestDomSupport.isProjectPath(cu.getSourcePath());
                vitestRuntime = false;
                jestGlobalsRuntime = false;
                executableConfig = JestDomSupport.isExecutableConfig(cu.getSourcePath());
                String leaf = cu.getSourcePath().getFileName() == null ? "" :
                        cu.getSourcePath().getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                vitestConfig = leaf.startsWith("vitest.config");
                if (active) scanRunners(cu, ctx);
                JS.CompilationUnit visited = active ? super.visitJsCompilationUnit(cu, ctx) : cu;
                active = oldActive;
                vitestRuntime = oldVitest;
                jestGlobalsRuntime = oldJestGlobals;
                executableConfig = oldConfig;
                vitestConfig = oldVitestConfig;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!active || !(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                String module = JestDomSupport.moduleName(visited);
                if (JestDomSupport.LEGACY_SIDE_EFFECT_ENTRIES.contains(module) &&
                    visited.getImportClause() != null) return visited;
                String replacement = JestDomSupport.migratedModule(module);
                if (JestDomSupport.PACKAGE.equals(replacement) && visited.getImportClause() == null &&
                    vitestRuntime != jestGlobalsRuntime) {
                    replacement = JestDomSupport.PACKAGE + (vitestRuntime ? "/vitest" : "/jest-globals");
                }
                return module.equals(replacement) ? visited :
                        visited.withModuleSpecifier(JestDomSupport.replaceString(literal, replacement));
            }

            @Override
            public JS.ExportDeclaration visitExportDeclaration(JS.ExportDeclaration declaration,
                                                                 ExecutionContext ctx) {
                JS.ExportDeclaration visited = super.visitExportDeclaration(declaration, ctx);
                if (!active || !(visited.getModuleSpecifier() instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module)) return visited;
                if (JestDomSupport.LEGACY_SIDE_EFFECT_ENTRIES.contains(module)) return visited;
                String replacement = JestDomSupport.migratedModule(module);
                return module.equals(replacement) ? visited :
                        visited.withModuleSpecifier(JestDomSupport.replaceString(literal, replacement));
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (!active || !"import".equals(visited.getFunction().toString().trim())) return visited;
                return visited.withArguments(migrateLoaderArguments(visited.getArguments()));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!active) return visited;
                if (!JestDomSupport.requireModule(visited).isEmpty()) {
                    return visited.withArguments(migrateLoaderArguments(visited.getArguments()));
                }
                if (visited.getSelect() == null && "import".equals(visited.getSimpleName())) {
                    return visited.withArguments(migrateLoaderArguments(visited.getArguments()));
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!active || !executableConfig || !(visited.getValue() instanceof String module)) return visited;
                String replacement = JestDomSupport.migratedModule(module);
                if (vitestConfig && setupProperty() && (JestDomSupport.PACKAGE.equals(replacement) ||
                                                        JestDomSupport.LEGACY_SIDE_EFFECT_ENTRIES.contains(module))) {
                    replacement = JestDomSupport.PACKAGE + "/vitest";
                }
                return module.equals(replacement) ? visited : JestDomSupport.replaceString(visited, replacement);
            }

            private boolean setupProperty() {
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null && !(cursor.getValue() instanceof JS.CompilationUnit)) {
                    if (cursor.getValue() instanceof JS.PropertyAssignment property &&
                        Set.of("setupFiles", "setupFilesAfterEnv").contains(JestDomSupport.propertyName(property))) {
                        return true;
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private List<Expression> migrateLoaderArguments(List<Expression> arguments) {
                if (arguments.size() != 1 || !(arguments.get(0) instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module)) return arguments;
                if (JestDomSupport.LEGACY_SIDE_EFFECT_ENTRIES.contains(module) &&
                    loaderResultIsConsumed()) return arguments;
                String replacement = JestDomSupport.migratedModule(module);
                if (JestDomSupport.PACKAGE.equals(replacement) && vitestRuntime != jestGlobalsRuntime) {
                    replacement = JestDomSupport.PACKAGE + (vitestRuntime ? "/vitest" : "/jest-globals");
                }
                if (module.equals(replacement)) return arguments;
                List<Expression> migrated = new ArrayList<>(arguments);
                migrated.set(0, JestDomSupport.replaceString(literal, replacement));
                return migrated;
            }

            /**
             * A removed side-effect entry is safe to normalize only when the loader call itself is a statement.
             * Consuming the returned module namespace changes the migration question from entry-point selection to
             * value compatibility, so those forms are deliberately left for the companion risk recipe.
             */
            private boolean loaderResultIsConsumed() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    Object value = cursor.getValue();
                    if (value instanceof JS.ExpressionStatement) return false;
                    if (value instanceof JS.CompilationUnit || value instanceof J.Block) return false;
                    if (value instanceof Statement || value instanceof J.Lambda) return true;
                    cursor = cursor.getParent();
                }
                return true;
            }

            private void scanRunners(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (!JestDomSupport.runtimeImport(visited)) return visited;
                        runner(JestDomSupport.moduleName(visited));
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
