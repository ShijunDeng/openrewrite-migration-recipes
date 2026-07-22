package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

/** Upgrade only @angular/platform-browser versions visible in the migration spreadsheet. */
public final class UpgradeSelectedAngularPlatformBrowserDependency extends Recipe {
    static final String TARGET = "20.3.26";
    static final Set<String> SOURCES = Set.of(
            "10.0.14", "10.2.5", "11.2.14", "12.2.10", "12.2.13", "12.2.14",
            "12.2.16", "12.2.17", "13.1.3", "13.2.6"
    );
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected @angular/platform-browser declarations to 20.3.26";
    }

    @Override
    public String getDescription() {
        return "Upgrade exact, caret, or tilde scalar declarations for spreadsheet-visible releases without " +
               "changing compound ranges, protocols, variables, metadata, lockfiles or unlisted versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !"@angular/platform-browser".equals(key(visited)) ||
                    !(visited.getValue() instanceof Json.Literal value) ||
                    !(value.getValue() instanceof String declaration) || !selected(declaration) ||
                    !isDirectDependency()) {
                    return visited;
                }
                return visited.withValue(value.withSource('"' + TARGET + '"').withValue(TARGET));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }

            private boolean isDirectDependency() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor section = object == null ? null : object.getParentTreeCursor();
                Cursor root = section == null ? null : section.getParentTreeCursor();
                Cursor document = root == null ? null : root.getParentTreeCursor();
                return section != null && section.getValue() instanceof Json.Member parent &&
                       SECTIONS.contains(key(parent)) && document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static boolean selected(String declaration) {
        String candidate = declaration.startsWith("^") || declaration.startsWith("~") ?
                declaration.substring(1) : declaration;
        return SOURCES.contains(candidate) && declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+");
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
