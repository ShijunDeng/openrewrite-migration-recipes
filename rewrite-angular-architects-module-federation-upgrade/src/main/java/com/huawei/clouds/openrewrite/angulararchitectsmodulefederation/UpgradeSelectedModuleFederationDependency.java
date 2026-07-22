package com.huawei.clouds.openrewrite.angulararchitectsmodulefederation;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrade only the direct declarations selected by the workbook. */
public final class UpgradeSelectedModuleFederationDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Angular Architects Module Federation declarations";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact, caret, or tilde @angular-architects/module-federation 15.0.3 and 16.0.4 " +
               "declarations in root direct package.json dependency sections to 20.0.0.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!ModuleFederationSupport.packageJson(document.getSourcePath()) ||
                    !ModuleFederationSupport.PACKAGE.equals(ModuleFederationSupport.key(visited)) ||
                    !ModuleFederationSupport.SECTIONS.contains(ModuleFederationSupport.directSection(getCursor())) ||
                    !ModuleFederationSupport.selected(ModuleFederationSupport.string(visited))) return visited;
                return ModuleFederationSupport.replaceString(visited, ModuleFederationSupport.TARGET);
            }
        };
    }
}
