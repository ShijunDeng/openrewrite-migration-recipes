package com.huawei.clouds.openrewrite.ngxecharts;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

/** Upgrade only ngx-echarts releases explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedNgxEchartsDependency extends Recipe {
    static final String PACKAGE = "ngx-echarts";
    static final String TARGET = "20.0.2";
    static final Set<String> SOURCES = Set.of(
            "5.2.2", "6.0.1", "7.0.2", "7.1.0", "8.0.1",
            "14.0.0", "15.0.0", "15.0.2", "15.0.3", "16.0.0"
    );
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected ngx-echarts declarations to 20.0.2";
    }

    @Override
    public String getDescription() {
        return "Upgrade exact, caret, or tilde declarations for spreadsheet-visible releases without changing " +
               "compound ranges, protocols, aliases, tags, variables, metadata, lockfiles or unlisted versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !PACKAGE.equals(key(visited)) || !isDirectDependency() ||
                    !(visited.getValue() instanceof Json.Literal value) ||
                    !(value.getValue() instanceof String declaration) || !selected(declaration)) {
                    return visited;
                }
                return visited.withValue(value.withSource('"' + TARGET + '"').withValue(TARGET));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }

            private boolean isDirectDependency() {
                Cursor object = getCursor().getParent();
                Cursor section = object == null ? null : object.getParent();
                Cursor root = section == null ? null : section.getParent();
                Cursor document = root == null ? null : root.getParent();
                return section != null && section.getValue() instanceof Json.Member parent &&
                       SECTIONS.contains(key(parent)) && document != null &&
                       document.getValue() instanceof Json.Document;
            }
        };
    }

    static boolean selected(String declaration) {
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+") && SOURCES.contains(candidate);
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) return String.valueOf(literal.getValue());
        if (member.getKey() instanceof Json.Identifier identifier) return identifier.getName();
        return "";
    }
}
