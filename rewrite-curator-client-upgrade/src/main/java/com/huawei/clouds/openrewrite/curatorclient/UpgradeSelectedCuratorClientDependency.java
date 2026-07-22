package com.huawei.clouds.openrewrite.curatorclient;

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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only the curator-client versions explicitly visible in the workbook. */
public final class UpgradeSelectedCuratorClientDependency extends Recipe {
    static final String GROUP = "org.apache.curator";
    static final String ARTIFACT = "curator-client";
    static final String TARGET = "5.9.0";
    static final Set<String> SOURCE_VERSIONS = Set.of("2.7.1", "5.2.0", "5.4.0");
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> VARIANT_KEYS = Set.of("classifier", "ext", "type", "variant");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "generated-sources", "generated-test-sources",
            "install", "installed", ".gradle", ".idea", ".mvn", ".m2", ".yarn", ".cache",
            "coverage", "node_modules", "vendor"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Apache Curator Client declarations to 5.9.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only owned Maven or Gradle org.apache.curator:curator-client declarations at the three " +
               "literal workbook source versions, including provably exclusive local Curator version properties.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !isProjectPath(source.getSourcePath()) ||
                    source.getSourcePath().getFileName() == null) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return migrateGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return migrateKotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, Integer> definitions = new HashMap<>();
        Map<String, String> values = new HashMap<>();
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> curatorReferences = new HashMap<>();
        Map<String, Integer> clientReferences = new HashMap<>();

        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext p) {
                collectReferences(charData.getText(), allReferences);
                return super.visitCharData(charData, p);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext p) {
                collectReferences(attribute.getValueAsString(), allReferences);
                return super.visitAttribute(attribute, p);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (isMavenPropertyDefinition(getCursor(), visited)) {
                    definitions.merge(visited.getName(), 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(visited.getName(), value.trim()));
                }
                if (isStandardCuratorDependency(getCursor(), visited)) {
                    propertyName(visited).ifPresent(name -> {
                        curatorReferences.merge(name, 1, Integer::sum);
                        if (ARTIFACT.equals(visited.getChildValue("artifactId").orElse(null))) {
                            clientReferences.merge(name, 1, Integer::sum);
                        }
                    });
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        clientReferences.forEach((name, count) -> {
            if (count > 0 && SOURCE_VERSIONS.contains(values.get(name)) &&
                definitions.getOrDefault(name, 0) == 1 &&
                allReferences.getOrDefault(name, 0).equals(curatorReferences.getOrDefault(name, 0))) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (isMavenPropertyDefinition(getCursor(), visited) && safeProperties.contains(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withValue(TARGET);
                }
                if (isUpgradeableClientDependency(getCursor(), visited) &&
                    visited.getChildValue("version").map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withChildValue("version", TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                boolean direct = isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (!direct || hasVariant(visited)) return visited;
                if (GROUP.equals(mapValue(visited, "group")) && ARTIFACT.equals(mapValue(visited, "name")) &&
                    SOURCE_VERSIONS.contains(mapValue(visited, "version"))) {
                    return visited.withArguments(visited.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry) return upgradeVersionEntry(entry);
                        if (argument instanceof G.MapLiteral map && isUpgradeableMap(map)) return upgradeMap(map);
                        return argument;
                    }).toList());
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                boolean direct = isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, p);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                boolean direct = isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, p);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag container) ||
            !"dependencies".equals(container.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (owner == null || !(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if (isProjectOrProfile(owner)) return true;
        return "dependencyManagement".equals(ownerTag.getName()) && isProjectOrProfile(owner.getParentTreeCursor());
    }

    static boolean isTargetDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean isStandardArtifact(Xml.Tag tag) {
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        boolean jar = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty())
                .map("jar"::equals).orElse(true);
        return noClassifier && jar;
    }

    static boolean isStandardCuratorDependency(Cursor cursor, Xml.Tag tag) {
        String artifact = tag.getChildValue("artifactId").orElse("");
        return isProjectDependency(cursor, tag) && isStandardArtifact(tag) &&
               GROUP.equals(tag.getChildValue("groupId").orElse(null)) && artifact.startsWith("curator-");
    }

    private static boolean isUpgradeableClientDependency(Cursor cursor, Xml.Tag tag) {
        return isTargetDependency(cursor, tag) && isStandardArtifact(tag);
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        if (!(properties.getValue() instanceof Xml.Tag propertiesTag) ||
            !"properties".equals(propertiesTag.getName()) || "properties".equals(tag.getName())) return false;
        return isProjectOrProfile(properties.getParentTreeCursor());
    }

    static boolean isProjectOrProfile(Cursor cursor) {
        if (cursor == null || !(cursor.getValue() instanceof Xml.Tag tag)) return false;
        if ("project".equals(tag.getName())) return cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
        if (!"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        Cursor project = profiles == null ? null : profiles.getParentTreeCursor();
        return profiles != null && profiles.getValue() instanceof Xml.Tag profilesTag &&
               "profiles".equals(profilesTag.getName()) && project != null &&
               project.getValue() instanceof Xml.Tag projectTag && "project".equals(projectTag.getName()) &&
               project.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        Cursor dependencies = null;
        for (Cursor ancestor = cursor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getValue() instanceof J.MethodInvocation owner) {
                if ("dependencies".equals(owner.getSimpleName()) && owner.getSelect() == null) dependencies = ancestor;
                break;
            }
        }
        if (dependencies == null) return false;
        for (Cursor ancestor = dependencies.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getValue() instanceof J.MethodInvocation) return false;
        }
        return true;
    }

    static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").map(String::trim).orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static void collectReferences(String source, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(source);
        while (matcher.find()) references.merge(matcher.group(1), 1, Integer::sum);
    }

    static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && VARIANT_KEYS.contains(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && map.getElements().stream()
                        .anyMatch(entry -> VARIANT_KEYS.contains(mapKey(entry))));
    }

    static String mapValue(J.MethodInvocation invocation, String key) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal && literal.getValue() instanceof String value) return value;
            if (argument instanceof G.MapLiteral map) {
                String value = mapValue(map, key);
                if (value != null) return value;
            }
        }
        return null;
    }

    static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static boolean isUpgradeableMap(G.MapLiteral map) {
        return map.getElements().stream().noneMatch(entry -> VARIANT_KEYS.contains(mapKey(entry))) &&
               GROUP.equals(mapValue(map, "group")) && ARTIFACT.equals(mapValue(map, "name")) &&
               SOURCE_VERSIONS.contains(mapValue(map, "version"));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream()
                .map(UpgradeSelectedCuratorClientDependency::upgradeVersionEntry).toList());
    }

    private static G.MapEntry upgradeVersionEntry(G.MapEntry entry) {
        return "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                ? entry.withValue(upgradeVersionLiteral(literal)) : entry;
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !GROUP.equals(parts[0]) || !ARTIFACT.equals(parts[1]) ||
            !SOURCE_VERSIONS.contains(parts[2])) return literal;
        return replaceLiteral(literal, value, PREFIX + TARGET);
    }

    private static J.Literal upgradeVersionLiteral(J.Literal literal) {
        return literal.getValue() instanceof String value && SOURCE_VERSIONS.contains(value)
                ? replaceLiteral(literal, value, TARGET) : literal;
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String replacement) {
        String source = literal.getValueSource();
        return literal.withValue(replacement)
                .withValueSource(source == null ? null : source.replace(oldValue, replacement));
    }

    static boolean isProjectPath(Path path) {
        for (Path segment : path.normalize()) {
            String name = segment.toString().toLowerCase(Locale.ROOT);
            if (GENERATED_DIRECTORIES.contains(name) || name.startsWith("generated") ||
                name.startsWith("install")) return false;
        }
        return true;
    }
}
