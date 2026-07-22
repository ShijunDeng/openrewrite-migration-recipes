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

/** Mark platform-browser APIs whose Angular 20 migration needs application or runtime context. */
public final class FindAngularPlatformBrowserTypeScriptRisks extends Recipe {
    private static final Set<String> HAMMER = Set.of(
            "HammerModule", "HammerGestureConfig", "HAMMER_GESTURE_CONFIG", "HAMMER_LOADER"
    );
    private static final Set<String> EVENT_MANAGER = Set.of(
            "EventManager", "EventManagerPlugin", "EVENT_MANAGER_PLUGINS"
    );
    private static final Set<String> BROWSER_MODULE = Set.of("BrowserModule", "BrowserTransferStateModule");
    private static final Set<String> META_AND_TOOLS = Set.of(
            "Title", "Meta", "enableDebugTools", "disableDebugTools", "provideProtractorTestingSupport",
            "REMOVE_STYLES_ON_COMPONENT_DESTROY"
    );
    private static final Set<String> SANITIZER_METHODS = Set.of(
            "bypassSecurityTrustHtml", "bypassSecurityTrustStyle", "bypassSecurityTrustScript",
            "bypassSecurityTrustUrl", "bypassSecurityTrustResourceUrl"
    );
    private static final Set<String> TRANSFER_METHODS = Set.of(
            "set", "get", "remove", "hasKey", "onSerialize", "toJson"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular 20 platform-browser TypeScript risks";
    }

    @Override
    public String getDescription() {
        return "Mark bootstrap, hydration, BrowserModule, sanitizer, event manager, Hammer, animations, " +
               "testing, SSR, metadata and transfer-state nodes that need binding and runtime decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> browserModules = Set.of();
            private Set<String> platformBrowserDynamics = Set.of();
            private Set<String> bootstrapApplications = Set.of();
            private Set<String> hydrationProviders = Set.of();
            private Set<String> sanitizerBindings = Set.of();
            private Set<String> transferStateTypes = Set.of();
            private Set<String> protractorProviders = Set.of();
            private Set<String> debugTools = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> oldBrowser = browserModules;
                Set<String> oldDynamic = platformBrowserDynamics;
                Set<String> oldBootstrap = bootstrapApplications;
                Set<String> oldHydration = hydrationProviders;
                Set<String> oldSanitizer = sanitizerBindings;
                Set<String> oldTransfer = transferStateTypes;
                Set<String> oldProtractor = protractorProviders;
                Set<String> oldDebug = debugTools;
                browserModules = new HashSet<>();
                platformBrowserDynamics = new HashSet<>();
                bootstrapApplications = new HashSet<>();
                hydrationProviders = new HashSet<>();
                sanitizerBindings = new HashSet<>();
                transferStateTypes = new HashSet<>();
                protractorProviders = new HashSet<>();
                debugTools = new HashSet<>();
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                browserModules = oldBrowser;
                platformBrowserDynamics = oldDynamic;
                bootstrapApplications = oldBootstrap;
                hydrationProviders = oldHydration;
                sanitizerBindings = oldSanitizer;
                transferStateTypes = oldTransfer;
                protractorProviders = oldProtractor;
                debugTools = oldDebug;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = MigrateDeterministicAngularPlatformBrowserSource.moduleName(visited);
                if ("@angular/platform-browser".equals(module)) {
                    collectAlias(visited, "BrowserModule", browserModules);
                    collectAlias(visited, "bootstrapApplication", bootstrapApplications);
                    collectAlias(visited, "provideClientHydration", hydrationProviders);
                    collectAlias(visited, "DomSanitizer", sanitizerBindings);
                    collectAlias(visited, "TransferState", transferStateTypes);
                    collectAlias(visited, "provideProtractorTestingSupport", protractorProviders);
                    collectAlias(visited, "enableDebugTools", debugTools);
                    collectAlias(visited, "disableDebugTools", debugTools);
                    if (MigrateDeterministicAngularPlatformBrowserSource.importsAny(visited,
                            MigrateDeterministicAngularPlatformBrowserSource.MOVED_TO_CORE) &&
                        !MigrateDeterministicAngularPlatformBrowserSource.importsOnlyMoved(visited)) {
                        return SearchResult.found(visited,
                                "Mixed platform-browser import contains APIs moved to @angular/core; split/merge imports without duplicating local bindings");
                    }
                    if (MigrateDeterministicAngularPlatformBrowserSource.importsAny(visited, HAMMER)) {
                        return SearchResult.found(visited,
                                "Angular 20 deprecates HammerJS integration; replace gesture providers/events with an owned Pointer Events or maintained library strategy");
                    }
                    if (MigrateDeterministicAngularPlatformBrowserSource.importsAny(visited, EVENT_MANAGER)) {
                        return SearchResult.found(visited,
                                "Custom EventManager integration is order, zone and DOM-runtime sensitive; verify plugin supports(), listener cleanup, SSR and zoneless behavior");
                    }
                    if (MigrateDeterministicAngularPlatformBrowserSource.importsAny(visited, BROWSER_MODULE)) {
                        return SearchResult.found(visited,
                                "BrowserModule must provide browser services only once; verify this is the root module and replace feature/lazy imports with CommonModule or standalone imports");
                    }
                    if (MigrateDeterministicAngularPlatformBrowserSource.importsAny(visited, META_AND_TOOLS)) {
                        return SearchResult.found(visited,
                                "Platform-browser metadata/debug/testability/style integration needs SSR, production and teardown review under Angular 20");
                    }
                }
                if ("@angular/core".equals(module)) {
                    collectAlias(visited, "TransferState", transferStateTypes);
                }
                if ("@angular/platform-browser-dynamic".equals(module)) {
                    collectAlias(visited, "platformBrowserDynamic", platformBrowserDynamics);
                }
                if ("@angular/platform-browser/animations".equals(module) ||
                    "@angular/platform-browser/animations/async".equals(module)) {
                    return SearchResult.found(visited,
                            "Angular 20.2 deprecates legacy animations providers/modules; migrate templates to animate.enter/leave and verify lazy animation loading");
                }
                if ("@angular/platform-browser/testing".equals(module) ||
                    "@angular/platform-browser-dynamic/testing".equals(module)) {
                    return SearchResult.found(visited,
                            "Browser test platform setup is global and timing-sensitive; align TestBed environment, teardown, compiler mode, DOM adapter and test runner for Angular 20");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String name = visited.getSimpleName();
                if (platformBrowserDynamics.contains(name)) {
                    return SearchResult.found(visited,
                            "platformBrowserDynamic is deprecated; choose platformBrowser for AOT or retain @angular/compiler explicitly for a proven JIT requirement");
                }
                if (hydrationProviders.contains(name)) {
                    return SearchResult.found(visited,
                            "Review provideClientHydration features, server/client DOM parity, event replay, incremental boundaries and direct DOM manipulation");
                }
                if (bootstrapApplications.contains(name) && isServerEntry() && visited.getArguments().size() < 3) {
                    return SearchResult.found(visited,
                            "Angular 20.3 server bootstrap must propagate BootstrapContext as the third argument without sharing a platform across requests");
                }
                if (protractorProviders.contains(name)) {
                    return SearchResult.found(visited,
                            "provideProtractorTestingSupport opts into legacy Testability; confirm the remaining E2E harness needs it or remove during runner migration");
                }
                if (debugTools.contains(name)) {
                    return SearchResult.found(visited,
                            "Angular debug tools must not leak into production or SSR; verify environment guards and replacement diagnostics");
                }
                if ("withServerTransition".equals(name) && isBrowserModule(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "BrowserModule.withServerTransition was removed; migrate APP_ID/modern SSR and hydration configuration together");
                }
                if (SANITIZER_METHODS.contains(name) && isLikelySanitizer(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "bypassSecurityTrust* disables Angular sanitization; prove the value's trust boundary and retest CSP/Trusted Types before retaining it");
                }
                if (TRANSFER_METHODS.contains(name) && isLikelyTransferState(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "TransferState crosses the server/client HTML boundary; verify stable keys, JSON-lossless values, cache lifecycle and sensitive-data exposure");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String name = expressionName(visited.getFunction());
                if (visited.getFunction() instanceof J.FieldAccess field) {
                    if (SANITIZER_METHODS.contains(field.getSimpleName()) && isLikelySanitizer(field.getTarget())) {
                        return SearchResult.found(visited,
                                "bypassSecurityTrust* disables Angular sanitization; prove the value's trust boundary and retest CSP/Trusted Types before retaining it");
                    }
                    if (TRANSFER_METHODS.contains(field.getSimpleName()) && isLikelyTransferState(field.getTarget())) {
                        return SearchResult.found(visited,
                                "TransferState crosses the server/client HTML boundary; verify stable keys, JSON-lossless values, cache lifecycle and sensitive-data exposure");
                    }
                }
                if (platformBrowserDynamics.contains(name)) {
                    return SearchResult.found(visited,
                            "platformBrowserDynamic is deprecated; choose platformBrowser for AOT or retain @angular/compiler explicitly for a proven JIT requirement");
                }
                if (hydrationProviders.contains(name)) {
                    return SearchResult.found(visited,
                            "Review provideClientHydration features, server/client DOM parity, event replay, incremental boundaries and direct DOM manipulation");
                }
                if (bootstrapApplications.contains(name) && isServerEntry() && visited.getArguments().size() < 3) {
                    return SearchResult.found(visited,
                            "Angular 20.3 server bootstrap must propagate BootstrapContext as the third argument without sharing a platform across requests");
                }
                if (protractorProviders.contains(name)) {
                    return SearchResult.found(visited,
                            "provideProtractorTestingSupport opts into legacy Testability; confirm the remaining E2E harness needs it or remove during runner migration");
                }
                if (debugTools.contains(name)) {
                    return SearchResult.found(visited,
                            "Angular debug tools must not leak into production or SSR; verify environment guards and replacement diagnostics");
                }
                return visited;
            }

            private boolean isBrowserModule(Expression select) {
                return select instanceof J.Identifier identifier && browserModules.contains(identifier.getSimpleName());
            }

            private boolean isLikelySanitizer(Expression select) {
                // These method names are unique to DomSanitizer. Requiring its platform-browser import keeps the
                // match binding-aware while also covering property-injection shapes such as this.sanitizer.
                return !sanitizerBindings.isEmpty();
            }

            private boolean isLikelyTransferState(Expression select) {
                String name = expressionName(select).toLowerCase();
                return !transferStateTypes.isEmpty() && (name.contains("transferstate") || name.equals("state"));
            }

            private boolean isServerEntry() {
                Path path = getCursor().firstEnclosingOrThrow(JS.CompilationUnit.class).getSourcePath();
                String normalized = path.toString().replace('\\', '/');
                return normalized.endsWith("main.server.ts") || normalized.contains("/server/");
            }
        };
    }

    private static void collectAlias(JS.Import declaration, String importedName, Set<String> aliases) {
        String alias = MigrateDeterministicAngularPlatformBrowserSource.importedAlias(declaration, importedName);
        if (alias != null) {
            aliases.add(alias);
        }
    }

    private static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof J.FieldAccess field) {
            return field.getSimpleName();
        }
        if (expression instanceof J.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        return "";
    }
}
