package com.huawei.clouds.openrewrite.okhttp;

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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strictly upgrades only owned OkHttp declarations selected by the workbook. */
public final class UpgradeSelectedOkHttpDependency extends Recipe {
    static final String GROUP = "com.squareup.okhttp3";
    static final Set<String> SELECTED_ARTIFACTS = Set.of("okhttp", "okhttp-bom", "okhttp-jvm");
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "3.14.4", "3.14.9", "4.8.0", "4.9.1", "4.9.2",
            "4.9.3", "4.10.0", "4.11.0", "5.0.0-alpha.11"
    );
    static final String TARGET = "5.3.0";

    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime",
            "runtimeOnly", "annotationProcessor", "testCompile", "testCompileOnly",
            "testImplementation", "testRuntime", "testRuntimeOnly", "testFixturesApi",
            "testFixturesImplementation", "testFixturesRuntimeOnly", "kapt", "ksp"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".m2",
            ".idea", "node_modules", "vendor"
    );
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected OkHttp declarations to 5.3.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only owned Maven and Gradle okhttp, okhttp-jvm, and okhttp-bom declarations whose " +
               "versions are exact workbook sources, without widening version selection or changing variants.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migrateMaven(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {
                            boolean owned = isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                            if (!owned || hasVariant(visited)) return visited;
                            String artifact = mapValue(visited, "name");
                            String version = mapValue(visited, "version");
                            if (!GROUP.equals(mapValue(visited, "group")) || !isDirectArtifact(artifact) ||
                                !SOURCE_VERSIONS.contains(version)) return visited;
                            return visited.withArguments(visited.getArguments().stream().map(argument ->
                                    argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                    entry.getValue() instanceof J.Literal literal
                                            ? entry.withValue(replaceVersionLiteral(literal)) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            CoordinateOwner owner = coordinateOwner(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return owner == CoordinateOwner.NONE ? visited : upgradeCoordinate(visited, owner);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            CoordinateOwner owner = coordinateOwner(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return owner == CoordinateOwner.NONE ? visited : upgradeCoordinate(visited, owner);
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migrateMaven(Xml.Document document, ExecutionContext ctx) {
        Map<String, Integer> definitions = new HashMap<>();
        Map<String, String> propertyValues = new HashMap<>();
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> familyReferences = new HashMap<>();
        Map<String, Integer> selectedReferences = new HashMap<>();
        Map<String, UUID> definitionOwners = new HashMap<>();
        Map<String, Set<UUID>> familyOwners = new HashMap<>();
        UUID rootId = document.getRoot().getId();

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
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (isPropertyDefinition(getCursor(), visited)) {
                    definitions.merge(visited.getName(), 1, Integer::sum);
                    propertyOwner(getCursor()).ifPresent(owner -> definitionOwners.put(visited.getName(), owner.getId()));
                    visited.getValue().ifPresent(value -> propertyValues.put(visited.getName(), value.trim()));
                }
                if (isStandardOkHttpFamilyDependency(getCursor(), visited)) {
                    propertyName(visited).ifPresent(name -> {
                        familyReferences.merge(name, 1, Integer::sum);
                        dependencyOwner(getCursor()).ifPresent(owner ->
                                familyOwners.computeIfAbsent(name, ignored -> new HashSet<>()).add(owner.getId()));
                        if (SELECTED_ARTIFACTS.contains(visited.getChildValue("artifactId").orElse(""))) {
                            selectedReferences.merge(name, 1, Integer::sum);
                        }
                    });
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        selectedReferences.forEach((name, count) -> {
            UUID definitionOwner = definitionOwners.get(name);
            Set<UUID> referenceOwners = familyOwners.getOrDefault(name, Set.of());
            boolean validScope = definitionOwner != null &&
                    (definitionOwner.equals(rootId) || referenceOwners.stream().allMatch(definitionOwner::equals));
            if (count > 0 && SOURCE_VERSIONS.contains(propertyValues.get(name)) &&
                definitions.getOrDefault(name, 0) == 1 &&
                validScope &&
                allReferences.getOrDefault(name, 0).equals(familyReferences.getOrDefault(name, 0))) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (isPropertyDefinition(getCursor(), visited) && safeProperties.contains(visited.getName()) &&
                    propertyOwner(getCursor()).map(Xml.Tag::getId).filter(definitionOwners.get(visited.getName())::equals).isPresent() &&
                    visited.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withValue(TARGET);
                }
                if (isSelectedMavenDependency(getCursor(), visited) &&
                    visited.getChildValue("version").map(String::trim)
                            .filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withChildValue("version", TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    static boolean isSelectedMavenDependency(Cursor cursor, Xml.Tag dependency) {
        return isStandardOkHttpFamilyDependency(cursor, dependency) &&
               SELECTED_ARTIFACTS.contains(dependency.getChildValue("artifactId").orElse(""));
    }

    static boolean isStandardOkHttpFamilyDependency(Cursor cursor, Xml.Tag dependency) {
        if (!isProjectDependency(cursor, dependency) ||
            !GROUP.equals(dependency.getChildValue("groupId").orElse(null)) ||
            dependency.getChild("classifier").isPresent()) return false;
        String artifact = dependency.getChildValue("artifactId").orElse("");
        String type = dependency.getChildValue("type").orElse("jar");
        if ("okhttp-bom".equals(artifact)) {
            return "pom".equals(type) && "import".equals(dependency.getChildValue("scope").orElse("")) &&
                   isInsideDependencyManagement(cursor);
        }
        return "jar".equals(type);
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependenciesCursor = cursor.getParentTreeCursor();
        if (!(dependenciesCursor.getValue() instanceof Xml.Tag dependencies) ||
            !"dependencies".equals(dependencies.getName())) return false;
        Cursor ownerCursor = dependenciesCursor.getParentTreeCursor();
        if (!(ownerCursor.getValue() instanceof Xml.Tag owner)) return false;
        if (isProjectOrProfile(ownerCursor)) return true;
        if (!"dependencyManagement".equals(owner.getName())) return false;
        Cursor managedOwner = ownerCursor.getParentTreeCursor();
        return isProjectOrProfile(managedOwner);
    }

    private static boolean isInsideDependencyManagement(Cursor cursor) {
        Cursor dependencies = cursor.getParentTreeCursor();
        Cursor owner = dependencies.getParentTreeCursor();
        return owner.getValue() instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName());
    }

    static boolean isPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor propertiesCursor = cursor.getParentTreeCursor();
        if (!(propertiesCursor.getValue() instanceof Xml.Tag properties) ||
            !"properties".equals(properties.getName()) || "properties".equals(tag.getName())) return false;
        Cursor ownerCursor = propertiesCursor.getParentTreeCursor();
        return isProjectOrProfile(ownerCursor);
    }

    static boolean isProjectOrProfile(Cursor cursor) {
        if (isRootProject(cursor)) return true;
        if (cursor == null || !(cursor.getValue() instanceof Xml.Tag profile) ||
            !"profile".equals(profile.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles != null && profiles.getValue() instanceof Xml.Tag profilesTag &&
               "profiles".equals(profilesTag.getName()) && isRootProject(profiles.getParentTreeCursor());
    }

    static Optional<Xml.Tag> dependencyOwner(Cursor dependencyCursor) {
        Cursor dependencies = dependencyCursor.getParentTreeCursor();
        Cursor owner = dependencies.getParentTreeCursor();
        if (isProjectOrProfile(owner) && owner.getValue() instanceof Xml.Tag tag) return Optional.of(tag);
        if (owner.getValue() instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName())) {
            Cursor scope = owner.getParentTreeCursor();
            if (isProjectOrProfile(scope) && scope.getValue() instanceof Xml.Tag scoped) return Optional.of(scoped);
        }
        return Optional.empty();
    }

    static Optional<Xml.Tag> propertyOwner(Cursor propertyCursor) {
        Cursor properties = propertyCursor.getParentTreeCursor();
        Cursor owner = properties.getParentTreeCursor();
        return isProjectOrProfile(owner) && owner.getValue() instanceof Xml.Tag tag
                ? Optional.of(tag) : Optional.empty();
    }

    static boolean isRootProject(Cursor cursor) {
        if (cursor == null || !(cursor.getValue() instanceof Xml.Tag project) ||
            !"project".equals(project.getName())) return false;
        Cursor document = cursor.getParentTreeCursor();
        return document != null && document.getValue() instanceof Xml.Document;
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        Cursor dependencies = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if ("dependencies".equals(ancestor.getSimpleName()) && ancestor.getSelect() == null) {
                    dependencies = current;
                }
                break;
            }
        }
        if (dependencies == null) return false;
        for (Cursor current = dependencies.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation) return false;
        }
        return true;
    }

    static boolean isOwnedGradleCoordinateLiteral(Cursor literalCursor) {
        return coordinateOwner(literalCursor) != CoordinateOwner.NONE;
    }

    static boolean isGradlePlatformCoordinateLiteral(Cursor literalCursor) {
        return coordinateOwner(literalCursor) == CoordinateOwner.PLATFORM;
    }

    private static CoordinateOwner coordinateOwner(Cursor literalCursor) {
        Cursor parent = literalCursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof J.MethodInvocation invocation)) return CoordinateOwner.NONE;
        if (isGradleDependencyInvocation(parent, invocation)) return CoordinateOwner.DIRECT;
        if (!("platform".equals(invocation.getSimpleName()) ||
              "enforcedPlatform".equals(invocation.getSimpleName()))) return CoordinateOwner.NONE;
        Cursor configurationCursor = parent.getParent();
        while (configurationCursor != null && !(configurationCursor.getValue() instanceof J.MethodInvocation)) {
            configurationCursor = configurationCursor.getParent();
        }
        if (configurationCursor == null) return CoordinateOwner.NONE;
        J.MethodInvocation configuration = configurationCursor.getValue();
        return isGradleDependencyInvocation(configurationCursor, configuration)
                ? CoordinateOwner.PLATFORM : CoordinateOwner.NONE;
    }

    private static J.Literal upgradeCoordinate(J.Literal literal, CoordinateOwner owner) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] coordinate = value.split(":", -1);
        if (coordinate.length != 3 || !GROUP.equals(coordinate[0]) ||
            !SOURCE_VERSIONS.contains(coordinate[2])) return literal;
        String artifact = coordinate[1];
        if ((owner == CoordinateOwner.DIRECT && !isDirectArtifact(artifact)) ||
            (owner == CoordinateOwner.PLATFORM && !"okhttp-bom".equals(artifact))) return literal;
        return replaceLiteral(literal, value, GROUP + ":" + artifact + ":" + TARGET);
    }

    private static boolean isDirectArtifact(String artifact) {
        return "okhttp".equals(artifact) || "okhttp-jvm".equals(artifact);
    }

    private static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static J.Literal replaceVersionLiteral(J.Literal literal) {
        return literal.getValue() instanceof String version && SOURCE_VERSIONS.contains(version)
                ? replaceLiteral(literal, version, TARGET) : literal;
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(
                source == null ? null : source.replace(oldValue, newValue));
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) references.merge(matcher.group(1), 1, Integer::sum);
    }

    static boolean generated(Path path) {
        for (Path part : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(part.toString())) return true;
        }
        return false;
    }

    private enum CoordinateOwner { NONE, DIRECT, PLATFORM }
}
