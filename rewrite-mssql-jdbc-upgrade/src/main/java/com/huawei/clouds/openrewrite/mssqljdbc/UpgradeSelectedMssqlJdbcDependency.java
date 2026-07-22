package com.huawei.clouds.openrewrite.mssqljdbc;

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

/** Upgrade only mssql-jdbc versions explicitly listed in the migration spreadsheet. */
public final class UpgradeSelectedMssqlJdbcDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "7.2.2.jre8", "9.4.1.jre11", "10.2.1.jre8", "10.2.3.jre8",
            "10.2.3.jre17", "11.2.2.jre11", "12.2.0.jre11", "12.3.0.jre17-preview"
    );
    static final String TARGET = "13.2.1.jre11";
    static final String GROUP = "com.microsoft.sqlserver";
    static final String ARTIFACT = "mssql-jdbc";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> MAP_VARIANT_KEYS = Set.of("classifier", "ext", "type", "variant");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "generated-sources", "generated-test-sources",
            "install", "installed", ".gradle", ".idea", ".m2", ".mvn", "node_modules", "vendor"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected Microsoft SQL Server JDBC declarations to 13.2.1.jre11";
    }

    @Override
    public String getDescription() {
        return "Upgrade only project Maven or Gradle declarations whose literal or exclusively owned root " +
               "property exactly matches one of the eight spreadsheet source versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !isProjectPath(source.getSourcePath())) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return migrateGroovy(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return migrateKotlin(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> propertyValues = new HashMap<>();
        Map<String, Integer> propertyDeclarations = new HashMap<>();
        Set<String> shadowedProperties = new HashSet<>();
        document.getRoot().getChild("properties").ifPresent(properties -> properties.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast).forEach(property -> {
                    property.getValue().ifPresent(value -> propertyValues.put(property.getName(), value.trim()));
                    propertyDeclarations.merge(property.getName(), 1, Integer::sum);
                }));

        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> driverReferences = new HashMap<>();
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
                Cursor parent = getCursor().getParentTreeCursor();
                if (parent.getValue() instanceof Xml.Tag properties && "properties".equals(properties.getName()) &&
                    !isRootProperty(getCursor(), tag)) shadowedProperties.add(tag.getName());
                if (isUpgradeableDriverDependency(getCursor(), tag)) {
                    propertyName(tag).ifPresent(name -> driverReferences.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, p);
            }
        }.visit(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        driverReferences.forEach((name, count) -> {
            if (!shadowedProperties.contains(name) && count.equals(allReferences.get(name)) &&
                propertyDeclarations.getOrDefault(name, 0) == 1 &&
                SOURCE_VERSIONS.contains(propertyValues.get(name))) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (isRootProperty(getCursor(), visited) && safeProperties.contains(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withValue(TARGET);
                }
                if (isUpgradeableDriverDependency(getCursor(), visited) &&
                    visited.getChildValue("version").map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withChildValue("version", TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (!isGradleDependencyInvocation(getCursor(), visited) || hasMapVariant(visited)) {
                    return visited;
                }
                String group = invocationMapValue(visited, "group");
                String name = invocationMapValue(visited, "name");
                String version = invocationMapValue(visited, "version");
                if (GROUP.equals(group) && ARTIFACT.equals(name) && SOURCE_VERSIONS.contains(version)) {
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
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, p);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, p);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag container) || !"dependencies".equals(container.getName())) {
            return false;
        }
        Cursor owner = dependencies.getParentTreeCursor();
        if (owner == null || !(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if (isProjectOwner(owner) || isProfileOwner(owner)) return true;
        if (!"dependencyManagement".equals(ownerTag.getName())) return false;
        Cursor managedOwner = owner.getParentTreeCursor();
        return managedOwner != null && (isProjectOwner(managedOwner) || isProfileOwner(managedOwner));
    }

    static boolean isDriverDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static boolean isUpgradeableDriverDependency(Cursor cursor, Xml.Tag tag) {
        return isDriverDependency(cursor, tag) && tag.getChild("classifier").isEmpty() && tag.getChild("type").isEmpty();
    }

    static boolean isRootProperty(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        if (!(properties.getValue() instanceof Xml.Tag propertiesTag) ||
            !"properties".equals(propertiesTag.getName())) return false;
        Cursor project = properties.getParentTreeCursor();
        Cursor document = project == null ? null : project.getParentTreeCursor();
        return project != null && project.getValue() instanceof Xml.Tag projectTag &&
               "project".equals(projectTag.getName()) && document != null &&
               document.getValue() instanceof Xml.Document;
    }

    static boolean isProjectOwner(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag) || !"project".equals(tag.getName())) return false;
        return cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    static boolean isProfileOwner(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag) || !"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        if (!(profiles.getValue() instanceof Xml.Tag profilesTag) ||
            !"profiles".equals(profilesTag.getName())) return false;
        return isProjectOwner(profiles.getParentTreeCursor());
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        boolean dependencies = false;
        for (Cursor ancestor = cursor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (!(ancestor.getValue() instanceof J.MethodInvocation owner)) continue;
            if (!dependencies) {
                if (!"dependencies".equals(owner.getSimpleName())) return false;
                dependencies = true;
                continue;
            }
            if (Set.of("buildscript", "pluginManagement", "plugins", "repositories", "publishing",
                    "dependencyResolutionManagement").contains(owner.getSimpleName())) return false;
        }
        return dependencies;
    }

    private static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
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

    private static String invocationMapValue(J.MethodInvocation invocation, String key) {
        String direct = invocation.getArguments().stream().filter(G.MapEntry.class::isInstance)
                .map(G.MapEntry.class::cast).filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
        if (direct != null) return direct;
        return invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                .flatMap(map -> map.getElements().stream()).filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static boolean hasMapVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && MAP_VARIANT_KEYS.contains(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && map.getElements().stream()
                        .anyMatch(entry -> MAP_VARIANT_KEYS.contains(mapKey(entry))));
    }

    private static boolean isUpgradeableMap(G.MapLiteral map) {
        return map.getElements().stream().noneMatch(entry -> MAP_VARIANT_KEYS.contains(mapKey(entry))) &&
               GROUP.equals(mapValue(map, "group")) && ARTIFACT.equals(mapValue(map, "name")) &&
               SOURCE_VERSIONS.contains(mapValue(map, "version"));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(UpgradeSelectedMssqlJdbcDependency::upgradeVersionEntry)
                .toList());
    }

    private static G.MapEntry upgradeVersionEntry(G.MapEntry entry) {
        return "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                ? entry.withValue(upgradeVersionLiteral(literal)) : entry;
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
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

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }

    static boolean isProjectPath(Path path) {
        for (Path segment : path) if (GENERATED_DIRECTORIES.contains(segment.toString())) return false;
        return true;
    }
}
