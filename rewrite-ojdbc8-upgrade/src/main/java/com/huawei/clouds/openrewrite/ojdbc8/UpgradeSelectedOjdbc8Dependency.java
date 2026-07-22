package com.huawei.clouds.openrewrite.ojdbc8;

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

/** Upgrade only workbook-selected ojdbc8 declarations and provably local owners. */
public final class UpgradeSelectedOjdbc8Dependency extends Recipe {
    static final String GROUP = "com.oracle.database.jdbc";
    static final String ARTIFACT = "ojdbc8";
    static final String BOM = "ojdbc-bom";
    static final String TARGET = "23.26.1.0.0";
    static final Set<String> SOURCE_VERSIONS = Set.of("19.19.0.0", "21.9.0.0", "23.2.0.0");
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
            "node_modules", "coverage", "vendor"
    );
    private static final Map<String, Set<String>> FAMILY = Map.of(
            "com.oracle.database.jdbc", Set.of(
                    "ojdbc8", "ojdbc10", "ojdbc11", "ojdbc17", "ojdbc-bom", "ojdbc8-production",
                    "ojdbc11-production", "ojdbc17-production", "ucp", "ucp11", "ucp17", "rsi"),
            "com.oracle.database.security", Set.of("oraclepki"),
            "com.oracle.database.ha", Set.of("ons", "simplefan"),
            "com.oracle.database.nls", Set.of("orai18n"),
            "com.oracle.database.xml", Set.of("xdb", "xmlparserv2", "xmlparserv2_sans_jaxp_services")
    );

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Oracle ojdbc8 declarations to 23.26.1.0.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only owned Maven or Gradle com.oracle.database.jdbc:ojdbc8 declarations at the three " +
               "visible workbook versions, plus provably local dependency-management/BOM/property owners.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !isProjectPath(source.getSourcePath()) ||
                    source.getSourcePath().getFileName() == null) return tree;
                String name = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(name)) return migratePom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && name.endsWith(".gradle")) return migrateGroovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && name.endsWith(".gradle.kts")) return migrateKotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        UUID rootScope = rootScopeId(document);
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Map<PropertyKey, Integer> allReferences = new HashMap<>();
        Map<PropertyKey, Integer> familyReferences = new HashMap<>();
        Map<PropertyKey, Integer> clientOwnerReferences = new HashMap<>();
        Set<UUID> scopesWithVersionlessClient = new HashSet<>();
        Map<UUID, UUID> bomScopes = new HashMap<>();

        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(mavenScope(getCursor()), visited.getName());
                    definitions.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext p) {
                collectReferences(charData.getText(), mavenScope(getCursor()), rootScope,
                        definitions, allReferences);
                return super.visitCharData(charData, p);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext p) {
                collectReferences(attribute.getValueAsString(), mavenScope(getCursor()), rootScope,
                        definitions, allReferences);
                return super.visitAttribute(attribute, p);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (!isProjectDependency(getCursor(), visited)) return visited;
                UUID scope = scopeId(getCursor());
                if (scope != null && isTargetDependency(getCursor(), visited) && isStandardJar(visited) &&
                    visited.getChild("version").isEmpty()) scopesWithVersionlessClient.add(scope);
                if (scope != null && isImportedOjdbcBom(visited)) bomScopes.put(visited.getId(), scope);
                if (isOracleFamilyDependency(visited)) {
                    propertyName(visited).flatMap(name -> effectivePropertyKey(scope, rootScope, name, definitions))
                            .ifPresent(key -> familyReferences.merge(key, 1, Integer::sum));
                    if (isTargetDependency(getCursor(), visited)) {
                        propertyName(visited).flatMap(name -> effectivePropertyKey(scope, rootScope, name, definitions))
                                .ifPresent(key -> clientOwnerReferences.merge(key, 1, Integer::sum));
                    }
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<UUID> upgradeableBomIds = new HashSet<>();
        for (UUID consumerScope : scopesWithVersionlessClient) {
            Set<UUID> applicable = new HashSet<>();
            bomScopes.forEach((bomId, ownerScope) -> {
                if (ownerScope.equals(consumerScope) || ownerScope.equals(rootScope)) applicable.add(bomId);
            });
            if (applicable.size() == 1) upgradeableBomIds.addAll(applicable);
        }
        bomScopes.forEach((bomId, scope) -> {
            if (!upgradeableBomIds.contains(bomId)) return;
            findDependency(document, bomId, ctx).flatMap(UpgradeSelectedOjdbc8Dependency::propertyName)
                    .flatMap(name -> effectivePropertyKey(scope, rootScope, name, definitions))
                    .ifPresent(key -> clientOwnerReferences.merge(key, 1, Integer::sum));
        });

        Set<PropertyKey> safeProperties = new HashSet<>();
        clientOwnerReferences.forEach((key, count) -> {
            if (count > 0 && SOURCE_VERSIONS.contains(values.get(key)) &&
                definitions.getOrDefault(key, 0) == 1 &&
                allReferences.getOrDefault(key, 0).equals(familyReferences.getOrDefault(key, 0))) {
                safeProperties.add(key);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (isMavenPropertyDefinition(getCursor(), visited) &&
                    safeProperties.contains(new PropertyKey(mavenScope(getCursor()), visited.getName())) &&
                    visited.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withValue(TARGET);
                }
                if (isTargetDependency(getCursor(), visited) && isStandardJar(visited) &&
                    visited.getChildValue("version").map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withChildValue("version", TARGET);
                }
                if (upgradeableBomIds.contains(visited.getId()) && isImportedOjdbcBom(visited) &&
                    visited.getChildValue("version").map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withChildValue("version", TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static Optional<Xml.Tag> findDependency(Xml.Document document, UUID id, ExecutionContext ctx) {
        Xml.Tag[] found = {null};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                if (tag.getId().equals(id)) {
                    found[0] = tag;
                    return tag;
                }
                return found[0] == null ? super.visitTag(tag, p) : tag;
            }
        }.visit(document, ctx);
        return Optional.ofNullable(found[0]);
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
        if (!(dependencies.getValue() instanceof Xml.Tag container) || !"dependencies".equals(container.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (owner == null || !(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if (isProjectOrProfile(owner)) return true;
        return "dependencyManagement".equals(ownerTag.getName()) && isProjectOrProfile(owner.getParentTreeCursor());
    }

    static boolean isDependencyManagement(Cursor cursor) {
        Cursor dependencies = cursor.getParentTreeCursor();
        Cursor owner = dependencies == null ? null : dependencies.getParentTreeCursor();
        return owner != null && owner.getValue() instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName());
    }

    static boolean isTargetDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean isStandardJar(Xml.Tag tag) {
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        boolean jar = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty())
                .map("jar"::equals).orElse(true);
        return noClassifier && jar;
    }

    static boolean isImportedOjdbcBom(Xml.Tag tag) {
        return GROUP.equals(tag.getChildValue("groupId").orElse(null)) && BOM.equals(tag.getChildValue("artifactId").orElse(null)) &&
               "pom".equals(tag.getChildValue("type").map(String::trim).orElse("")) &&
               "import".equals(tag.getChildValue("scope").map(String::trim).orElse("")) &&
               tag.getChildValue("classifier").map(String::trim).filter(value -> !value.isEmpty()).isEmpty();
    }

    static boolean isOracleFamilyCoordinate(String group, String artifact) {
        return group != null && artifact != null && FAMILY.getOrDefault(group, Set.of()).contains(artifact);
    }

    private static boolean isOracleFamilyDependency(Xml.Tag tag) {
        String group = tag.getChildValue("groupId").orElse("");
        String artifact = tag.getChildValue("artifactId").orElse("");
        if (!isOracleFamilyCoordinate(group, artifact) ||
            tag.getChildValue("classifier").map(String::trim).filter(value -> !value.isEmpty()).isPresent()) return false;
        if (BOM.equals(artifact)) return isImportedOjdbcBom(tag);
        String type = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty()).orElse("jar");
        return "jar".equals(type) || artifact.endsWith("-production") && "pom".equals(type);
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

    static UUID scopeId(Cursor cursor) {
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag dependenciesTag) ||
            !"dependencies".equals(dependenciesTag.getName())) return null;
        Cursor owner = dependencies.getParentTreeCursor();
        if (owner != null && owner.getValue() instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName())) {
            owner = owner.getParentTreeCursor();
        }
        return owner != null && isProjectOrProfile(owner) && owner.getValue() instanceof Xml.Tag tag ? tag.getId() : null;
    }

    static UUID rootScopeId(Xml.Document document) {
        return document.getRoot().getId();
    }

    static UUID mavenScope(Cursor cursor) {
        for (Cursor ancestor = cursor; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getValue() instanceof Xml.Tag tag &&
                ("profile".equals(tag.getName()) || "project".equals(tag.getName()))) {
                return tag.getId();
            }
        }
        return null;
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
        return parent.getValue() instanceof J.MethodInvocation invocation && isGradleDependencyInvocation(parent, invocation);
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").map(String::trim).orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static void collectReferences(String source, UUID scope, UUID rootScope,
                                          Map<PropertyKey, Integer> definitions,
                                          Map<PropertyKey, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(source);
        while (matcher.find()) {
            effectivePropertyKey(scope, rootScope, matcher.group(1), definitions)
                    .ifPresent(key -> references.merge(key, 1, Integer::sum));
        }
    }

    private static Optional<PropertyKey> effectivePropertyKey(UUID scope, UUID rootScope, String name,
                                                               Map<PropertyKey, Integer> definitions) {
        if (scope != null && !scope.equals(rootScope)) {
            PropertyKey local = new PropertyKey(scope, name);
            if (definitions.containsKey(local)) return Optional.of(local);
        }
        PropertyKey root = new PropertyKey(rootScope, name);
        return definitions.containsKey(root) ? Optional.of(root) : Optional.empty();
    }

    private record PropertyKey(UUID scope, String name) {
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
        return map.withElements(map.getElements().stream().map(UpgradeSelectedOjdbc8Dependency::upgradeVersionEntry).toList());
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
        return literal.withValue(replacement).withValueSource(source == null ? null : source.replace(oldValue, replacement));
    }

    static boolean isProjectPath(Path path) {
        for (Path segment : path.normalize()) {
            String lower = segment.toString().toLowerCase(java.util.Locale.ROOT);
            if (GENERATED_DIRECTORIES.contains(lower) || lower.startsWith("generated") ||
                lower.startsWith("install")) return false;
        }
        return true;
    }
}
