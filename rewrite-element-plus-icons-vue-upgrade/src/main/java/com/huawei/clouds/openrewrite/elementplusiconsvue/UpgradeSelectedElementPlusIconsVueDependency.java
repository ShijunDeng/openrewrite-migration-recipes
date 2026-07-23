package com.huawei.clouds.openrewrite.elementplusiconsvue;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrade only direct workbook-selected package declarations. */
public final class UpgradeSelectedElementPlusIconsVueDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected @element-plus/icons-vue dependency to 2.3.2";
    }

    @Override
    public String getDescription() {
        return "Changes exact, caret, or tilde direct declarations at 0.2.4, 2.0.10, or 2.1.0; " +
               "ranges, protocols, overrides, catalogs, lockfiles, and unlisted versions remain untouched.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!ElementPlusIconsVueSupport.isPackageJson(document.getSourcePath()) ||
                    !ElementPlusIconsVueSupport.PACKAGE.equals(ElementPlusIconsVueSupport.key(visited)) ||
                    ElementPlusIconsVueSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String declaration = ElementPlusIconsVueSupport.stringValue(visited);
                if (!ElementPlusIconsVueSupport.selected(declaration) ||
                    !(visited.getValue() instanceof Json.Literal literal)) return visited;
                return visited.withValue(ElementPlusIconsVueSupport.replaceJsonString(
                        literal, ElementPlusIconsVueSupport.targetDeclaration(declaration)));
            }
        };
    }
}
