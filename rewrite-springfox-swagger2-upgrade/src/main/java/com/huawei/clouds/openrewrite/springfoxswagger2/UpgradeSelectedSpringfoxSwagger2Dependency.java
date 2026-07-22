package com.huawei.clouds.openrewrite.springfoxswagger2;

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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Apply the workbook value literally, without pretending that its absent target artifact is resolvable. */
public final class UpgradeSelectedSpringfoxSwagger2Dependency extends Recipe {
    static final String GROUP = "io.springfox";
    static final String ARTIFACT = "springfox-swagger2";
    static final Set<String> SOURCE_VERSIONS = Set.of("3.0.0", "2.10.5", "2.6.1", "2.7.0", "2.9.2", "1.0.1");
    static final String TARGET = "1.1.2";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly", "kapt", "ksp"
    );
    private static final Set<String> EXCLUDED = Set.of(
            "target", "build", "out", "dist", "generated", "install", "vendor", ".gradle", ".mvn", ".m2",
            ".idea", ".git", "node_modules", "bower_components", ".pnpm", ".yarn", ".npm", ".angular",
            ".nx", ".next", ".cache", "coverage"
    );

    @Override
    public String getDisplayName() {
        return "Apply the workbook-selected Springfox Swagger 2 target version";
    }

    @Override
    public String getDescription() {
        return "Change only direct standard io.springfox:springfox-swagger2 declarations at the six visible workbook " +
               "source versions to the literal workbook target 1.1.2; the recommended recipe separately marks that " +
               "this target is absent from Maven Central and cannot form a buildable migration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || excluded(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && "pom.xml".equals(fileName)) return migratePom(xml, ctx);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                            if (!direct) return visited;
                            if (GROUP.equals(invocationMapValue(visited, "group")) &&
                                ARTIFACT.equals(invocationMapValue(visited, "name")) &&
                                SOURCE_VERSIONS.contains(invocationMapValue(visited, "version")) && !hasVariant(visited)) {
                                return visited.withArguments(visited.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(upgradeVersion(literal)) : argument).toList());
                            }
                            return visited.withArguments(visited.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, ec);
                            return direct ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, ec);
                            return direct ? upgradeCoordinate(visited) : visited;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        UUID rootScope = document.getRoot().getId();
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Map<PropertyKey, Integer> allRefs = new HashMap<>();
        Map<PropertyKey, Integer> bridgeRefs = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (isPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(mavenScope(getCursor()), visited.getName());
                    definitions.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData data, ExecutionContext ec) {
                collectPropertyReferences(data.getText(), mavenScope(getCursor()), rootScope, definitions, allRefs);
                return super.visitCharData(data, ec);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                collectPropertyReferences(attribute.getValueAsString(), mavenScope(getCursor()), rootScope,
                        definitions, allRefs);
                return super.visitAttribute(attribute, ec);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (isStandardDirectDependency(getCursor(), visited)) {
                    UUID scope = mavenScope(getCursor());
                    propertyName(visited).flatMap(name -> effectivePropertyKey(scope, rootScope, name, definitions))
                            .ifPresent(key -> bridgeRefs.merge(key, 1, Integer::sum));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        Set<PropertyKey> safe = new HashSet<>();
        bridgeRefs.forEach((key, count) -> {
            if (count > 0 && SOURCE_VERSIONS.contains(values.get(key)) && definitions.getOrDefault(key, 0) == 1 &&
                allRefs.getOrDefault(key, 0).equals(count)) safe.add(key);
        });
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (isPropertyDefinition(getCursor(), visited) &&
                    safe.contains(new PropertyKey(mavenScope(getCursor()), visited.getName())) &&
                    visited.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withValue(TARGET);
                }
                if (isStandardDirectDependency(getCursor(), visited) && visited.getChildValue("version").map(String::trim)
                        .filter(SOURCE_VERSIONS::contains).isPresent()) return visited.withChildValue("version", TARGET);
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    static boolean isStandardDirectDependency(Cursor cursor, Xml.Tag tag) {
        return isDirectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null)) && tag.getChild("classifier").isEmpty() &&
               "jar".equals(tag.getChildValue("type").orElse("jar"));
    }

    static boolean isDirectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag parent) || !"dependencies".equals(parent.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        return isProject(owner) || isProfile(owner);
    }

    static boolean isOwnedDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag parent) || !"dependencies".equals(parent.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (isProject(owner) || isProfile(owner)) return true;
        return owner.getValue() instanceof Xml.Tag management && "dependencyManagement".equals(management.getName()) &&
               (isProject(owner.getParentTreeCursor()) || isProfile(owner.getParentTreeCursor()));
    }

    static boolean isPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag properties) || !"properties".equals(properties.getName()) ||
            "properties".equals(tag.getName())) return false;
        Cursor owner = parent.getParentTreeCursor();
        return isProject(owner) || isProfile(owner);
    }

    private static boolean isProject(Cursor cursor) {
        return cursor.getValue() instanceof Xml.Tag tag && "project".equals(tag.getName()) &&
               cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static boolean isProfile(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag) || !"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag p && "profiles".equals(p.getName()) &&
               isProject(profiles.getParentTreeCursor());
    }

    static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation && isGradleDependencyInvocation(parent, invocation);
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        boolean dependencies = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if (!dependencies) {
                    if (!"dependencies".equals(ancestor.getSimpleName())) return false;
                    dependencies = true;
                } else return false;
            }
        }
        return dependencies;
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static void collectPropertyReferences(String text, UUID scope, UUID rootScope,
                                                  Map<PropertyKey, Integer> definitions,
                                                  Map<PropertyKey, Integer> refs) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            effectivePropertyKey(scope, rootScope, matcher.group(1), definitions)
                    .ifPresent(key -> refs.merge(key, 1, Integer::sum));
        }
    }

    private static UUID mavenScope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (current.getValue() instanceof Xml.Tag tag &&
                ("profile".equals(tag.getName()) || "project".equals(tag.getName()))) return tag.getId();
        }
        return null;
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

    private static String invocationMapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!GROUP.equals(mapValue(map, "group")) || !ARTIFACT.equals(mapValue(map, "name")) ||
            !SOURCE_VERSIONS.contains(mapValue(map, "version")) || hasVariant(map)) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(upgradeVersion(literal)) : entry).toList());
    }

    static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type", "variant").contains(mapKey(entry)));
    }

    static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry ->
                Set.of("classifier", "ext", "type", "variant").contains(mapKey(entry)));
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX) ||
            !SOURCE_VERSIONS.contains(value.substring(PREFIX.length()))) return literal;
        return replace(literal, value, PREFIX + TARGET);
    }

    private static J.Literal upgradeVersion(J.Literal literal) {
        return literal.getValue() instanceof String value && SOURCE_VERSIONS.contains(value)
                ? replace(literal, value, TARGET) : literal;
    }

    private static J.Literal replace(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }

    static boolean excluded(Path path) {
        Path normalized = path.normalize();
        for (int i = 0; i < Math.max(0, normalized.getNameCount() - 1); i++) {
            Path part = normalized.getName(i);
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(value) || value.startsWith("generated") || value.startsWith("install")) return true;
        }
        return false;
    }
}
