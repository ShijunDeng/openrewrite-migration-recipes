package com.huawei.clouds.openrewrite.echarts;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Locate manifest and JSON configuration decisions that cannot be safely inferred. */
public final class FindEChartsJsonRisks extends Recipe {
    private static final Set<String> COMPANIONS = Set.of(
            "ngx-echarts", "echarts-for-react", "vue-echarts", "@types/echarts", "echarts-gl", "echarts-stat"
    );
    private static final Set<String> TOOLING = Set.of(
            "typescript", "webpack", "webpack-cli", "webpack-dev-server", "rollup", "vite", "parcel",
            "jest", "ts-jest", "babel-jest"
    );

    @Override
    public String getDisplayName() {
        return "Find ECharts 6 manifest and JSON configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved direct ECharts declarations, framework/data extensions, obsolete community types, " +
               "old module tooling and physical src/lib/map resolver paths at exact JSON members.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                Path path = document.getSourcePath();
                if (!EChartsSupport.isProjectPath(path)) return visited;
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(file)) {
                    return containsDirectECharts(document.getValue()) ? inspectPackage(visited) : visited;
                }
                if (auditedConfig(file) && containsEChartsReference(document.getValue())) {
                    return inspectConfig(visited);
                }
                return visited;
            }

            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value) || !containsPhysicalEntry(value)) return visited;
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!EChartsSupport.isProjectPath(document.getSourcePath())) return visited;
                String file = document.getSourcePath().getFileName() == null ? "" :
                        document.getSourcePath().getFileName().toString();
                if ("package.json".equals(file) && containsDirectECharts(document.getValue())) {
                    return SearchResult.found(visited,
                            "This manifest/build value names an ECharts src/lib/map physical path; replace it with a documented target export or an explicitly owned map/asset boundary");
                }
                if (auditedConfig(file) && containsEChartsReference(document.getValue())) {
                    return SearchResult.found(visited,
                            "This ECharts-aware JSON configuration pins src/lib/map internals; map the resolver/copy/test rule to a public ECharts 6 export");
                }
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                String name = UpgradeSelectedEChartsDependency.key(member);
                boolean dependency = UpgradeSelectedEChartsDependency.SECTIONS.contains(parentKey()) &&
                                     isDirectDependencyMember();
                if (dependency && EChartsSupport.PACKAGE.equals(name) && !target(member)) {
                    return markValue(member, unresolvedMessage(member));
                }
                if (dependency && COMPANIONS.contains(name)) {
                    String message = "@types/echarts".equals(name)
                            ? "ECharts publishes its own declarations; remove @types/echarts only after resolving augmentations and duplicate global types"
                            : "This ECharts wrapper/extension has an independent compatibility matrix; verify its explicit ECharts 6.1 support, peer range, registration, renderer and framework lifecycle";
                    return markValue(member, message);
                }
                if (dependency && TOOLING.contains(name) && legacyTool(name, member)) {
                    return markValue(member, "This older toolchain needs explicit proof for ECharts 6 package exports, tree-shaken core registration, bundled declarations and test transforms");
                }
                return member;
            }

            private Json.Member inspectConfig(Json.Member member) {
                String name = UpgradeSelectedEChartsDependency.key(member);
                if ("moduleResolution".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Set.of("classic", "node", "node10").contains(String.valueOf(literal.getValue()).toLowerCase())) {
                    return markValue(member, "This ECharts-aware config uses legacy module resolution; verify exports and declarations with bundler, node16 or nodenext resolution as appropriate");
                }
                return member;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member enclosing
                        ? UpgradeSelectedEChartsDependency.key(enclosing) : "";
            }

            private boolean isDirectDependencyMember() {
                Cursor dependencyObject = getCursor().getParentTreeCursor();
                Cursor section = dependencyObject == null ? null : dependencyObject.getParentTreeCursor();
                Cursor rootObject = section == null ? null : section.getParentTreeCursor();
                Cursor document = rootObject == null ? null : rootObject.getParentTreeCursor();
                return document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static boolean containsDirectECharts(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        return root.getMembers().stream().anyMatch(value -> value instanceof Json.Member section &&
                UpgradeSelectedEChartsDependency.SECTIONS.contains(UpgradeSelectedEChartsDependency.key(section)) &&
                section.getValue() instanceof Json.JsonObject dependencies &&
                dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                        EChartsSupport.PACKAGE.equals(UpgradeSelectedEChartsDependency.key(dependency))));
    }

    private static boolean containsEChartsReference(Json tree) {
        if (tree instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            return value.contains("echarts");
        }
        if (tree instanceof Json.Member member) return containsEChartsReference(member.getValue());
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindEChartsJsonRisks::containsEChartsReference);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindEChartsJsonRisks::containsEChartsReference);
        }
        return false;
    }

    private static boolean containsPhysicalEntry(String value) {
        return value.contains("node_modules/echarts/src/") || value.contains("node_modules/echarts/lib/") ||
               value.contains("node_modules/echarts/map/") || value.matches(".*(?:^|[/'])echarts/(?:src|lib|map)/.*");
    }

    private static boolean auditedConfig(String file) {
        return file.startsWith("tsconfig") || file.startsWith("jsconfig") || file.contains("jest") ||
               file.contains("webpack") || file.contains("rollup") || file.contains("vite") ||
               file.contains("parcel");
    }

    private static boolean target(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && EChartsSupport.TARGET.equals(literal.getValue());
    }

    private static String unresolvedMessage(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String value)) {
            return "Non-string ECharts declaration was not changed; resolve it to an owned target scalar";
        }
        if (value.contains(":") || value.contains("${") || value.contains("{{") ||
            value.matches("(?:latest|next|catalog.*)")) {
            return "Protocol, alias, tag or dynamic ECharts declaration was not changed; update its catalog/workspace owner deliberately";
        }
        if (value.matches("[~^]?\\d+\\.\\d+\\.\\d+")) {
            return "Unlisted or non-target ECharts scalar was not changed; stage its actual release path before selecting 6.1.0";
        }
        return "Complex ECharts range was not changed; resolve the intended npm range and supported release path manually";
    }

    private static boolean legacyTool(String name, Json.Member member) {
        int[] version = scalar(member);
        if (version == null) return true;
        return switch (name) {
            case "typescript" -> version[0] < 4 || version[0] == 4 && version[1] < 7;
            case "webpack", "webpack-cli", "webpack-dev-server" -> version[0] < 5;
            case "rollup", "vite" -> version[0] < 3;
            case "parcel" -> version[0] < 2;
            case "jest", "ts-jest", "babel-jest" -> version[0] < 29;
            default -> false;
        };
    }

    private static int[] scalar(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String value)) return null;
        String candidate = value.startsWith("^") || value.startsWith("~") ? value.substring(1) : value;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static Json.Member markValue(Json.Member member, String message) {
        return member.withValue(SearchResult.found(member.getValue(), message));
    }
}
