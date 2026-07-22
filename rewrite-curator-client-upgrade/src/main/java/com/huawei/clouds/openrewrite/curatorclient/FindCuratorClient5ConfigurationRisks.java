package com.huawei.clouds.openrewrite.curatorclient;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;
import java.util.regex.Pattern;

/** Mark exact parsed configuration values that retain Curator 5's unsupported ZooKeeper 3.4 baseline. */
public final class FindCuratorClient5ConfigurationRisks extends Recipe {
    private static final Set<String> KEYS = Set.of("zookeeper.version", "zookeeper-version");
    private static final Pattern ZOOKEEPER_34 = Pattern.compile("3\\.4(?:\\..+)?");
    private static final String MESSAGE =
            "Curator 5 cannot run with ZooKeeper 3.4.x; migrate the owning client/server baseline and verify ensemble rolling compatibility, TLS/SASL, ACLs, watches, sessions, and container nodes";

    @Override
    public String getDisplayName() {
        return "Find Apache Curator Client 5 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact zookeeper.version/zookeeper-version values pinned to 3.4.x in parsed properties, YAML, " +
               "and non-POM XML without selecting a platform ZooKeeper version automatically.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    !UpgradeSelectedCuratorClientDependency.isProjectPath(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) {
                    return new PropertiesIsoVisitor<ExecutionContext>() {
                        @Override
                        public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                            Properties.Entry visited = super.visitEntry(entry, p);
                            return KEYS.contains(visited.getKey()) &&
                                   ZOOKEEPER_34.matcher(visited.getValue().getText().trim()).matches()
                                    ? mark(visited, MESSAGE) : visited;
                        }
                    }.visitNonNull(properties, ctx);
                }
                if (tree instanceof Yaml.Documents yaml) {
                    return new YamlIsoVisitor<ExecutionContext>() {
                        private final JsonPathMatcher dotted = new JsonPathMatcher("$.zookeeper.version");
                        private final JsonPathMatcher dashed = new JsonPathMatcher("$.zookeeper-version");

                        @Override
                        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext p) {
                            Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, p);
                            String value = visited.getValue() instanceof Yaml.Scalar scalar
                                    ? scalar.getValue().trim() : "";
                            return (dotted.matches(getCursor()) || dashed.matches(getCursor())) &&
                                   ZOOKEEPER_34.matcher(value).matches() ? mark(visited, MESSAGE) : visited;
                        }
                    }.visitNonNull(yaml, ctx);
                }
                String fileName = source.getSourcePath().getFileName() == null ? "" :
                        source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                            Xml.Tag visited = super.visitTag(tag, p);
                            return KEYS.contains(visited.getName()) && visited.getValue().map(String::trim)
                                    .filter(value -> ZOOKEEPER_34.matcher(value).matches()).isPresent()
                                    ? mark(visited, MESSAGE) : visited;
                        }
                    }.visitNonNull(xml, ctx);
                }
                return tree;
            }
        };
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
