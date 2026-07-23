package com.huawei.clouds.openrewrite.fastglob;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks dependency ownership and Node runtime decisions outside the strict upgrade. */
public final class FindFastGlobManifestRisks extends Recipe {
    private static final Pattern FIRST_NODE_VERSION = Pattern.compile(
            "^(?:>=|>|\\^|~)?v?(\\d+)(?:\\.(\\d+))?");
    private static final Set<String> CENTRAL_OWNERS = Set.of(
            "overrides", "resolutions", "pnpm", "catalog", "catalogs");
    @Override
    public String getDisplayName() {
        return "Find fast-glob 3.3.3 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped fast-glob declarations, central overrides/resolutions, and Node engine declarations " +
               "that may not satisfy fast-glob's >=8.6.0 runtime floor.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean fastGlobManifest;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!FastGlobSupport.isPackageJson(document.getSourcePath())) return document;
                boolean old = fastGlobManifest;
                fastGlobManifest = containsFastGlob(document);
                Json.Document visited = super.visitDocument(document, ctx);
                fastGlobManifest = old;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!fastGlobManifest) return visited;
                String key = FastGlobSupport.key(visited);
                String value = FastGlobSupport.stringValue(visited);
                String section = FastGlobSupport.directDependencySection(getCursor());
                if (FastGlobSupport.PACKAGE.equals(key) && !section.isEmpty() && value != null && !isTarget(value)) {
                    return mark(visited, "Strict migration skipped this complex range, protocol, alias, tag, or unlisted fast-glob version; choose a tested 3.3.3 constraint and regenerate the lockfile with the owning package manager");
                }
                String parent = parentKey();
                if (FastGlobSupport.PACKAGE.equals(key) && hasCentralOwnerAncestor()) {
                    return mark(visited, "This central override, resolution, pnpm policy, or catalog owns fast-glob independently; reconcile it with 3.3.3 and regenerate instead of editing lockfile bytes");
                }
                if ("node".equals(key) && "engines".equals(parent) && value != null && belowNode86(value)) {
                    return mark(visited, "fast-glob 3.3.3 requires Node >=8.6.0; align local, CI, container, Electron, build agents, and production runtime before upgrading");
                }
                return visited;
            }

            private String parentKey() {
                Cursor object = getCursor().getParent();
                Cursor parent = object == null ? null : object.getParent();
                return parent != null && parent.getValue() instanceof Json.Member member
                        ? FastGlobSupport.key(member) : "";
            }

            private boolean hasCentralOwnerAncestor() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null && !(cursor.getValue() instanceof Json.Document)) {
                    if (cursor.getValue() instanceof Json.Member ancestor &&
                        CENTRAL_OWNERS.contains(FastGlobSupport.key(ancestor))) return true;
                    cursor = cursor.getParent();
                }
                return false;
            }

            private Json.Member mark(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }
        };
    }

    private static boolean isTarget(String value) {
        return FastGlobSupport.TARGET_VERSION.equals(value) || ("^" + FastGlobSupport.TARGET_VERSION).equals(value) ||
               ("~" + FastGlobSupport.TARGET_VERSION).equals(value);
    }

    private static boolean belowNode86(String declaration) {
        String value = declaration.replace(" ", "").toLowerCase();
        if (value.contains("||") || value.startsWith("<") && !value.startsWith(">=")) return true;
        Matcher matcher = FIRST_NODE_VERSION.matcher(value);
        if (!matcher.find()) return false;
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return major < 8 || major == 8 && minor < 6;
    }

    private static boolean containsFastGlob(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) return false;
        for (Json child : root.getMembers()) {
            if (!(child instanceof Json.Member section) ||
                !FastGlobSupport.DEPENDENCY_SECTIONS.contains(FastGlobSupport.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject entries)) continue;
            for (Json entry : entries.getMembers()) {
                if (entry instanceof Json.Member member && FastGlobSupport.PACKAGE.equals(FastGlobSupport.key(member))) {
                    return true;
                }
            }
        }
        return false;
    }
}
