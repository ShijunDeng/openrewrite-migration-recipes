package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

/** Updates only source releases that are visible in the supplied migration spreadsheet. */
public final class UpgradeSelectedAngularCoreDependency extends Recipe {
    static final String TARGET_VERSION = "20.3.26";
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "10.0.14", "10.2.5", "11.2.14", "12.2.10", "12.2.13", "12.2.14",
            "12.2.16", "12.2.17", "13.1.3", "13.2.6"
    );
    private static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected @angular/core declarations to 20.3.26";
    }

    @Override
    public String getDescription() {
        return "Updates exact, caret, or tilde scalar @angular/core declarations selected by the migration spreadsheet, " +
               "without changing ranges, aliases, protocols, variables, package-manager metadata, or lockfiles.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !"@angular/core".equals(key(visited)) ||
                    !(visited.getValue() instanceof Json.Literal value) ||
                    !(value.getValue() instanceof String declaration) ||
                    !selected(declaration) || !isDirectDependency()) {
                    return visited;
                }
                return visited.withValue(value.withSource('"' + TARGET_VERSION + '"').withValue(TARGET_VERSION));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }

            private boolean isDirectDependency() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor section = object == null ? null : object.getParentTreeCursor();
                Cursor rootObject = section == null ? null : section.getParentTreeCursor();
                Cursor document = rootObject == null ? null : rootObject.getParentTreeCursor();
                return section != null && section.getValue() instanceof Json.Member &&
                       SECTIONS.contains(key((Json.Member) section.getValue())) &&
                       document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static boolean selected(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) {
            candidate = candidate.substring(1);
        }
        return SOURCE_VERSIONS.contains(candidate);
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        if (member.getKey() instanceof Json.Identifier identifier) {
            return identifier.getName();
        }
        return "";
    }
}
