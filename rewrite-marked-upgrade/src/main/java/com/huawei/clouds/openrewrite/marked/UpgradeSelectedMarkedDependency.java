package com.huawei.clouds.openrewrite.marked;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only spreadsheet-selected exact, caret, or tilde single npm versions. */
public final class UpgradeSelectedMarkedDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected Marked dependencies to 17.0.6";
    }

    @Override
    public String getDescription() {
        return "Changes only direct marked declarations that are an exact, caret, or tilde single value anchored " +
               "to one of the nine spreadsheet versions; complex ranges and non-registry specs remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!MarkedManifestSupport.isPackageJson(document.getSourcePath()) ||
                    !MarkedManifestSupport.PACKAGE.equals(MarkedManifestSupport.key(visited)) ||
                    MarkedManifestSupport.directDependencySection(getCursor()).isEmpty()) {
                    return visited;
                }
                String declaration = MarkedManifestSupport.stringValue(visited);
                if (MarkedManifestSupport.selectedVersion(declaration) == null) {
                    return visited;
                }
                Json.Literal literal = (Json.Literal) visited.getValue();
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(MarkedManifestSupport.TARGET_VERSION)
                        .withSource(quote + MarkedManifestSupport.TARGET_VERSION + quote));
            }
        };
    }
}
