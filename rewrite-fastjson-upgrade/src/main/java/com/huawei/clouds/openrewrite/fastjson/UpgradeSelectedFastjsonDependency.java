package com.huawei.clouds.openrewrite.fastjson;

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

/** Upgrade only Fastjson versions whose complete value is visible in the migration spreadsheet. */
public class UpgradeSelectedFastjsonDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "1.2.40", "1.2.70", "1.2.71", "1.2.75", "1.2.83", "1.2.83_noneautotype",
            "2.0.12", "2.0.13", "2.0.14", "2.0.16"
    );
    private static final String TARGET = "2.0.62";
    private static final String OLD_GROUP = "com.alibaba";
    private static final String OLD_ARTIFACT = "fastjson";
    private static final String OLD_PREFIX = OLD_GROUP + ":" + OLD_ARTIFACT + ":";
    private static final String NATIVE_GROUP = "com.alibaba.fastjson2";
    private static final String NATIVE_ARTIFACT = "fastjson2";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", ".gradle", ".idea", "node_modules"
    );

    private final boolean nativeApi;

    public UpgradeSelectedFastjsonDependency() {
        this(false);
    }

    protected UpgradeSelectedFastjsonDependency(boolean nativeApi) {
        this.nativeApi = nativeApi;
    }

    @Override
    public String getDisplayName() {
        return nativeApi
                ? "Migrate spreadsheet-selected Fastjson declarations to native Fastjson2 2.0.62"
                : "Upgrade spreadsheet-selected Fastjson declarations to compatibility version 2.0.62";
    }

    @Override
    public String getDescription() {
        return "Change only direct com.alibaba:fastjson declarations whose literal or safely isolated Maven-property " +
               "version exactly matches a value visible in the spreadsheet; do not expand hidden, ranged, managed, or shared versions.";
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
                if (!isProjectPath(path) || path.getFileName() == null) {
                    return tree;
                }
                String fileName = path.getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!GRADLE_CONFIGURATIONS.contains(m.getSimpleName())) {
                                return m;
                            }
                            String group = invocationMapValue(m, "group");
                            String name = invocationMapValue(m, "name");
                            String version = invocationMapValue(m, "version");
                            if (OLD_GROUP.equals(group) && OLD_ARTIFACT.equals(name) && SOURCE_VERSIONS.contains(version)) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry ? upgradeMapEntry(entry) : argument).toList());
                            }
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

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

    private Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> propertyValues = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(properties ->
                properties.getChildren().stream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                        .forEach(property -> property.getValue().ifPresent(value -> propertyValues.put(property.getName(), value))));

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
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                Matcher matcher = PROPERTY_REFERENCE.matcher(attribute.getValueAsString());
                while (matcher.find()) {
                    allReferences.merge(matcher.group(1), 1, Integer::sum);
                }
                return super.visitAttribute(attribute, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isFastjson(tag)) {
                    propertyName(tag).filter(name -> SOURCE_VERSIONS.contains(propertyValues.get(name)))
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
                    t.getValue().filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (!isFastjson(t) || !eligibleDependency(t, safeProperties)) {
                    return t;
                }
                Xml.Tag changed = t;
                if (nativeApi) {
                    changed = changed.withChildValue("groupId", NATIVE_GROUP)
                            .withChildValue("artifactId", NATIVE_ARTIFACT);
                }
                if (changed.getChildValue("version").filter(SOURCE_VERSIONS::contains).isPresent()) {
                    changed = changed.withChildValue("version", TARGET);
                }
                return changed;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isFastjson(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) &&
               OLD_GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               OLD_ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static boolean eligibleDependency(Xml.Tag dependency, Set<String> safeProperties) {
        String version = dependency.getChildValue("version").orElse("");
        if (SOURCE_VERSIONS.contains(version)) {
            return true;
        }
        Matcher matcher = PROPERTY_REFERENCE.matcher(version);
        return matcher.matches() && safeProperties.contains(matcher.group(1));
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

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static String invocationMapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!OLD_GROUP.equals(mapValue(map, "group")) || !OLD_ARTIFACT.equals(mapValue(map, "name")) ||
            !SOURCE_VERSIONS.contains(mapValue(map, "version"))) {
            return map;
        }
        return map.withElements(map.getElements().stream().map(this::upgradeMapEntry).toList());
    }

    private G.MapEntry upgradeMapEntry(G.MapEntry entry) {
        String key = mapKey(entry);
        if (!(entry.getValue() instanceof J.Literal literal)) {
            return entry;
        }
        if ("version".equals(key) && literal.getValue() instanceof String version && SOURCE_VERSIONS.contains(version)) {
            return entry.withValue(replaceLiteral(literal, version, TARGET));
        }
        if (nativeApi && "group".equals(key) && OLD_GROUP.equals(literal.getValue())) {
            return entry.withValue(replaceLiteral(literal, OLD_GROUP, NATIVE_GROUP));
        }
        if (nativeApi && "name".equals(key) && OLD_ARTIFACT.equals(literal.getValue())) {
            return entry.withValue(replaceLiteral(literal, OLD_ARTIFACT, NATIVE_ARTIFACT));
        }
        return entry;
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(OLD_PREFIX) ||
            !SOURCE_VERSIONS.contains(value.substring(OLD_PREFIX.length()))) {
            return literal;
        }
        String prefix = nativeApi ? NATIVE_GROUP + ":" + NATIVE_ARTIFACT + ":" : OLD_PREFIX;
        return replaceLiteral(literal, value, prefix + TARGET);
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }

    static boolean isProjectPath(Path path) {
        for (Path part : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(part.toString())) {
                return false;
            }
        }
        return true;
    }
}
