package com.huawei.clouds.openrewrite.testinglibraryjestdom;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only the direct workbook-selected jest-dom npm declaration. */
public final class UpgradeSelectedJestDomDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected @testing-library/jest-dom dependency to 6.9.1";
    }

    @Override
    public String getDescription() {
        return "Changes direct package.json declarations anchored exactly, by caret, or by tilde at 5.17.0; " +
               "ranges, aliases, protocols, overrides, and lockfiles remain untouched.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!JestDomSupport.isPackageJson(document.getSourcePath()) ||
                    !JestDomSupport.PACKAGE.equals(JestDomSupport.key(visited)) ||
                    JestDomSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String declaration = JestDomSupport.stringValue(visited);
                if (!JestDomSupport.selected(declaration) || !(visited.getValue() instanceof Json.Literal literal)) {
                    return visited;
                }
                return visited.withValue(JestDomSupport.replaceJsonString(
                        literal, JestDomSupport.targetDeclaration(declaration)));
            }
        };
    }
}
