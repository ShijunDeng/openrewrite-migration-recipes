package com.huawei.clouds.openrewrite.elementplusiconsvue;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Mark package ownership and peer/tool integration decisions that a version scalar cannot settle. */
public final class FindElementPlusIconsVueManifestRisks extends Recipe {
    private static final Set<String> INTEGRATION_PACKAGES = Set.of(
            "element-plus", "unplugin-icons", "unplugin-vue-components", "unplugin-element-plus");

    @Override
    public String getDisplayName() {
        return "Find @element-plus/icons-vue 2.3.2 manifest risks";
    }

    @Override
    public String getDescription() {
        return "Marks unresolved package declarations, central owners, unsupported or ambiguous Vue peers, " +
               "Element Plus integration, icon auto-import tooling, and side-effect/tree-shaking policy.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean relevant;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!ElementPlusIconsVueSupport.isPackageJson(document.getSourcePath())) return document;
                boolean previous = relevant;
                relevant = containsPackageSelector(document.getValue());
                Json.Document visited = super.visitDocument(document, ctx);
                relevant = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!relevant) return visited;
                String key = ElementPlusIconsVueSupport.key(visited);
                String value = ElementPlusIconsVueSupport.stringValue(visited);
                String section = ElementPlusIconsVueSupport.directDependencySection(getCursor());
                if (packageSelector(key)) {
                    if (section.isEmpty() || !ElementPlusIconsVueSupport.PACKAGE.equals(key)) {
                        return markValue(visited, "This @element-plus/icons-vue declaration is owned outside a root direct dependency section; update the workspace catalog, override, resolution, template, or other central owner deliberately");
                    }
                    if (value == null) {
                        return markValue(visited, "Non-string @element-plus/icons-vue declaration cannot be upgraded safely");
                    }
                    if (ElementPlusIconsVueSupport.selected(value)) {
                        return markValue(visited, "The workbook-selected @element-plus/icons-vue declaration remains; run the strict upgrade to 2.3.2 and regenerate the owning lockfile");
                    }
                    if (!Set.of(ElementPlusIconsVueSupport.TARGET, "^" + ElementPlusIconsVueSupport.TARGET,
                                "~" + ElementPlusIconsVueSupport.TARGET).contains(value)) {
                        return markValue(visited, declarationRisk(value));
                    }
                    return visited;
                }
                if (!section.isEmpty() && "vue".equals(key) && (value == null || !clearlySupportsVue32(value))) {
                    return markValue(visited, "@element-plus/icons-vue 2.3.2 peers on Vue ^3.2.0; prove every supported install resolves Vue 3.2+ below Vue 4 and verify compiler/runtime alignment");
                }
                if (!section.isEmpty() && INTEGRATION_PACKAGES.contains(key)) {
                    String message = "element-plus".equals(key)
                            ? "Coordinate Element Plus icon props, legacy el-icon-* classes, component registration, SSR/hydration, and visual snapshots with the icons package upgrade"
                            : "Review this icon/component auto-import plugin's resolver, generated declarations, include/exclude rules, SSR output, and tree-shaking against @element-plus/icons-vue 2.3.2";
                    return markValue(visited, message);
                }
                if ("sideEffects".equals(key) && rootMember() &&
                    (!(visited.getValue() instanceof Json.Literal literal) || Boolean.FALSE.equals(literal.getValue()))) {
                    return markValue(visited, "A sideEffects:false application can erase registration-only modules; verify explicit imports/global plugin bootstrap survive production tree shaking and SSR builds");
                }
                return visited;
            }

            private boolean rootMember() {
                return getCursor().getParent() != null && getCursor().getParent().getParent() != null &&
                       getCursor().getParent().getParent().getValue() instanceof Json.Document;
            }

            private String declarationRisk(String declaration) {
                if (declaration.matches("^(?:[~^]?\\d+\\.\\d+\\.\\d+)$")) {
                    return "Unlisted @element-plus/icons-vue version; the workbook authorizes only 0.2.4, 2.0.10, and 2.1.0 to 2.3.2";
                }
                if (declaration.startsWith("workspace:") || declaration.startsWith("npm:") ||
                    declaration.startsWith("file:") || declaration.startsWith("link:") ||
                    declaration.startsWith("git") || declaration.startsWith("http") ||
                    declaration.equals("latest") || declaration.equals("next") || declaration.startsWith("catalog:")) {
                    return "Protocol, alias, tag, catalog, fork, or dynamic @element-plus/icons-vue declaration needs an explicit owner-aware migration";
                }
                return "Complex @element-plus/icons-vue range, prerelease, or decorated scalar is intentionally not rewritten; select one target policy explicitly";
            }

            private boolean clearlySupportsVue32(String declaration) {
                String value = declaration.trim();
                if (!value.matches("[~^]?\\d+\\.\\d+(?:\\.\\d+)?")) return false;
                value = value.replaceFirst("^[~^]", "");
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.\\d+)?$").matcher(value);
                if (!matcher.matches()) return false;
                int major = Integer.parseInt(matcher.group(1));
                int minor = Integer.parseInt(matcher.group(2));
                return major == 3 && minor >= 2;
            }
        };
    }

    private static boolean packageSelector(String key) {
        if (ElementPlusIconsVueSupport.PACKAGE.equals(key) ||
            key.startsWith(ElementPlusIconsVueSupport.PACKAGE + "@")) return true;
        int parent = key.lastIndexOf('>');
        if (parent < 0) return false;
        String child = key.substring(parent + 1);
        return ElementPlusIconsVueSupport.PACKAGE.equals(child) ||
               child.startsWith(ElementPlusIconsVueSupport.PACKAGE + "@");
    }

    private static boolean containsPackageSelector(Json tree) {
        if (tree instanceof Json.Member member) {
            return packageSelector(ElementPlusIconsVueSupport.key(member)) ||
                   containsPackageSelector(member.getValue());
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindElementPlusIconsVueManifestRisks::containsPackageSelector);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindElementPlusIconsVueManifestRisks::containsPackageSelector);
        }
        return false;
    }

    private static Json.Member markValue(Json.Member member, String message) {
        var value = member.getValue();
        if (value.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))) return member;
        return member.withValue(SearchResult.found(value, message));
    }
}
