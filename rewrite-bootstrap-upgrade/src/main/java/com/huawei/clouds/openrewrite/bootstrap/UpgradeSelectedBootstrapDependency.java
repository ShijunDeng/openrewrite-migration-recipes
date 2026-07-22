package com.huawei.clouds.openrewrite.bootstrap;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only XLSX-visible direct Bootstrap declarations. */
public final class UpgradeSelectedBootstrapDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected Bootstrap dependencies to 5.3.6";
    }

    @Override
    public String getDescription() {
        return "Changes only exact, caret, or tilde direct bootstrap declarations anchored to an XLSX-visible source version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                return BootstrapSupport.isPackageJson(document.getSourcePath())
                        ? super.visitDocument(document, ctx) : document;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String declaration = BootstrapSupport.stringValue(visited);
                if (!BootstrapSupport.PACKAGE.equals(BootstrapSupport.key(visited)) ||
                    BootstrapSupport.directSection(getCursor()).isEmpty() || !BootstrapSupport.selected(declaration)) {
                    return visited;
                }
                Json.Literal literal = (Json.Literal) visited.getValue();
                String replacement = BootstrapSupport.targetDeclaration(declaration);
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(replacement).withSource(quote + replacement + quote));
            }
        };
    }
}
