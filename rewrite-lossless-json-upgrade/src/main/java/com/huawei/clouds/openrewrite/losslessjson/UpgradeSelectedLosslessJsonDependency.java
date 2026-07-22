package com.huawei.clouds.openrewrite.losslessjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only safe scalar package.json declarations anchored to spreadsheet versions. */
public final class UpgradeSelectedLosslessJsonDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected lossless-json dependencies to 4.0.1";
    }

    @Override
    public String getDescription() {
        return "Changes exact, caret, or tilde direct dependency declarations anchored to 2.0.8 and 2.0.11; compound ranges, " +
               "protocols, aliases, catalogs, overrides, lockfiles, variables, and unlisted versions are unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!LosslessJsonManifestSupport.isPackageJson(document.getSourcePath()) ||
                    !LosslessJsonManifestSupport.PACKAGE.equals(LosslessJsonManifestSupport.key(visited)) ||
                    LosslessJsonManifestSupport.directDependencySection(getCursor()).isEmpty()) {
                    return visited;
                }
                String value = LosslessJsonManifestSupport.stringValue(visited);
                if (!selected(value)) {
                    return visited;
                }
                Json.Literal literal = (Json.Literal) visited.getValue();
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(LosslessJsonManifestSupport.TARGET_VERSION)
                        .withSource(quote + LosslessJsonManifestSupport.TARGET_VERSION + quote));
            }
        };
    }

    private static boolean selected(String declaration) {
        if (declaration == null || declaration.isEmpty()) {
            return false;
        }
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) {
            candidate = candidate.substring(1);
        }
        return LosslessJsonManifestSupport.SOURCE_VERSIONS.contains(candidate);
    }
}
