package com.huawei.clouds.openrewrite.kafka;

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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only kafka-clients versions whose exact values remain visible in the spreadsheet. */
public final class UpgradeSelectedKafkaClientsDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "2.4.1", "2.5.1", "3.1.2", "3.4.0", "3.4.1",
            "3.5.1", "3.6.0", "3.6.1", "3.6.2", "3.7.0"
    );
    static final String TARGET_VERSION = "4.1.2";
    static final String GROUP = "org.apache.kafka";
    static final String ARTIFACT = "kafka-clients";
    static final String COORDINATE_PREFIX = "org.apache.kafka:kafka-clients:";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", ".gradle", ".idea", "node_modules"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected kafka-clients dependencies to 4.1.2";
    }

    @Override
    public String getDescription() {
        return "Update direct Maven and Gradle org.apache.kafka:kafka-clients declarations only when their " +
               "resolved literal version is one of the exact spreadsheet source versions.";
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
                    return upgradePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!isGradleDependencyInvocation(getCursor(), m)) {
                                return m;
                            }
                            String group = invocationMapValue(m, "group");
                            String name = invocationMapValue(m, "name");
                            String version = invocationMapValue(m, "version");
                            if ("org.apache.kafka".equals(group) && "kafka-clients".equals(name) &&
                                isSourceVersion(version) && !hasGradleVariant(m)) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(upgradeVersionLiteral(literal)) : argument).toList());
                            }
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document upgradePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(tag -> tag.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast).forEach(property ->
                        property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()))));

        Set<String> eligibleProperties = new HashSet<>();
        Set<String> shadowedProperties = new HashSet<>();
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> clientReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                collectReferences(charData.getText(), allReferences);
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                collectReferences(attribute.getValueAsString(), allReferences);
                return super.visitAttribute(attribute, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isOwnedKafkaClientsDependency(getCursor(), tag)) {
                    propertyName(tag.getChildValue("version").orElse(null)).ifPresent(name -> {
                        clientReferences.merge(name, 1, Integer::sum);
                        if (isSourceVersion(properties.get(name))) {
                            eligibleProperties.add(name);
                        }
                    });
                }
                if (isPropertiesChild(getCursor(), tag) && !isProjectPropertiesChild(getCursor(), tag)) {
                    shadowedProperties.add(tag.getName());
                }
                return super.visitTag(tag, executionContext);
            }
        }.visitNonNull(document, ctx);

        Map<String, Boolean> exclusiveProperties = new HashMap<>();
        eligibleProperties.removeAll(shadowedProperties);
        eligibleProperties.forEach(name -> exclusiveProperties.put(name,
                clientReferences.getOrDefault(name, 0).equals(allReferences.getOrDefault(name, -1))));

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isOwnedKafkaClientsDependency(getCursor(), t)) {
                    String version = t.getChildValue("version").orElse(null);
                    if (isSourceVersion(version)) {
                        return t.withChildValue("version", TARGET_VERSION);
                    }
                    String property = propertyName(version).orElse(null);
                    if (property != null && eligibleProperties.contains(property) &&
                        !exclusiveProperties.getOrDefault(property, false)) {
                        return t.withChildValue("version", TARGET_VERSION);
                    }
                }
                if (isProjectPropertiesChild(getCursor(), t) && eligibleProperties.contains(t.getName()) &&
                    exclusiveProperties.getOrDefault(t.getName(), false) &&
                    isSourceVersion(t.getValue().orElse(null))) {
                    return t.withValue(TARGET_VERSION);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    static boolean hasClientCoordinates(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean isStandardJar(Xml.Tag tag) {
        boolean standardType = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty())
                .map("jar"::equals).orElse(true);
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        return standardType && noClassifier;
    }

    static boolean isMavenDependencyBlock(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) {
            return false;
        }
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag dependencies) || !"dependencies".equals(dependencies.getName())) {
            return false;
        }
        Cursor owner = parent.getParentTreeCursor();
        if (isProjectOrProfileOwner(owner)) {
            return true;
        }
        if (owner == null || !(owner.getValue() instanceof Xml.Tag ownerTag) ||
            !"dependencyManagement".equals(ownerTag.getName())) {
            return false;
        }
        return isProjectOrProfileOwner(owner.getParentTreeCursor());
    }

    private static boolean isOwnedKafkaClientsDependency(Cursor cursor, Xml.Tag tag) {
        return isMavenDependencyBlock(cursor, tag) && hasClientCoordinates(tag) && isStandardJar(tag);
    }

    private static Optional<String> propertyName(String version) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(version == null ? "" : version.trim());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            references.merge(matcher.group(1), 1, Integer::sum);
        }
    }

    private static boolean isPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof Xml.Tag properties && "properties".equals(properties.getName()) &&
               !"properties".equals(tag.getName());
    }

    private static boolean isProjectPropertiesChild(Cursor cursor, Xml.Tag tag) {
        if (!isPropertiesChild(cursor, tag)) {
            return false;
        }
        Cursor properties = cursor.getParentTreeCursor();
        Cursor owner = properties.getParentTreeCursor();
        return isProjectOwner(owner);
    }

    private static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor &&
                "dependencies".equals(ancestor.getSimpleName())) return true;
        }
        return false;
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

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!"org.apache.kafka".equals(mapValue(map, "group")) ||
            !"kafka-clients".equals(mapValue(map, "name")) ||
            !isSourceVersion(mapValue(map, "version")) || hasVariantKey(map)) {
            return map;
        }
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(upgradeVersionLiteral(literal)) : entry).toList());
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        if (entry.getKey() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return null;
    }

    static boolean hasGradleVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && isVariantKey(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && hasVariantKey(map));
    }

    private static boolean hasVariantKey(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> isVariantKey(mapKey(entry)));
    }

    private static boolean isVariantKey(String key) {
        return Set.of("classifier", "ext", "type").contains(key);
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(COORDINATE_PREFIX)) {
            return literal;
        }
        String version = value.substring(COORDINATE_PREFIX.length());
        if (!isSourceVersion(version)) {
            return literal;
        }
        String replacement = COORDINATE_PREFIX + TARGET_VERSION;
        String valueSource = literal.getValueSource();
        return literal.withValue(replacement).withValueSource(
                valueSource == null ? null : valueSource.replace(value, replacement));
    }

    private static J.Literal upgradeVersionLiteral(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !isSourceVersion(value)) {
            return literal;
        }
        String valueSource = literal.getValueSource();
        return literal.withValue(TARGET_VERSION).withValueSource(
                valueSource == null ? null : valueSource.replace(value, TARGET_VERSION));
    }

    static boolean isSourceVersion(String version) {
        return version != null && SOURCE_VERSIONS.contains(version.trim());
    }

    static boolean isProjectPath(Path path) {
        for (Path segment : path) {
            if (GENERATED_DIRECTORIES.contains(segment.toString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isProjectOrProfileOwner(Cursor owner) {
        if (isProjectOwner(owner)) return true;
        if (owner == null || !(owner.getValue() instanceof Xml.Tag profile) ||
            !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        Cursor project = profiles == null ? null : profiles.getParentTreeCursor();
        return profiles != null && profiles.getValue() instanceof Xml.Tag profilesTag &&
               "profiles".equals(profilesTag.getName()) && isProjectOwner(project);
    }

    private static boolean isProjectOwner(Cursor owner) {
        if (owner == null || !(owner.getValue() instanceof Xml.Tag project) ||
            !"project".equals(project.getName())) return false;
        Cursor document = owner.getParentTreeCursor();
        return document != null && document.getValue() instanceof Xml.Document;
    }
}
