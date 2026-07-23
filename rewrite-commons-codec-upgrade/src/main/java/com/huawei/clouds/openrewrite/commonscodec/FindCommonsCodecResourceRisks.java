package com.huawei.clouds.openrewrite.commonscodec;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;

/** Mark OSGi metadata because Codec 1.19 restored package uses constraints. */
public final class FindCommonsCodecResourceRisks extends Recipe {
    static final String OSGI =
            "Commons Codec 1.19 restored OSGi package uses constraints; regenerate and resolve this manifest/bnd " +
            "metadata against the complete bundle graph instead of copying the old Import-Package wiring";
    private static final Set<String> BUNDLE_TAGS = Set.of("Import-Package", "Export-Package", "Embed-Dependency", "instructions");

    @Override
    public String getDisplayName() {
        return "Find Apache Commons Codec OSGi metadata risks";
    }

    @Override
    public String getDescription() {
        return "Marks bnd, manifest, and Maven bundle-plugin package instructions that reference Commons Codec.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedCommonsCodecDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof PlainText text &&
                    ("bnd.bnd".equals(file) || "MANIFEST.MF".equals(file)) &&
                    text.getText().contains("org.apache.commons.codec")) {
                    return SearchResult.found(text, OSGI);
                }
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                            Xml.CharData visited = super.visitCharData(charData, ec);
                            if (!visited.getText().contains("org.apache.commons.codec")) return visited;
                            Object parent = getCursor().getParentTreeCursor().getValue();
                            return parent instanceof Xml.Tag tag && BUNDLE_TAGS.contains(tag.getName()) &&
                                   bundlePluginOwner(getCursor())
                                    ? SearchResult.found(visited, OSGI) : visited;
                        }
                    }.visitNonNull(document, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean bundlePluginOwner(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (!(current.getValue() instanceof Xml.Tag plugin) || !"plugin".equals(plugin.getName())) continue;
            String artifact = plugin.getChildValue("artifactId").orElse("");
            if (!Set.of("maven-bundle-plugin", "bnd-maven-plugin").contains(artifact)) return false;
            Cursor plugins = treeParent(current);
            if (plugins == null) return false;
            if (!(plugins.getValue() instanceof Xml.Tag pluginsTag) || !"plugins".equals(pluginsTag.getName())) return false;
            Cursor owner = treeParent(plugins);
            if (owner == null) return false;
            if (owner.getValue() instanceof Xml.Tag build && "build".equals(build.getName())) {
                return projectOrProfile(treeParent(owner));
            }
            if (!(owner.getValue() instanceof Xml.Tag management) || !"pluginManagement".equals(management.getName())) return false;
            Cursor build = treeParent(owner);
            if (build == null) return false;
            return build.getValue() instanceof Xml.Tag buildTag && "build".equals(buildTag.getName()) &&
                   projectOrProfile(treeParent(build));
        }
        return false;
    }

    private static boolean projectOrProfile(Cursor cursor) {
        if (cursor == null) return false;
        if (!(cursor.getValue() instanceof Xml.Tag tag)) return false;
        if ("project".equals(tag.getName())) {
            Cursor parent = treeParent(cursor);
            return parent != null && parent.getValue() instanceof Xml.Document;
        }
        if (!"profile".equals(tag.getName())) return false;
        Cursor profiles = treeParent(cursor);
        if (profiles == null) return false;
        Cursor project = treeParent(profiles);
        if (project == null) return false;
        Cursor document = treeParent(project);
        return profiles.getValue() instanceof Xml.Tag profilesTag && "profiles".equals(profilesTag.getName()) &&
               project.getValue() instanceof Xml.Tag projectTag && "project".equals(projectTag.getName()) &&
               document != null && document.getValue() instanceof Xml.Document;
    }

    private static Cursor treeParent(Cursor cursor) {
        for (Cursor parent = cursor.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.getValue() instanceof Tree) return parent;
        }
        return null;
    }
}
