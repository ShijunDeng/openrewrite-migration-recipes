package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

/** Upgrade only spreadsheet-visible scalar declarations. */
public final class UpgradeSelectedAngularPlatformBrowserDynamicDependency extends Recipe {
    static final String PACKAGE = "@angular/platform-browser-dynamic";
    static final String TARGET = "20.3.26";
    static final Set<String> VERSIONS = Set.of(
            "10.0.14", "10.2.5", "11.2.14", "12.2.10", "12.2.13", "12.2.14",
            "12.2.16", "12.2.17", "13.1.3", "13.2.6");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");

    @Override
    public String getDisplayName() {
        return "Upgrade selected Angular platform-browser-dynamic declarations to 20.3.26";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact, caret, or tilde direct declarations for versions explicitly visible in the spreadsheet.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !PACKAGE.equals(key(visited)) || !isDirectDependency() ||
                    !(visited.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration) || !selected(declaration)) {
                    return visited;
                }
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(TARGET).withSource(quote + TARGET + quote));
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
                return section != null && section.getValue() instanceof Json.Member sectionMember &&
                       SECTIONS.contains(key(sectionMember)) && document != null &&
                       document.getValue() instanceof Json.Document;
            }
        };
    }

    static boolean selected(String declaration) {
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return VERSIONS.contains(candidate) &&
               (declaration.equals(candidate) || declaration.equals("^" + candidate) || declaration.equals("~" + candidate));
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
