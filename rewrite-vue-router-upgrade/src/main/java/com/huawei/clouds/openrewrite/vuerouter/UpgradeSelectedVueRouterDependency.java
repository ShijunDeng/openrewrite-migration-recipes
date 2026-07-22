package com.huawei.clouds.openrewrite.vuerouter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only XLSX-visible exact, caret, or tilde single vue-router declarations. */
public final class UpgradeSelectedVueRouterDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected Vue Router dependencies to 5.0.3";
    }

    @Override
    public String getDescription() {
        return "Changes only direct vue-router declarations anchored to an XLSX-visible source version; " +
               "complex ranges, protocols, dynamic declarations, and unlisted versions remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!VueRouterManifestSupport.isPackageJson(document.getSourcePath()) ||
                    !VueRouterManifestSupport.PACKAGE.equals(VueRouterManifestSupport.key(visited)) ||
                    VueRouterManifestSupport.directSection(getCursor()).isEmpty() ||
                    VueRouterManifestSupport.selectedVersion(VueRouterManifestSupport.stringValue(visited)) == null) {
                    return visited;
                }
                Json.Literal literal = (Json.Literal) visited.getValue();
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(VueRouterManifestSupport.TARGET)
                        .withSource(quote + VueRouterManifestSupport.TARGET + quote));
            }
        };
    }
}
