package com.huawei.clouds.openrewrite.reactresizable;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark package graph boundaries that the dependency-only transform must not guess. */
public final class FindReactResizableProjectRisks extends Recipe {
    private static final Pattern VERSION = Pattern.compile("[~^]?(\\d+)(?:[.](\\d+))?(?:[.]\\d+)?");
    private static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );

    @Override
    public String getDisplayName() {
        return "Find react-resizable 3 package graph risks";
    }

    @Override
    public String getDescription() {
        return "Mark unsupported React peers, explicit react-draggable ownership, TypeScript declaration alignment, " +
               "unselected/external react-resizable declarations, and override/resolution ownership in package.json.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean relevant;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!packageJson(document.getSourcePath()) ||
                    !document.printAll().matches("(?s).*['\"]react-resizable['\"].*")) return document;
                boolean old = relevant;
                relevant = true;
                Json.Document visited = super.visitDocument(document, ctx);
                relevant = old;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!relevant) return visited;
                String key = ReactResizableSupport.jsonKey(visited);
                String value = ReactResizableSupport.jsonString(visited);
                String section = directSection();
                if (ReactResizableSupport.PACKAGE.equals(key)) {
                    if (overrideOwner()) {
                        return mark(visited, "This override/resolution owns react-resizable independently of direct dependencies; update the true owner, regenerate the selected lockfile, and verify no duplicate 1.x copy remains");
                    }
                    if (DEPENDENCY_SECTIONS.contains(section) && !selectedOrTarget(value)) {
                        return mark(visited, "This react-resizable declaration is outside the workbook's exact 1.11.1 source or is externally ranged/aliased/protocol-owned; do not widen AUTO, and resolve its actual installed version deliberately");
                    }
                }
                if (!DEPENDENCY_SECTIONS.contains(section)) return visited;
                if (Set.of("react", "react-dom").contains(key)) {
                    if (value == null || !atLeast(value, 16, 3)) {
                        return mark(visited, "react-resizable 3 requires both React and ReactDOM >=16.3 for createRef/nodeRef integration; align every workspace/renderer and verify a single compatible runtime copy");
                    }
                }
                if ("react-draggable".equals(key) && (value == null || !atLeast(value, 4, 5))) {
                    return mark(visited, "The target depends on react-draggable ^4.5.0; this explicit owner may force an older or incompatible copy. Align it and verify nodeRef, scale, bounds, grid, offsetParent, cancellation, and pointer behavior");
                }
                if ("@types/react-resizable".equals(key)) {
                    return mark(visited, "react-resizable ships Flow source rather than TypeScript declarations; align @types/react-resizable with the v3 two-argument handle/ref contract and the installed React types, then compile strict JSX consumers");
                }
                return visited;
            }

            private String directSection() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor section = object == null ? null : object.getParentTreeCursor();
                if (section == null || !(section.getValue() instanceof Json.Member member)) {
                    return "";
                }
                Cursor rootObject = section.getParentTreeCursor();
                if (rootObject == null || !(rootObject.getValue() instanceof Json.JsonObject)) {
                    return "";
                }
                Cursor document = rootObject.getParentTreeCursor();
                return document != null && document.getValue() instanceof Json.Document
                        ? ReactResizableSupport.jsonKey(member) : "";
            }

            private boolean overrideOwner() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor section = object == null ? null : object.getParentTreeCursor();
                if (section == null || !(section.getValue() instanceof Json.Member member)) return false;
                String name = ReactResizableSupport.jsonKey(member);
                if (Set.of("overrides", "resolutions", "pnpm.overrides").contains(name) && rootMember(section)) {
                    return true;
                }
                if (!"overrides".equals(name)) return false;
                Cursor pnpmObject = section.getParentTreeCursor();
                Cursor pnpm = pnpmObject == null ? null : pnpmObject.getParentTreeCursor();
                return pnpm != null && pnpm.getValue() instanceof Json.Member pnpmMember &&
                       "pnpm".equals(ReactResizableSupport.jsonKey(pnpmMember)) && rootMember(pnpm);
            }

            private boolean rootMember(Cursor member) {
                Cursor rootObject = member.getParentTreeCursor();
                Cursor document = rootObject == null ? null : rootObject.getParentTreeCursor();
                return document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static boolean selectedOrTarget(String value) {
        if (value == null) return false;
        String normalized = value.startsWith("^") || value.startsWith("~") ? value.substring(1) : value;
        return ReactResizableSupport.SOURCE.equals(normalized) || ReactResizableSupport.TARGET.equals(normalized);
    }

    private static boolean atLeast(String value, int expectedMajor, int expectedMinor) {
        Matcher matcher = VERSION.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) return false;
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return major > expectedMajor || major == expectedMajor && minor >= expectedMinor;
    }

    private static boolean packageJson(Path path) {
        return ReactResizableSupport.isProjectPath(path) && path.getFileName() != null &&
               "package.json".equals(path.getFileName().toString());
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
