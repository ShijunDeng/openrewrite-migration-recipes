package com.huawei.clouds.openrewrite.singlespaangular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrade only direct package.json declarations selected by the workbook. */
public final class UpgradeSelectedSingleSpaAngularDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected single-spa-angular dependencies to 9.2.0";
    }

    @Override
    public String getDescription() {
        return "Changes direct exact, caret, or tilde package.json declarations only when anchored to one of the six workbook source versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                String declaration = SingleSpaAngularSupport.string(visited);
                if (!SingleSpaAngularSupport.packageJson(document.getSourcePath()) ||
                    !SingleSpaAngularSupport.PACKAGE.equals(SingleSpaAngularSupport.key(visited)) ||
                    !SingleSpaAngularSupport.SECTIONS.contains(SingleSpaAngularSupport.directSection(getCursor())) ||
                    !SingleSpaAngularSupport.selected(declaration)) return visited;
                return SingleSpaAngularSupport.replaceString(visited,
                        SingleSpaAngularSupport.targetDeclaration(declaration));
            }
        };
    }
}
