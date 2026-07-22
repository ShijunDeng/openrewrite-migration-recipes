package com.huawei.clouds.openrewrite.reactrouterdom;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Marks package values that require coordinated React Router DOM migration decisions. */
public final class FindReactRouterDomJsonRisks extends Recipe {
    private static final Set<String> COMPANIONS = Set.of(
            "history", "connected-react-router", "react-router-config", "react-router-dom-v5-compat",
            "@types/react-router", "@types/react-router-dom"
    );
    private static final Set<String> CENTRAL_OWNERS = Set.of(
            "overrides", "resolutions", "catalog", "catalogs", "pnpm"
    );

    @Override
    public String getDisplayName() {
        return "Find React Router DOM 6.30.4 package migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unselected dependency declarations, React and Node minimums, router/history/type companions, " +
               "and central package-manager owners in package.json without touching lockfiles.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !containsManagedRouter(
                        getCursor().firstEnclosingOrThrow(Json.Document.class).getValue())) return visited;
                String name = UpgradeSelectedReactRouterDomDependency.key(visited);
                if (!(visited.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration)) return visited;
                String parent = parentKey();
                boolean direct = UpgradeSelectedReactRouterDomDependency.SECTIONS.contains(parent);
                if (direct && "react-router-dom".equals(name) &&
                    !UpgradeSelectedReactRouterDomDependency.TARGET_VERSION.equals(declaration)) {
                    return markValue(visited, "react-router-dom is unlisted, newer, complex, tagged, variable-based, or protocol-based; choose the target constraint explicitly and regenerate the lockfile");
                }
                if (direct && Set.of("react", "react-dom").contains(name) && !atLeast(declaration, 16, 8)) {
                    return markValue(visited, name + " must be at least 16.8 before React Router v6 hooks are adopted; upgrade, deploy, and validate it independently");
                }
                if (direct && "react-router".equals(name) &&
                    !UpgradeSelectedReactRouterDomDependency.TARGET_VERSION.equals(stripSimplePrefix(declaration))) {
                    return markValue(visited, "A direct react-router declaration can create mixed majors with react-router-dom 6.30.4; align or remove it after checking direct imports");
                }
                if (direct && COMPANIONS.contains(name)) {
                    String message = name.startsWith("@types/")
                            ? name + " describes v4/v5 APIs while v6 ships its own types; remove it after checking other direct router consumers and TypeScript resolution"
                            : name + " is coupled to legacy router/history architecture; select a v6-compatible release, compatibility phase, or removal and test the integration";
                    return markValue(visited, message);
                }
                if ("engines".equals(parent) && "node".equals(name) && !atLeast(declaration, 14, 0)) {
                    return markValue(visited, "react-router-dom 6.30.4 declares Node >=14; align local, CI, build images, SSR, and deployment runtimes");
                }
                if ("react-router-dom".equals(name) && hasCentralOwner()) {
                    return markValue(visited, "Central package-manager ownership detected; update the catalog/override/resolution and every consumer atomically, then regenerate all lockfiles");
                }
                return visited;
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member
                        ? UpgradeSelectedReactRouterDomDependency.key((Json.Member) parent.getValue()) : "";
            }

            private boolean hasCentralOwner() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof Json.Member ancestor &&
                        CENTRAL_OWNERS.contains(UpgradeSelectedReactRouterDomDependency.key(ancestor))) return true;
                    cursor = cursor.getParent();
                }
                return false;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.withValue(SearchResult.found(member.getValue(), message));
            }
        };
    }

    private static boolean containsManagedRouter(Json tree) {
        if (tree instanceof Json.Member member) {
            return "react-router-dom".equals(UpgradeSelectedReactRouterDomDependency.key(member)) ||
                   containsManagedRouter(member.getValue());
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindReactRouterDomJsonRisks::containsManagedRouter);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindReactRouterDomJsonRisks::containsManagedRouter);
        }
        return false;
    }

    private static boolean atLeast(String declaration, int requiredMajor, int requiredMinor) {
        for (String alternative : declaration.split("\\s*\\|\\|\\s*")) {
            String candidate = alternative.trim();
            if (candidate.matches(">=\\d+(?:\\.\\d+){0,2}")) candidate = candidate.substring(2);
            else if (candidate.matches("[~^]?\\d+\\.\\d+\\.\\d+")) candidate = stripSimplePrefix(candidate);
            else if (candidate.matches("\\d+\\.x")) candidate = candidate.substring(0, candidate.length() - 2) + ".0";
            else return false;
            String[] parts = candidate.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (major < requiredMajor || major == requiredMajor && minor < requiredMinor) return false;
        }
        return true;
    }

    private static String stripSimplePrefix(String declaration) {
        return declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
    }
}
