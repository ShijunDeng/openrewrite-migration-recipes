package com.huawei.clouds.openrewrite.fastglob;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only the exact workbook-selected fast-glob version family. */
public final class UpgradeSelectedFastGlobDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected fast-glob dependencies to 3.3.3";
    }

    @Override
    public String getDescription() {
        return "Changes only direct fast-glob declarations equal to 3.2.2, ^3.2.2, or ~3.2.2, preserving the operator.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!FastGlobSupport.isPackageJson(document.getSourcePath()) ||
                    !FastGlobSupport.PACKAGE.equals(FastGlobSupport.key(visited)) ||
                    FastGlobSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String replacement = FastGlobSupport.replacement(FastGlobSupport.stringValue(visited));
                if (replacement == null || !(visited.getValue() instanceof Json.Literal literal)) return visited;
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(replacement).withSource(quote + replacement + quote));
            }
        };
    }
}
