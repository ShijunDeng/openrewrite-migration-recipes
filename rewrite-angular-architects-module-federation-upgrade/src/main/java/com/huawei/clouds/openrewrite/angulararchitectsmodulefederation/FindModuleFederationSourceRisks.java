package com.huawei.clouds.openrewrite.angulararchitectsmodulefederation;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark public API, webpack sharing, remote loading, and deployment contracts at their owning syntax. */
public final class FindModuleFederationSourceRisks extends Recipe {
    private static final Set<String> PUBLIC_ENTRYPOINTS = Set.of(
            ModuleFederationSupport.PACKAGE,
            ModuleFederationSupport.PACKAGE + "/webpack",
            ModuleFederationSupport.PACKAGE + "/runtime",
            ModuleFederationSupport.PACKAGE + "/rspack"
    );
    private static final Set<String> RUNTIME_CALLS = Set.of(
            "initFederation", "loadManifest", "setManifest", "getManifest", "loadRemoteEntry", "loadRemoteModule"
    );
    private static final Set<String> SHARING_CALLS = Set.of(
            "withModuleFederationPlugin", "withFederation", "share", "shareAll", "setInferVersion"
    );
    private static final Set<String> SHARING_KEYS = Set.of(
            "shared", "singleton", "strictVersion", "requiredVersion", "includeSecondaries", "eager", "pinned"
    );
    private static final Set<String> REMOTE_KEYS = Set.of(
            "remotes", "exposes", "remoteEntry", "remoteName", "exposedModule", "remoteType", "outputModule",
            "runtimeChunk", "scriptType"
    );
    private static final String OWNED_MODULE = Pattern.quote(ModuleFederationSupport.PACKAGE) + "(?:/[^'\"]+)?";
    private static final Pattern NAMED_IMPORT = Pattern.compile(
            "import\\s*\\{([^}]*)}\\s*from\\s*['\"](" + OWNED_MODULE + ")['\"]", Pattern.DOTALL);
    private static final Pattern NAMED_REQUIRE = Pattern.compile(
            "(?:const|let|var)\\s*\\{([^}]*)}\\s*=\\s*require\\s*\\(\\s*['\"](" + OWNED_MODULE + ")['\"]\\s*\\)", Pattern.DOTALL);
    private static final Pattern NAMESPACE_IMPORT = Pattern.compile(
            "import\\s+(?:\\*\\s+as\\s+)?([A-Za-z_$][\\w$]*)\\s+from\\s*['\"](" + OWNED_MODULE + ")['\"]");
    private static final Pattern NAMESPACE_REQUIRE = Pattern.compile(
            "(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*require\\s*\\(\\s*['\"](" + OWNED_MODULE + ")['\"]\\s*\\)");

    @Override
    public String getDisplayName() {
        return "Find Module Federation 20 source and webpack risks";
    }

    @Override
    public String getDescription() {
        return "Mark deep imports, classic/rsbuild/runtime APIs, SharedMappings, share policy, remote/expose contracts, " +
               "manifest paths, async bootstrap, and deployment-owned URLs in JavaScript and TypeScript.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean relevant;
            private Set<String> ownedRuntimeCalls = Set.of();
            private Set<String> ownedSharingCalls = Set.of();
            private Set<String> namespaces = Set.of();
            private Map<String, Integer> expectedDeclarations = Map.of();
            private Map<String, Integer> declarationCounts = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!ModuleFederationSupport.projectPath(cu.getSourcePath())) return cu;
                boolean old = relevant;
                Set<String> oldRuntimeCalls = ownedRuntimeCalls;
                Set<String> oldSharingCalls = ownedSharingCalls;
                Set<String> oldNamespaces = namespaces;
                Map<String, Integer> oldExpectedDeclarations = expectedDeclarations;
                Map<String, Integer> oldDeclarationCounts = declarationCounts;
                String source = cu.printAll();
                relevant = ModuleFederationSupport.containsPackageReference(source) ||
                           source.contains("ModuleFederationPlugin") && source.contains("remoteEntry");
                ownedRuntimeCalls = new HashSet<>();
                ownedSharingCalls = new HashSet<>();
                namespaces = new HashSet<>();
                expectedDeclarations = new HashMap<>();
                declarationCounts = relevant ? findDeclarationCounts(cu) : Map.of();
                if (relevant) {
                    collectBindings(source, ownedRuntimeCalls, ownedSharingCalls, namespaces, expectedDeclarations);
                }
                JS.CompilationUnit visited = relevant ? super.visitJsCompilationUnit(cu, ctx) : cu;
                relevant = old;
                ownedRuntimeCalls = oldRuntimeCalls;
                ownedSharingCalls = oldSharingCalls;
                namespaces = oldNamespaces;
                expectedDeclarations = oldExpectedDeclarations;
                declarationCounts = oldDeclarationCounts;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!relevant) return visited;
                String module = ModuleFederationSupport.moduleName(visited);
                if (!ModuleFederationSupport.packageModule(module)) return visited;
                if (!PUBLIC_ENTRYPOINTS.contains(module)) {
                    return mark(visited, "This private/deep package path is outside the target's documented root, /webpack, /runtime and /rspack entrypoints; replace it with a public owner after checking exported symbols");
                }
                if (module.equals(ModuleFederationSupport.PACKAGE) || module.equals(ModuleFederationSupport.PACKAGE + "/runtime")) {
                    return mark(visited, "Runtime loading remains public but crosses host/remote deployment contracts; verify classic versus enhanced runtime, manifest format, async bootstrap, errors and duplicate runtime copies");
                }
                if (module.equals(ModuleFederationSupport.PACKAGE + "/webpack") ||
                    module.equals(ModuleFederationSupport.PACKAGE + "/rspack")) {
                    return mark(visited, "This import fixes the federation stack; verify the matching Angular builder and do not mechanically switch webpack, experimental rsbuild and Native Federation semantics");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!relevant) return visited;
                String name = visited.getSimpleName();
                if ("require".equals(name) && !visited.getArguments().isEmpty() &&
                    visited.getArguments().get(0) instanceof J.Literal literal && literal.getValue() instanceof String module &&
                    ModuleFederationSupport.packageModule(module)) {
                    if (!PUBLIC_ENTRYPOINTS.contains(module)) {
                        return mark(visited, "This CommonJS deep import is not a documented target entrypoint; use root, /webpack, /runtime or /rspack according to explicit stack ownership");
                    }
                    return mark(visited, module.endsWith("/webpack")
                            ? "Classic webpack ownership requires Angular 20 plus ngx-build-plus/@nx webpack builders and matching production/dev-server configs"
                            : "Review this runtime/stack entrypoint against the selected target stack and package export boundary");
                }
                if ((RUNTIME_CALLS.contains(name) || ownedRuntimeCalls.contains(name)) &&
                    ownedCall(visited, name, ownedRuntimeCalls)) {
                    return mark(visited, "Remote runtime loading is deployment-sensitive; verify manifest schema, module/script mode, URL/base href, initialization order, fallback/error handling and host/remote version negotiation");
                }
                if ((SHARING_CALLS.contains(name) || ownedSharingCalls.contains(name)) &&
                    ownedCall(visited, name, ownedSharingCalls)) {
                    return mark(visited, "Revalidate the target sharing policy across every independently deployed host and remote, including secondary entrypoints, eager/pinned bundles and strict version negotiation");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (!relevant || visited.getArguments().isEmpty() ||
                    !(visited.getFunction() instanceof J.Identifier identifier) ||
                    !("import".equals(identifier.getSimpleName()) || "require".equals(identifier.getSimpleName())) ||
                    !(visited.getArguments().get(0) instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module) || !ModuleFederationSupport.packageModule(module)) {
                    return visited;
                }
                return mark(visited, PUBLIC_ENTRYPOINTS.contains(module)
                        ? "Review this dynamic/CommonJS runtime or stack load against the selected target export, initialization order and deployment boundary"
                        : "This dynamic/CommonJS deep import is outside the target's documented root, /webpack, /runtime and /rspack entrypoints");
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (!relevant) return visited;
                String name = visited.getSimpleName();
                if ("SharedMappings".equals(name)) {
                    return mark(visited, "SharedMappings is legacy configuration; verify tsconfig path aliases, descriptors, aliases and plugin registration under Angular 20 or migrate deliberately to documented helpers");
                }
                if (SHARING_KEYS.contains(name)) {
                    return mark(visited, "This sharing option is an ABI/runtime policy, not formatting; test singleton duplication, strict-version failures, secondaries and initial bundle size across mixed deployments");
                }
                if (REMOTE_KEYS.contains(name)) {
                    return mark(visited, "This remote/expose/output value is a cross-deployment contract; verify stable names, filenames, module format, CORS/CSP, public path, cache invalidation and rollback compatibility");
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!relevant || !(visited.getValue() instanceof String value)) return visited;
                if (value.contains("remoteEntry") || value.contains("mf.manifest") || value.contains("module-federation.manifest")) {
                    return mark(visited, "This manifest/remote URL is deployment-owned; verify Angular 20 output paths, assets/public copying, content type, module scripts, CORS, CSP, caching and failure fallback");
                }
                if (value.contains("./bootstrap")) {
                    return mark(visited, "The async bootstrap boundary initializes the shared scope before Angular; preserve ordering and test rejected imports, SSR/hydration and startup telemetry");
                }
                return visited;
            }

            private boolean ownedCall(J.MethodInvocation invocation, String name, Set<String> bindings) {
                if (invocation.getSelect() == null) return bindings.contains(name) && unshadowed(name);
                String root = ModuleFederationSupport.rootIdentifier(invocation.getSelect());
                return root != null && namespaces.contains(root) && unshadowed(root);
            }

            private boolean unshadowed(String binding) {
                return declarationCounts.getOrDefault(binding, 0)
                       == expectedDeclarations.getOrDefault(binding, 0);
            }

            private Map<String, Integer> findDeclarationCounts(JS.CompilationUnit cu) {
                Map<String, Integer> counts = new HashMap<>();
                new JavaScriptIsoVisitor<Map<String, Integer>>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, Map<String, Integer> accumulator) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, accumulator);
                        accumulator.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, counts);
                return counts;
            }
        };
    }

    private static void collectBindings(String source, Set<String> runtimeCalls, Set<String> sharingCalls,
                                        Set<String> namespaces, Map<String, Integer> expectedDeclarations) {
        collectNamed(NAMED_IMPORT.matcher(source), runtimeCalls, sharingCalls, false, expectedDeclarations);
        collectNamed(NAMED_REQUIRE.matcher(source), runtimeCalls, sharingCalls, true, expectedDeclarations);
        collectNamespaces(NAMESPACE_IMPORT.matcher(source), namespaces, false, expectedDeclarations);
        collectNamespaces(NAMESPACE_REQUIRE.matcher(source), namespaces, true, expectedDeclarations);
        for (String namespace : Set.copyOf(namespaces)) {
            Pattern aliases = Pattern.compile("(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*" +
                                              Pattern.quote(namespace) + "[.]([A-Za-z_$][\\w$]*)");
            Matcher matcher = aliases.matcher(source);
            while (matcher.find()) {
                if (RUNTIME_CALLS.contains(matcher.group(2)) || SHARING_CALLS.contains(matcher.group(2))) {
                    (RUNTIME_CALLS.contains(matcher.group(2)) ? runtimeCalls : sharingCalls).add(matcher.group(1));
                    expectedDeclarations.put(matcher.group(1), 1);
                }
            }
        }
    }

    private static void collectNamed(Matcher matcher, Set<String> runtimeCalls, Set<String> sharingCalls,
                                     boolean requireSyntax, Map<String, Integer> expectedDeclarations) {
        while (matcher.find()) {
            for (String raw : matcher.group(1).split(",")) {
                String binding = raw.trim().replaceFirst("^type\\s+", "");
                String[] parts = binding.split(requireSyntax ? "\\s*:\\s*" : "\\s+as\\s+");
                String imported = parts[0].trim();
                String local = parts.length > 1 ? parts[parts.length - 1].trim() : imported;
                if (RUNTIME_CALLS.contains(imported)) runtimeCalls.add(local);
                if (SHARING_CALLS.contains(imported)) sharingCalls.add(local);
                if (RUNTIME_CALLS.contains(imported) || SHARING_CALLS.contains(imported)) {
                    // JavaScript destructuring bindings are represented as binding elements rather than
                    // NamedVariable nodes, while later parameters/locals with the same name are counted.
                    expectedDeclarations.put(local, 0);
                }
            }
        }
    }

    private static void collectNamespaces(Matcher matcher, Set<String> namespaces, boolean requireSyntax,
                                          Map<String, Integer> expectedDeclarations) {
        while (matcher.find()) {
            namespaces.add(matcher.group(1));
            expectedDeclarations.put(matcher.group(1), requireSyntax ? 1 : 0);
        }
    }

    private static <T extends org.openrewrite.Tree> T mark(T tree, String message) {
        return ModuleFederationSupport.mark(tree, message);
    }
}
