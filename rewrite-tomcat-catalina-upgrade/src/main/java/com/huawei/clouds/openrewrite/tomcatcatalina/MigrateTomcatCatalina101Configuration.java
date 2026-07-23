package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;

/** Remove listener attributes whose Java-8 leak workarounds no longer exist on the Java-11 baseline. */
public final class MigrateTomcatCatalina101Configuration extends Recipe {
    static final Set<String> REMOVED_LISTENER_ATTRIBUTES = Set.of(
            "awtThreadProtection", "gcDaemonProtection", "ldapPoolProtection",
            "tokenPollerProtection", "xmlParsingProtection", "forkJoinCommonPoolProtection");
    private static final String LISTENER = "org.apache.catalina.core.JreMemoryLeakPreventionListener";

    @Override
    public String getDisplayName() {
        return "Remove obsolete Tomcat Java-8 leak-prevention attributes";
    }

    @Override
    public String getDescription() {
        return "Remove the six JreMemoryLeakPreventionListener attributes whose setters were removed in Tomcat 10.1 " +
               "because the corresponding leaks no longer exist on the required Java 11 baseline.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Xml.Document document) || !(tree instanceof SourceFile source) ||
                    UpgradeSelectedTomcatCatalinaDependency.generated(source.getSourcePath())) return tree;
                if (!"server.xml".equals(source.getSourcePath().getFileName().toString())) return tree;
                return new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                        Xml.Tag visited = super.visitTag(tag, ec);
                        if (!"Listener".equals(visited.getName()) ||
                            !underServer(getCursor()) ||
                            !LISTENER.equals(visited.getAttributes().stream()
                                    .filter(attribute -> "className".equals(attribute.getKeyAsString()))
                                    .map(Xml.Attribute::getValueAsString).findFirst().orElse(null))) return visited;
                        if (visited.getAttributes().stream().noneMatch(attribute ->
                                REMOVED_LISTENER_ATTRIBUTES.contains(attribute.getKeyAsString()))) return visited;
                        return visited.withAttributes(visited.getAttributes().stream()
                                .filter(attribute -> !REMOVED_LISTENER_ATTRIBUTES.contains(attribute.getKeyAsString()))
                                .toList());
                    }
                }.visitNonNull(document, ctx);
            }
        };
    }

    private static boolean underServer(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "Server".equals(tag.getName())) return true;
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }
}
