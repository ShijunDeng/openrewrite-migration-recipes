package com.huawei.clouds.openrewrite.reactrouterdom;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/** Updates only npm single-version declarations explicitly visible in the migration spreadsheet. */
public final class UpgradeSelectedReactRouterDomDependency extends Recipe {
    static final String TARGET_VERSION = "6.30.4";
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "4.3.1", "5.2.0", "5.2.1", "5.3.0", "5.3.1",
            "5.3.2", "5.3.4", "6.10.0", "6.14.0", "6.14.2"
    );
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected react-router-dom declarations to 6.30.4";
    }

    @Override
    public String getDescription() {
        return "Updates exact, caret, or tilde single-version react-router-dom declarations explicitly visible " +
               "in the spreadsheet without inferring folded versions or changing ranges, protocols, central owners, or lockfiles.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !"react-router-dom".equals(key(visited)) ||
                    !(visited.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration) || !selected(declaration) ||
                    !isDirectDependency()) {
                    return visited;
                }
                return visited.withValue(literal.withSource('"' + TARGET_VERSION + '"').withValue(TARGET_VERSION));
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

    static boolean selected(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) {
            candidate = candidate.substring(1);
        }
        return SOURCE_VERSIONS.contains(candidate) &&
               declaration.matches("[~^]?" + Pattern.quote(candidate));
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
