package com.huawei.clouds.openrewrite.okhttp;

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
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.regex.Pattern;

/** Marks parsed runtime configuration that names obsolete or encapsulated OkHttp packages. */
public final class FindOkHttp5ConfigurationRisks extends Recipe {
    private static final Pattern INTERNAL = Pattern.compile(
            "(?:^|[^A-Za-z0-9_$])okhttp3[./]internal(?:[.$/]|$)");
    private static final Pattern MOCK_WEB_SERVER = Pattern.compile(
            "(?:^|[^A-Za-z0-9_$])okhttp3[./]mockwebserver(?:[.$/]|$)");
    private static final String INTERNAL_MESSAGE =
            "This configuration names okhttp3.internal, which is not public and is strongly encapsulated by OkHttp 5 JPMS modules; replace the integration instead of retaining reflection or add-exports workarounds";
    private static final String MOCK_MESSAGE =
            "This configuration names the obsolete okhttp3.mockwebserver package; choose mockwebserver3 core/JUnit integration or temporary compatibility and update reflection/test discovery deliberately";

    @Override
    public String getDisplayName() {
        return "Find OkHttp 5 configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact okhttp3.internal and okhttp3.mockwebserver package references in parsed properties, " +
               "YAML, and non-POM XML without guessing replacement configuration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedOkHttpDependency.generated(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) {
                    return new PropertiesIsoVisitor<ExecutionContext>() {
                        @Override
                        public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext executionContext) {
                            Properties.Entry visited = super.visitEntry(entry, executionContext);
                            String message = message(visited.getValue().getText());
                            return message == null ? visited : mark(visited, message);
                        }
                    }.visitNonNull(properties, ctx);
                }
                if (tree instanceof Yaml.Documents yaml) {
                    return new YamlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext executionContext) {
                            Yaml.Scalar visited = super.visitScalar(scalar, executionContext);
                            String message = message(visited.getValue());
                            return message == null ? visited : mark(visited, message);
                        }
                    }.visitNonNull(yaml, ctx);
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag visited = super.visitTag(tag, executionContext);
                            String message = visited.getValue().map(FindOkHttp5ConfigurationRisks::message)
                                    .orElse(null);
                            return message == null ? visited : mark(visited, message);
                        }
                    }.visitNonNull(xml, ctx);
                }
                return tree;
            }
        };
    }

    private static String message(String value) {
        if (value == null) return null;
        if (INTERNAL.matcher(value).find()) return INTERNAL_MESSAGE;
        if (MOCK_WEB_SERVER.matcher(value).find()) return MOCK_MESSAGE;
        return null;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
