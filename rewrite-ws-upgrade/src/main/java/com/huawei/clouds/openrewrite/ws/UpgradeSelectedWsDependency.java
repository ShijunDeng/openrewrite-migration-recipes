package com.huawei.clouds.openrewrite.ws;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.UUID;

/** Upgrades only workbook-selected ws declarations. */
public final class UpgradeSelectedWsDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected ws dependencies to 8.21.0";
    }

    @Override
    public String getDescription() {
        return "Changes only direct ws declarations equal to 8.5.0, 8.16.0, 8.18.3, or 8.20.0, with optional caret or tilde, preserving that operator.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!WsSupport.isPackageJson(document.getSourcePath()) ||
                    !WsSupport.PACKAGE.equals(WsSupport.key(visited)) ||
                    WsSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String replacement = WsSupport.replacement(WsSupport.stringValue(visited));
                if (replacement == null || !(visited.getValue() instanceof Json.Literal literal)) return visited;
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                String original = WsSupport.normalizedVersion((String) literal.getValue());
                Json.Literal migrated = literal.withValue(replacement).withSource(quote + replacement + quote);
                return visited.withValue(migrated.withMarkers(migrated.getMarkers()
                        .add(new OriginalWsVersion(UUID.randomUUID(), original))));
            }
        };
    }
}
