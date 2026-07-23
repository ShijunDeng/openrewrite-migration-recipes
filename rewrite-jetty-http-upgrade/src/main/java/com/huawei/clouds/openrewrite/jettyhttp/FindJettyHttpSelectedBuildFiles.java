package com.huawei.clouds.openrewrite.jettyhttp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.tree.Xml;

/**
 * Exact local precondition for the official Java baseline recipe. Selection
 * deliberately uses the same conservative project analysis as source/config
 * AUTO, including exclusive Maven property ownership and mixed-version blocks.
 */
public final class FindJettyHttpSelectedBuildFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find builds with an exact selected Jetty HTTP dependency";
    }

    @Override
    public String getDescription() {
        return "Locate Maven or root Gradle builds that unambiguously own one exact " +
               "workbook-approved Jetty HTTP source version, so the official Java 17 " +
               "recipe cannot activate for mixed, shared, target, higher or off-whitelist owners.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    JettyHttpSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                MarkSelectedJettyHttpProjects.ProjectSelection selection = null;
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    selection = MarkSelectedJettyHttpProjects.mavenState(document, ctx);
                } else if (tree instanceof G.CompilationUnit groovy &&
                           "build.gradle".equals(file)) {
                    selection = MarkSelectedJettyHttpProjects.groovyState(groovy, ctx);
                } else if (tree instanceof K.CompilationUnit kotlin &&
                           "build.gradle.kts".equals(file)) {
                    selection = MarkSelectedJettyHttpProjects.kotlinState(kotlin, ctx);
                }
                return selection != null &&
                       selection.state() == MarkSelectedJettyHttpProjects.State.SELECTED
                        ? SearchResult.found(tree)
                        : tree;
            }
        };
    }
}
