package com.huawei.clouds.openrewrite.chalk;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Strictly upgrades only XLSX-selected direct Chalk declarations. */
public final class UpgradeSelectedChalkDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected Chalk dependencies to 5.6.2";
    }

    @Override
    public String getDescription() {
        return "Upgrades direct package.json Chalk declarations anchored exactly, by caret, or by tilde to one of the three XLSX source versions while preserving the declared operator.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                return ChalkSupport.isPackageJson(document.getSourcePath()) ? super.visitDocument(document, ctx) : document;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!ChalkSupport.PACKAGE.equals(ChalkSupport.jsonKey(visited)) ||
                    ChalkSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String declaration = ChalkSupport.jsonString(visited);
                String operator = ChalkSupport.selectedDeclaration(declaration);
                if (operator == null || !(visited.getValue() instanceof Json.Literal literal)) return visited;
                String replacement = operator + ChalkSupport.TARGET_VERSION;
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(replacement).withSource(quote + replacement + quote));
            }
        };
    }
}
