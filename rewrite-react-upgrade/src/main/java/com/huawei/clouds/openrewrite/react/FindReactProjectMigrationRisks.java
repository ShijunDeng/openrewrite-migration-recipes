package com.huawei.clouds.openrewrite.react;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Mark companion dependencies and JSX configuration that cannot be upgraded safely in isolation. */
public final class FindReactProjectMigrationRisks extends Recipe {
    private static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Map<String, String> PACKAGES = Map.ofEntries(
            Map.entry("react-dom", "Align react-dom with React 19.2.7, then migrate root, hydration and server-rendering APIs together"),
            Map.entry("react-test-renderer", "react-test-renderer is deprecated; align it temporarily and migrate tests to a maintained renderer"),
            Map.entry("react-native", "Verify the React Native release's declared React peer before changing React; do not force the web renderer matrix"),
            Map.entry("@types/react", "Align @types/react with React 19 and resolve ref, JSX, reducer and ReactElement type breaks"),
            Map.entry("@types/react-dom", "Align @types/react-dom with react-dom 19 and resolve client/server entry-point changes"),
            Map.entry("react-router", "Verify this React Router major supports React 19 and test navigation, transitions and SSR"),
            Map.entry("react-router-dom", "Verify this React Router DOM major supports React 19 and test navigation, hydration and data routers"),
            Map.entry("next", "Use a Next.js release that declares React 19 support; follow its compiler, server-components and hydration upgrade guide"),
            Map.entry("gatsby", "Verify Gatsby's React 19 support and test SSR, hydration, plugins and webpack aliases before upgrading"),
            Map.entry("enzyme", "Enzyme has no official React 18/19 adapter; replace or explicitly own an unofficial adapter strategy"),
            Map.entry("@testing-library/react", "Upgrade React Testing Library for React 19 act/root support and re-run async interaction tests"),
            Map.entry("eslint-plugin-react-hooks", "Upgrade eslint-plugin-react-hooks and review newly reported effect/compiler diagnostics"),
            Map.entry("@vitejs/plugin-react", "Verify the Vite React plugin and JSX transform support the selected React 19 toolchain"),
            Map.entry("mobx-react", "Verify mobx-react's React 19 peer range and observer behavior under StrictMode and concurrent rendering"),
            Map.entry("@material-ui/core", "Material UI v4 targets older React generations; migrate to a React 19-compatible MUI release and retest styling/SSR")
    );

    @Override
    public String getDisplayName() {
        return "Find React 19 project compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark renderer, framework, router, native, testing, type and tooling dependencies plus classic JSX " +
               "configuration that need their own compatibility decision.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                String filename = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(filename)) {
                    String packageName = UpgradeSelectedReactDependency.key(visited);
                    String message = PACKAGES.get(packageName);
                    if (message != null && isDirectDependency() && !isAlreadyAligned(packageName, visited)) {
                        return SearchResult.found(visited, message);
                    }
                }
                if (("tsconfig.json".equals(filename) || "jsconfig.json".equals(filename)) &&
                    "jsx".equals(UpgradeSelectedReactDependency.key(visited)) &&
                    visited.getValue() instanceof Json.Literal value &&
                    ("react".equals(value.getValue()) || "preserve".equals(value.getValue()))) {
                    return SearchResult.found(visited,
                            "React 19 requires the modern JSX transform; select react-jsx/react-jsxdev unless the framework owns JSX compilation");
                }
                if ((filename.startsWith(".babelrc") || "babel.config.json".equals(filename)) &&
                    "runtime".equals(UpgradeSelectedReactDependency.key(visited)) &&
                    visited.getValue() instanceof Json.Literal value && "classic".equals(value.getValue())) {
                    return SearchResult.found(visited,
                            "React 19 requires the modern JSX transform; switch the React preset runtime to automatic after verifying the compiler chain");
                }
                return visited;
            }

            private boolean isDirectDependency() {
                Cursor objectCursor = getCursor().getParentTreeCursor();
                Cursor sectionCursor = objectCursor == null ? null : objectCursor.getParentTreeCursor();
                return sectionCursor != null && sectionCursor.getValue() instanceof Json.Member section &&
                       SECTIONS.contains(UpgradeSelectedReactDependency.key(section));
            }
        };
    }

    private static boolean isAlreadyAligned(String packageName, Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal value) || !(value.getValue() instanceof String declaration)) {
            return false;
        }
        if ("react-dom".equals(packageName) || "react-test-renderer".equals(packageName)) {
            return "19.2.7".equals(declaration) || "^19.2.7".equals(declaration) || "~19.2.7".equals(declaration);
        }
        return false;
    }
}
