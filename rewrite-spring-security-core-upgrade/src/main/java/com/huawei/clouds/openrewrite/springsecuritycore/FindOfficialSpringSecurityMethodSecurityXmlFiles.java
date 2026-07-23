package com.huawei.clouds.openrewrite.springsecuritycore;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/**
 * Selects XML documents on which the official unprefixed recipe is
 * namespace-safe for every tag it can match.
 */
public final class FindOfficialSpringSecurityMethodSecurityXmlFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find namespace-safe XML for the official method-security recipe";
    }

    @Override
    public String getDescription() {
        return "Select XML documents containing unprefixed global-method-security or method-security elements " +
               "only when every such element resolves to the Spring Security namespace.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Xml.Document document) ||
                    SpringSecurityCoreSupport.generated(document.getSourcePath())) {
                    return tree;
                }
                boolean[] found = {false};
                boolean[] unsafe = {false};
                new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                        Xml.Tag visited = super.visitTag(tag, ec);
                        if (isOfficialTarget(visited.getName())) {
                            found[0] = true;
                            if (!SpringSecurityCoreXmlSupport.securityElement(
                                    getCursor(), visited)) {
                                unsafe[0] = true;
                            }
                        }
                        return visited;
                    }
                }.visitNonNull(document, ctx);
                return found[0] && !unsafe[0] ? SearchResult.found(document) : tree;
            }
        };
    }

    private static boolean isOfficialTarget(String name) {
        return "global-method-security".equals(name) || "method-security".equals(name);
    }
}
