package com.huawei.clouds.openrewrite.prettier;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrade only direct package.json declarations selected by the workbook. */
public final class UpgradeSelectedPrettierDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected Prettier dependencies to 3.6.2";
    }

    @Override
    public String getDescription() {
        return "Changes direct exact, caret, or tilde Prettier declarations only when anchored to workbook versions 1.19.1 or 2.8.8.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                String declaration = PrettierSupport.string(visited);
                if (!PrettierSupport.packageJson(document.getSourcePath()) ||
                    !PrettierSupport.PACKAGE.equals(PrettierSupport.key(visited)) ||
                    !PrettierSupport.SECTIONS.contains(PrettierSupport.directSection(getCursor())) ||
                    !PrettierSupport.selected(declaration)) return visited;
                return PrettierSupport.replaceString(visited, PrettierSupport.targetDeclaration(declaration));
            }
        };
    }
}
