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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Mark legacy animation source whose native CSS migration requires application behavior. */
public final class FindAngularAnimationsTypeScriptRisks extends Recipe {
    private static final Set<String> BASIC_DSL = Set.of(
            "trigger", "state", "style", "transition", "animate", "AUTO_STYLE"
    );
    private static final Set<String> COMPLEX_DSL = Set.of(
            "animation", "useAnimation", "animateChild", "query", "stagger", "group", "sequence", "keyframes"
    );
    private static final Set<String> PROGRAMMATIC = Set.of("AnimationBuilder", "AnimationFactory");
    private static final Set<String> PLAYERS = Set.of("AnimationPlayer", "NoopAnimationPlayer");
    private static final Set<String> PROVIDERS = Set.of(
            "BrowserAnimationsModule", "NoopAnimationsModule", "provideAnimations", "provideNoopAnimations",
            "BrowserAnimationsModuleConfig"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular 20 legacy animation TypeScript risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact legacy DSL, provider/module, builder/player, callback, testing and SSR-sensitive nodes " +
               "that cannot be translated to animate.enter/leave or CSS without application context.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> legacyFunctions = Map.of();
            private Map<String, String> providerFunctions = Map.of();
            private boolean hasLegacyImports;
            private boolean hasBuilder;
            private boolean hasPlayer;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, String> oldLegacy = legacyFunctions;
                Map<String, String> oldProviders = providerFunctions;
                boolean oldHasLegacy = hasLegacyImports;
                boolean oldHasBuilder = hasBuilder;
                boolean oldHasPlayer = hasPlayer;
                legacyFunctions = new HashMap<>();
                providerFunctions = new HashMap<>();
                hasLegacyImports = false;
                hasBuilder = false;
                hasPlayer = false;
                collectImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                legacyFunctions = oldLegacy;
                providerFunctions = oldProviders;
                hasLegacyImports = oldHasLegacy;
                hasBuilder = oldHasBuilder;
                hasPlayer = oldHasPlayer;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = MigrateDeterministicAngularAnimationsSource.moduleName(visited);
                if ("@angular/animations".equals(module)) {
                    if (MigrateDeterministicAngularAnimationsSource.importsAny(visited, COMPLEX_DSL)) {
                        return SearchResult.found(visited,
                                "Legacy query/stagger/group/sequence/keyframes/reusable animation DSL has no mechanical CSS equivalent; preserve selector, timeline, optional-query and final-style behavior");
                    }
                    if (MigrateDeterministicAngularAnimationsSource.importsAny(visited, PROGRAMMATIC)) {
                        return SearchResult.found(visited,
                                "AnimationBuilder/Factory is deprecated with the legacy package; choose Web Animations, CSS or an owned library and preserve cancel/finish/destroy lifecycle");
                    }
                    if (MigrateDeterministicAngularAnimationsSource.importsAny(visited, PLAYERS)) {
                        return SearchResult.found(visited,
                                "Legacy AnimationPlayer lifecycle is application-specific; preserve start/done/destroy callbacks, position and element teardown when replacing it");
                    }
                    if (MigrateDeterministicAngularAnimationsSource.importsAny(visited, Set.of("AnimationEvent"))) {
                        return SearchResult.found(visited,
                                "Legacy AnimationEvent callback shape differs from native animate callbacks and DOM events; migrate phase/timing/element consumers deliberately");
                    }
                    return SearchResult.found(visited,
                            "@angular/animations legacy trigger/state/transition/style/animate DSL is deprecated since Angular 20.2; migrate each trigger to animate.enter/leave and CSS with visual regression tests");
                }
                if ("@angular/platform-browser/animations".equals(module)) {
                    if (MigrateDeterministicAngularAnimationsSource.importsAny(visited, Set.of("ANIMATION_MODULE_TYPE")) &&
                        !MigrateDeterministicAngularAnimationsSource.importsOnly(visited, "ANIMATION_MODULE_TYPE")) {
                        return SearchResult.found(visited,
                                "Mixed platform-browser animations import contains core-owned ANIMATION_MODULE_TYPE; split/merge imports without duplicating local bindings");
                    }
                    return SearchResult.found(visited,
                            "BrowserAnimationsModule/NoopAnimationsModule and provideAnimations providers are deprecated since Angular 20.2; remove them only after all legacy triggers and third-party requirements are gone");
                }
                if ("@angular/platform-browser/animations/async".equals(module)) {
                    return SearchResult.found(visited,
                            "provideAnimationsAsync is deprecated since Angular 20.2; native animate.enter/leave needs no legacy renderer provider, but lazy-load and SSR behavior must be retested");
                }
                if ("@angular/animations/browser/testing".equals(module)) {
                    return SearchResult.found(visited,
                            "MockAnimationDriver/MockAnimationPlayer tests assert legacy engine internals; rewrite them around rendered classes, DOM events, completion and reduced-motion behavior");
                }
                if ("@angular/animations/browser".equals(module)) {
                    return SearchResult.found(visited,
                            "AnimationDriver/engine browser APIs are legacy internals; replace custom drivers only after defining browser, server, cancellation and error semantics");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String name = visited.getSimpleName();
                String canonical = legacyFunctions.get(name);
                if (canonical != null) {
                    return SearchResult.found(visited, callMessage(canonical));
                }
                canonical = providerFunctions.get(name);
                if (canonical != null) {
                    return SearchResult.found(visited, providerMessage(canonical));
                }
                String select = MigrateDeterministicAngularAnimationsSource.expressionName(visited.getSelect())
                        .toLowerCase();
                if (hasBuilder && ("build".equals(name) || "create".equals(name)) &&
                    (select.contains("animation") || select.contains("builder") || select.contains("factory"))) {
                    return SearchResult.found(visited,
                            "Programmatic animation construction requires an explicit replacement timeline and cancel/finish/destroy ownership");
                }
                if (hasPlayer && Set.of("play", "pause", "finish", "destroy", "reset", "restart", "setPosition", "getPosition")
                        .contains(name) && (select.contains("animation") || select.contains("player"))) {
                    return SearchResult.found(visited,
                            "AnimationPlayer control affects callback ordering and DOM teardown; preserve lifecycle and cancellation semantics in the replacement");
                }
                if ("withConfig".equals(name) && visited.getSelect() instanceof J.Identifier identifier &&
                    providerFunctions.containsValue("BrowserAnimationsModule") &&
                    providerFunctions.containsKey(identifier.getSimpleName())) {
                    return SearchResult.found(visited,
                            "BrowserAnimationsModule.withConfig controls global legacy renderer behavior; choose native CSS/reduced-motion behavior before removing it");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String name = MigrateDeterministicAngularAnimationsSource.expressionName(visited.getFunction());
                String canonical = legacyFunctions.get(name);
                if (canonical != null) {
                    return SearchResult.found(visited, callMessage(canonical));
                }
                canonical = providerFunctions.get(name);
                return canonical == null ? visited : SearchResult.found(visited, providerMessage(canonical));
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = MigrateDeterministicAngularAnimationsSource.expressionName(visited.getName());
                if (hasLegacyImports && "animations".equals(name)) {
                    return SearchResult.found(visited,
                            "Component animations metadata is deprecated since Angular 20.2; move each referenced trigger into template animate.enter/leave and component styles");
                }
                if (hasLegacyImports && "animation".equals(name)) {
                    return SearchResult.found(visited,
                            "Animation state stored in route/component metadata often drives legacy triggers; preserve navigation direction, lazy route and SSR/hydration behavior");
                }
                return visited;
            }

            private void collectImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = MigrateDeterministicAngularAnimationsSource.moduleName(visited);
                        if ("@angular/animations".equals(module)) {
                            hasLegacyImports = true;
                            for (String api : BASIC_DSL) add(visited, api, legacyFunctions);
                            for (String api : COMPLEX_DSL) add(visited, api, legacyFunctions);
                            hasBuilder |= MigrateDeterministicAngularAnimationsSource.importsAny(visited, PROGRAMMATIC);
                            hasPlayer |= MigrateDeterministicAngularAnimationsSource.importsAny(visited, PLAYERS);
                        }
                        if ("@angular/platform-browser/animations".equals(module)) {
                            for (String api : PROVIDERS) add(visited, api, providerFunctions);
                        }
                        if ("@angular/platform-browser/animations/async".equals(module)) {
                            add(visited, "provideAnimationsAsync", providerFunctions);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private String providerMessage(String canonical) {
                String context = isServerEntry()
                        ? " Server entry points must not share browser animation state and must hydrate to the same initial DOM/classes."
                        : " Verify third-party consumers, test timing, disabled/reduced-motion and renderer teardown before removal.";
                return canonical + " enables or disables the deprecated legacy animation renderer." + context;
            }

            private boolean isServerEntry() {
                Path path = getCursor().firstEnclosingOrThrow(JS.CompilationUnit.class).getSourcePath();
                String normalized = path.toString().replace('\\', '/');
                return normalized.endsWith("main.server.ts") || normalized.contains("/server/");
            }
        };
    }

    private static String callMessage(String api) {
        if (Set.of("query", "stagger", "group", "sequence", "keyframes", "animation", "useAnimation", "animateChild")
                .contains(api)) {
            return api + " composes a legacy timeline with selector/order/parallelism semantics; design and visually verify the CSS or Web Animations replacement";
        }
        if ("transition".equals(api)) {
            return "Legacy transition expressions and :enter/:leave ordering have no direct mechanical mapping; preserve state matching, interruption and final styles";
        }
        if ("animate".equals(api)) {
            return "Legacy animate timing/easing/fill behavior must be reproduced in CSS and tested for interruption, DOM removal and reduced motion";
        }
        return api + " is part of the deprecated legacy animation DSL; migrate the owning trigger as a unit rather than rewriting this call alone";
    }

    private static void add(JS.Import declaration, String imported, Map<String, String> aliases) {
        String alias = MigrateDeterministicAngularAnimationsSource.importedAlias(declaration, imported);
        if (alias != null) aliases.put(alias, imported);
    }
}
