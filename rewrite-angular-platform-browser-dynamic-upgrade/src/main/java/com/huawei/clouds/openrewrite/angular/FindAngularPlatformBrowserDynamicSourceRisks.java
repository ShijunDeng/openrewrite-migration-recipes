package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Mark JIT, compiler, testing, dynamic component, and bootstrap work requiring project context. */
public final class FindAngularPlatformBrowserDynamicSourceRisks extends Recipe {
    private static final Set<String> COMPILER_METHODS = Set.of(
            "compileModuleSync", "compileModuleAsync", "compileModuleAndAllComponentsSync",
            "compileModuleAndAllComponentsAsync", "clearCache", "clearCacheFor");
    private static final Set<String> COMPONENT_FACTORY_METHODS = Set.of(
            "resolveComponentFactory", "createComponent");

    @Override
    public String getDisplayName() {
        return "Find Angular platform-browser-dynamic source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark remaining browser-dynamic imports/calls, runtime Compiler use, legacy testing, entryComponents, " +
               "and dynamic component factory boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> dynamicPlatforms = Set.of();
            private Set<String> compilerTypes = Set.of();
            private Set<String> compilerVariables = Set.of();
            private Set<String> componentFactoryTypes = Set.of();
            private Set<String> componentFactoryVariables = Set.of();
            private boolean hasNgModule;
            private boolean hasRuntimeCompiler;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> oldDynamic = dynamicPlatforms;
                Set<String> oldCompilerTypes = compilerTypes;
                Set<String> oldCompilerVariables = compilerVariables;
                Set<String> oldFactoryTypes = componentFactoryTypes;
                Set<String> oldFactoryVariables = componentFactoryVariables;
                boolean oldNgModule = hasNgModule;
                boolean oldRuntimeCompiler = hasRuntimeCompiler;
                dynamicPlatforms = new HashSet<>();
                compilerTypes = new HashSet<>();
                compilerVariables = new HashSet<>();
                componentFactoryTypes = new HashSet<>();
                componentFactoryVariables = new HashSet<>();
                hasNgModule = false;
                hasRuntimeCompiler = false;
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                dynamicPlatforms = oldDynamic;
                compilerTypes = oldCompilerTypes;
                compilerVariables = oldCompilerVariables;
                componentFactoryTypes = oldFactoryTypes;
                componentFactoryVariables = oldFactoryVariables;
                hasNgModule = oldNgModule;
                hasRuntimeCompiler = oldRuntimeCompiler;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = MigrateDeterministicAngularPlatformBrowserDynamicSource.moduleName(visited);
                if (MigrateDeterministicAngularPlatformBrowserDynamicSource.DYNAMIC_MODULE.equals(module)) {
                    addAlias(visited, "platformBrowserDynamic", dynamicPlatforms);
                    if (imports(visited, "JitCompilerFactory")) {
                        hasRuntimeCompiler = true;
                        return SearchResult.found(visited, "JitCompilerFactory is deprecated; Ivy JIT does not require this factory, so remove private compiler ownership and verify AOT/JIT test paths");
                    }
                    return SearchResult.found(visited, "platformBrowserDynamic is deprecated; use platformBrowser for AOT output, or add an explicit @angular/compiler side effect only for a proven non-CLI JIT runtime");
                }
                if (module.startsWith(MigrateDeterministicAngularPlatformBrowserDynamicSource.DYNAMIC_MODULE + "/")) {
                    return SearchResult.found(visited, "platform-browser-dynamic deep/testing entry found; migrate production bootstrap and verify TestBed environment ownership before removing the package");
                }
                if ("@angular/compiler".equals(module)) {
                    hasRuntimeCompiler = true;
                    return SearchResult.found(visited, "Runtime @angular/compiler coupling prevents a pure AOT migration; precompile templates or isolate an explicitly owned JIT boundary with CSP and bundle tests");
                }
                if ("@angular/core".equals(module)) {
                    addAlias(visited, "Compiler", compilerTypes);
                    addAlias(visited, "CompilerFactory", compilerTypes);
                    addAlias(visited, "ComponentFactoryResolver", componentFactoryTypes);
                    hasNgModule |= imports(visited, "NgModule");
                    if (imports(visited, "COMPILER_OPTIONS")) {
                        hasRuntimeCompiler = true;
                        return SearchResult.found(visited, "COMPILER_OPTIONS customizes runtime JIT compilation; move behavior to build-time compilation or explicitly own compiler providers and their unsupported surface");
                    }
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                    ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
                String type = typeName(visited.getTypeExpression());
                if (compilerTypes.contains(type)) {
                    visited.getVariables().forEach(variable -> compilerVariables.add(variable.getSimpleName()));
                }
                if (componentFactoryTypes.contains(type)) {
                    visited.getVariables().forEach(variable -> componentFactoryVariables.add(variable.getSimpleName()));
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (visited.getFunction() instanceof J.Identifier identifier &&
                    dynamicPlatforms.contains(identifier.getSimpleName())) {
                    return SearchResult.found(visited, "Remaining platformBrowserDynamic call owns JIT providers; replace it with platformBrowser after proving AOT output, or explicitly import @angular/compiler for intentional JIT");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (COMPILER_METHODS.contains(visited.getSimpleName()) && ownedBy(visited.getSelect(), compilerVariables)) {
                    return SearchResult.found(visited, "Runtime Compiler API has no stable AOT equivalent; precompile the module/template or isolate and test an explicit JIT plugin boundary");
                }
                if (COMPONENT_FACTORY_METHODS.contains(visited.getSimpleName()) &&
                    ownedBy(visited.getSelect(), componentFactoryVariables)) {
                    return SearchResult.found(visited, "ComponentFactoryResolver-era dynamic creation should move to ViewContainerRef.createComponent/createComponent with an explicit EnvironmentInjector and lifecycle cleanup");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = expressionName(visited.getName());
                if (hasNgModule && "entryComponents".equals(name)) {
                    return SearchResult.found(visited, "Ivy ignores entryComponents; remove it after verifying every dynamic component call and replacing View Engine-only libraries");
                }
                if (hasRuntimeCompiler && ("template".equals(name) || "templateUrl".equals(name)) &&
                    !(visited.getInitializer() instanceof J.Literal)) {
                    return SearchResult.found(visited, "Runtime-computed component template cannot be assumed AOT-safe; replace it with precompiled components or a separately secured template engine");
                }
                return visited;
            }

            private boolean ownedBy(Expression select, Set<String> owners) {
                if (select instanceof J.Identifier identifier) {
                    return owners.contains(identifier.getSimpleName());
                }
                return select instanceof J.FieldAccess field && owners.contains(field.getSimpleName());
            }
        };
    }

    static String importedAlias(JS.Import declaration, String wanted) {
        if (declaration.getImportClause() == null ||
            !(declaration.getImportClause().getNamedBindings() instanceof JS.NamedImports named)) {
            return null;
        }
        for (JS.ImportSpecifier element : named.getElements()) {
            if (!wanted.equals(MigrateDeterministicAngularPlatformBrowserDynamicSource.importedName(element))) {
                continue;
            }
            if (element.getSpecifier() instanceof J.Identifier identifier) {
                return identifier.getSimpleName();
            }
            if (element.getSpecifier() instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
                return identifier.getSimpleName();
            }
        }
        return null;
    }

    private static boolean imports(JS.Import declaration, String wanted) {
        return importedAlias(declaration, wanted) != null;
    }

    private static void addAlias(JS.Import declaration, String wanted, Set<String> aliases) {
        String alias = importedAlias(declaration, wanted);
        if (alias != null) {
            aliases.add(alias);
        }
    }

    private static String typeName(TypeTree type) {
        if (type instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (type instanceof J.FieldAccess field) {
            return field.getSimpleName();
        }
        if (type instanceof JS.TypeTreeExpression expression) {
            return expressionName(expression.getExpression());
        }
        if (type instanceof JS.TypeInfo info) {
            return typeName(info.getTypeIdentifier());
        }
        return "";
    }

    private static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof J.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        return "";
    }
}
