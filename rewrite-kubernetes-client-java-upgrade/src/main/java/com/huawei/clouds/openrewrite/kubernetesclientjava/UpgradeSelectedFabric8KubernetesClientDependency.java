package com.huawei.clouds.openrewrite.kubernetesclientjava;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only the Fabric8 Kubernetes Client versions listed in the migration spreadsheet. */
public final class UpgradeSelectedFabric8KubernetesClientDependency extends Recipe {
    private static final String COORDINATE_PREFIX = "io.fabric8:kubernetes-client:";
    private static final String TARGET = "7.3.1";
    private static final Set<String> VERSIONS = Set.of("5.12.0", "5.12.4");
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime",
            "runtimeOnly", "annotationProcessor", "testCompile", "testCompileOnly",
            "testImplementation", "testRuntime", "testRuntimeOnly", "testFixturesApi",
            "testFixturesImplementation", "testFixturesRuntimeOnly", "kapt", "ksp"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected Fabric8 Kubernetes Client declarations to 7.3.1";
    }

    @Override
    public String getDescription() {
        return "Upgrade only io.fabric8:kubernetes-client 5.12.0 and 5.12.4 declarations whose " +
               "version ownership is local and unambiguous; externally managed and shared-property versions are preserved.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                Path path = source.getSourcePath();
                String fileName = path.getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> propertyValues = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(properties ->
                properties.getChildren().stream()
                        .filter(Xml.Tag.class::isInstance)
                        .map(Xml.Tag.class::cast)
                        .forEach(property -> property.getValue().ifPresent(value ->
                                propertyValues.put(property.getName(), value))));

        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> eligibleReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                Matcher matcher = PROPERTY_REFERENCE.matcher(charData.getText());
                while (matcher.find()) {
                    allReferences.merge(matcher.group(1), 1, Integer::sum);
                }
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isFabric8Client(tag)) {
                    propertyName(tag).filter(name -> propertyValues.get(name) != null &&
                                                      VERSIONS.contains(propertyValues.get(name)))
                            .ifPresent(name -> eligibleReferences.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        eligibleReferences.forEach((name, count) -> {
            if (count.equals(allReferences.get(name))) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isPropertiesChild(getCursor(), t) && safeProperties.contains(t.getName()) &&
                    t.getValue().filter(VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (!isFabric8Client(t)) {
                    return t;
                }
                String version = t.getChildValue("version").orElse(null);
                if (version != null && VERSIONS.contains(version)) {
                    return t.withChildValue("version", TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isFabric8Client(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) &&
               "io.fabric8".equals(tag.getChildValue("groupId").orElse(null)) &&
               "kubernetes-client".equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static java.util.Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse(""));
        return matcher.matches() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }

    private static boolean isPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof Xml.Tag parentTag && "properties".equals(parentTag.getName()) &&
               !"properties".equals(tag.getName());
    }

    private static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(COORDINATE_PREFIX) ||
            !VERSIONS.contains(value.substring(COORDINATE_PREFIX.length()))) {
            return literal;
        }
        String replacement = COORDINATE_PREFIX + TARGET;
        String valueSource = literal.getValueSource();
        return literal.withValue(replacement).withValueSource(
                valueSource == null ? null : valueSource.replace(value, replacement));
    }
}
