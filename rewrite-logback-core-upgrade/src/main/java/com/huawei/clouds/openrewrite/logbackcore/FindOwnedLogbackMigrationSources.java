package com.huawei.clouds.openrewrite.logbackcore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.tree.Xml;

/** Limits official Java and XML leaf recipes to authored sources with an unambiguous Logback owner. */
public final class FindOwnedLogbackMigrationSources extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find owned Logback Core migration sources";
    }

    @Override
    public String getDescription() {
        return "Find authored Java sources and owned Logback XML configurations outside generated, build, cache, " +
               "installation, and report directories before applying official OpenRewrite leaf recipes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedLogbackCoreDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                if (tree instanceof J.CompilationUnit ||
                    tree instanceof Xml.Document document && logbackConfiguration(document)) {
                    return SearchResult.found(source);
                }
                return tree;
            }
        };
    }

    static boolean logbackConfiguration(Xml.Document document) {
        String file = document.getSourcePath().getFileName().toString().toLowerCase();
        if (file.endsWith(".xml") && file.contains("logback")) {
            return true;
        }
        return "configuration".equals(document.getRoot().getName()) &&
               document.printAll().contains("ch.qos.logback");
    }
}
