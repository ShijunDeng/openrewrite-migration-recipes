package com.huawei.clouds.openrewrite.testinglibraryjestdom;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Migrates exact jest-dom setup module references in root package.json runner configuration. */
public final class MigrateJestDomPackageConfiguration extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic jest-dom package.json setup entries";
    }

    @Override
    public String getDescription() {
        return "Replaces removed v5 setup entries in root Jest setupFilesAfterEnv and selects the v6 Vitest entry " +
               "inside root Vitest setupFiles without changing arbitrary nested configuration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!JestDomSupport.isPackageJson(document.getSourcePath()) ||
                    !(visited.getValue() instanceof String module)) return visited;
                boolean jestSetup = JestDomSupport.underRootSection(getCursor(), "jest") &&
                                    JestDomSupport.underMember(getCursor(), "setupFilesAfterEnv");
                boolean vitestSetup = JestDomSupport.underRootSection(getCursor(), "vitest") &&
                                      JestDomSupport.underMember(getCursor(), "setupFiles");
                if (!jestSetup && !vitestSetup) return visited;
                String replacement = JestDomSupport.migratedModule(module);
                if (vitestSetup && (JestDomSupport.PACKAGE.equals(replacement) ||
                                    JestDomSupport.LEGACY_SIDE_EFFECT_ENTRIES.contains(module))) {
                    replacement = JestDomSupport.PACKAGE + "/vitest";
                }
                return module.equals(replacement) ? visited : JestDomSupport.replaceJsonString(visited, replacement);
            }
        };
    }
}
