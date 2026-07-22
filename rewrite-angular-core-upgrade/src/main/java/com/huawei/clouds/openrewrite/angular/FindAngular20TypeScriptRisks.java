package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Marks TypeScript AST nodes for Angular migrations that need project or runtime knowledge. */
public final class FindAngular20TypeScriptRisks extends Recipe {
    private static final Set<String> REMOVED_OR_CHANGED_CORE_IMPORTS = Set.of(
            "InjectFlags", "ANALYZE_FOR_ENTRY_COMPONENTS", "ComponentFactoryResolver",
            "NgModuleFactoryLoader", "ReflectiveInjector", "ReflectiveKey"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular 20 TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks imported removed APIs, optional migrations, SSR bootstrap calls, and timing-sensitive test " +
               "or rendering APIs at their exact TypeScript AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> testBedAliases = Set.of();
            private Set<String> routerAliases = Set.of();
            private Set<String> afterRenderAliases = Set.of();
            private Set<String> zonelessAliases = Set.of();
            private Set<String> bootstrapAliases = Set.of();
            private boolean hasNgModuleImport;
            private boolean hasInjectableImport;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> previous = testBedAliases;
                Set<String> previousRouters = routerAliases;
                Set<String> previousAfterRender = afterRenderAliases;
                Set<String> previousZoneless = zonelessAliases;
                Set<String> previousBootstrap = bootstrapAliases;
                boolean previousNgModule = hasNgModuleImport;
                boolean previousInjectable = hasInjectableImport;
                testBedAliases = new HashSet<>();
                routerAliases = new HashSet<>();
                afterRenderAliases = new HashSet<>();
                zonelessAliases = new HashSet<>();
                bootstrapAliases = new HashSet<>();
                hasNgModuleImport = false;
                hasInjectableImport = false;
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                testBedAliases = previous;
                routerAliases = previousRouters;
                afterRenderAliases = previousAfterRender;
                zonelessAliases = previousZoneless;
                bootstrapAliases = previousBootstrap;
                hasNgModuleImport = previousNgModule;
                hasInjectableImport = previousInjectable;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = MigrateDeterministicAngular20Source.moduleName(visited);
                if ("@angular/core/testing".equals(module)) {
                    String alias = MigrateDeterministicAngular20Source.importedAlias(visited, "TestBed");
                    if (alias != null) {
                        testBedAliases.add(alias);
                    }
                }
                if ("@angular/core".equals(module)) {
                    addAlias(visited, "provideExperimentalZonelessChangeDetection", zonelessAliases);
                    hasNgModuleImport |= MigrateDeterministicAngular20Source.importedAlias(visited, "NgModule") != null;
                    hasInjectableImport |= MigrateDeterministicAngular20Source.importedAlias(visited, "Injectable") != null;
                    String risky = firstImported(visited, REMOVED_OR_CHANGED_CORE_IMPORTS);
                    if (risky != null) {
                        return SearchResult.found(visited, "Angular 20 removed or changed " + risky + "; apply the official migration and verify DI/runtime semantics before selecting a replacement");
                    }
                    String afterRender = MigrateDeterministicAngular20Source.importedAlias(visited, "afterRender");
                    if (afterRender != null) {
                        afterRenderAliases.add(afterRender);
                        return SearchResult.found(visited, "afterRender was replaced by phase-specific rendering APIs; choose afterNextRender or afterEveryRender and verify callback timing");
                    }
                }
                if ("@angular/router".equals(module)) {
                    addAlias(visited, "Router", routerAliases);
                }
                if ("@angular/platform-browser".equals(module)) {
                    addAlias(visited, "bootstrapApplication", bootstrapAliases);
                }
                if ("@angular/common".equals(module) &&
                    MigrateDeterministicAngular20Source.importedAlias(visited, "DOCUMENT") != null) {
                    return SearchResult.found(visited, "Mixed DOCUMENT import cannot be moved without editing the remaining @angular/common bindings; split or merge imports intentionally");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String name = visited.getSimpleName();
                if ("get".equals(name) && isImportedTestBed(visited.getSelect())) {
                    return SearchResult.found(visited, "TestBed.get remains because the imported name is shadowed or ambiguous; replace the intended Angular reference with TestBed.inject");
                }
                if ("flushEffects".equals(name) && isImportedTestBed(visited.getSelect())) {
                    return SearchResult.found(visited, "TestBed.flushEffects was replaced by broader TestBed.tick behavior; verify pending effects, change detection, and timer ordering");
                }
                if ("getCurrentNavigation".equals(name) && !routerAliases.isEmpty() && isLikelyRouter(visited.getSelect())) {
                    return SearchResult.found(visited, "Router.getCurrentNavigation migration is optional; choose the currentNavigation signal and update reads/reactivity deliberately");
                }
                if (afterRenderAliases.contains(name) || zonelessAliases.contains(name)) {
                    return SearchResult.found(visited, "Angular rendering/zoneless API changed; select the stable Angular 20 API and retest scheduling and change detection");
                }
                if (bootstrapAliases.contains(name) && isServerMain() && visited.getArguments().size() < 3) {
                    return SearchResult.found(visited, "Angular 20.3 SSR requires BootstrapContext propagation; add the context parameter/import while preserving this server entry point's wrapper contract");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String name = expressionName(visited.getFunction());
                if (afterRenderAliases.contains(name) || zonelessAliases.contains(name)) {
                    return SearchResult.found(visited, "Angular rendering/zoneless API changed; select the stable Angular 20 API and retest scheduling and change detection");
                }
                if (bootstrapAliases.contains(name) && isServerMain() && visited.getArguments().size() < 3) {
                    return SearchResult.found(visited, "Angular 20.3 SSR requires BootstrapContext propagation; add the context parameter/import while preserving this server entry point's wrapper contract");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = expressionName(visited.getName());
                if ("entryComponents".equals(name) && hasNgModuleImport) {
                    return SearchResult.found(visited, "Ivy ignores entryComponents; remove it only after verifying dynamic component creation and all View Engine dependencies");
                }
                if ("providedIn".equals(name) && hasInjectableImport && visited.getInitializer() instanceof J.Literal literal &&
                    ("any".equals(literal.getValue()) || "platform".equals(literal.getValue()))) {
                    return SearchResult.found(visited, "Injector scope behavior is application-specific across this major-version jump; verify singleton and lazy-boundary lifetime");
                }
                return visited;
            }

            private boolean isImportedTestBed(Expression select) {
                return select instanceof J.Identifier &&
                       testBedAliases.contains(((J.Identifier) select).getSimpleName());
            }

            private boolean isLikelyRouter(Expression select) {
                if (select instanceof J.Identifier identifier) {
                    return "router".equals(identifier.getSimpleName()) || routerAliases.contains(identifier.getSimpleName());
                }
                return select instanceof J.FieldAccess && "router".equals(((J.FieldAccess) select).getSimpleName());
            }

            private boolean isServerMain() {
                Path path = getCursor().firstEnclosingOrThrow(JS.CompilationUnit.class).getSourcePath();
                return path.getFileName() != null && "main.server.ts".equals(path.getFileName().toString());
            }
        };
    }

    private static void addAlias(JS.Import declaration, String importedName, Set<String> aliases) {
        String alias = MigrateDeterministicAngular20Source.importedAlias(declaration, importedName);
        if (alias != null) {
            aliases.add(alias);
        }
    }

    private static String firstImported(JS.Import declaration, Set<String> candidates) {
        for (String candidate : candidates) {
            if (MigrateDeterministicAngular20Source.importedAlias(declaration, candidate) != null) {
                return candidate;
            }
        }
        return null;
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
