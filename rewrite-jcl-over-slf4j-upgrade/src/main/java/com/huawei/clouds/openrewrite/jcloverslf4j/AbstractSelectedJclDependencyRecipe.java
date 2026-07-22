package com.huawei.clouds.openrewrite.jcloverslf4j;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared strict Maven and Gradle implementation for JCL-over-SLF4J recipes. */
abstract class AbstractSelectedJclDependencyRecipe extends Recipe {
    static final Set<String> SOURCE_VERSIONS = Set.of("1.7.30", "1.7.32", "1.7.36");
    static final String TARGET = "2.0.17";
    static final String GROUP = "org.slf4j";
    static final String CORE = "jcl-over-slf4j";
    private static final String CONFLICT = "<conflict>";
    static final Set<String> COMPANIONS = Set.of(
            "slf4j-api", "slf4j-simple", "slf4j-nop", "slf4j-jdk14", "slf4j-reload4j",
            "jul-to-slf4j", "log4j-over-slf4j");
    static final Set<String> FAMILY;
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp");
    private static final Set<String> EXCLUDED = Set.of(
            "target", "build", "out", "dist", "generated", "install", "vendor", ".gradle", ".idea",
            ".mvn", ".m2", ".yarn", ".cache", "node_modules", "coverage");

    static {
        Set<String> family = new HashSet<>(COMPANIONS);
        family.add(CORE);
        FAMILY = Set.copyOf(family);
    }

    enum Mode {
        STRICT_CORE,
        FAMILY_FROM_SOURCE,
        COMPANIONS_FOR_TARGET
    }

    private final Mode mode;

    protected AbstractSelectedJclDependencyRecipe(Mode mode) {
        this.mode = mode;
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
                if (tree instanceof G.CompilationUnit unit && name.endsWith(".gradle") && hasGroovyGate(unit, ctx)) {
                    return migrateGroovy(unit, ctx);
                }
                if (tree instanceof K.CompilationUnit unit && name.endsWith(".gradle.kts") && hasKotlinGate(unit, ctx)) {
                    return migrateKotlin(unit, ctx);
                }
                return tree;
            }
        };
    }

    private Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        PropertyIndex propertyIndex = propertyIndex(document, ctx);
        Set<String> gateScopes = mavenGateScopes(document, propertyIndex, ctx);
        if (gateScopes.isEmpty()) return document;
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> candidateReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                collectReferences(charData.getText(), mavenScope(getCursor()), propertyIndex, allReferences);
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                collectReferences(attribute.getValueAsString(), mavenScope(getCursor()), propertyIndex, allReferences);
                return super.visitAttribute(attribute, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isCandidateDependency(getCursor(), tag) && scopeEligible(mavenScope(getCursor()), gateScopes)) {
                    String scope = mavenScope(getCursor());
                    propertyName(tag).map(name -> effectivePropertyKey(propertyIndex, scope, name))
                            .ifPresent(key -> candidateReferences.merge(key, 1, Integer::sum));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        Set<String> ownedProperties = ownedProperties(allReferences, candidateReferences, propertyIndex.definitions());
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (isProjectOrProfileProperty(getCursor(), visited) &&
                    ownedProperties.contains(propertyKey(mavenScope(getCursor()), visited.getName())) &&
                    SOURCE_VERSIONS.contains(visited.getValue().map(String::trim).orElse(""))) {
                    return visited.withValue(TARGET);
                }
                if (isCandidateDependency(getCursor(), visited) && !hasMavenVariant(visited) &&
                    scopeEligible(mavenScope(getCursor()), gateScopes) &&
                    visited.getChildValue("version").map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withChildValue("version", TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private Set<String> mavenGateScopes(Xml.Document document, PropertyIndex properties, ExecutionContext ctx) {
        Set<String> scopes = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isCoreDependency(getCursor(), tag) && !hasMavenVariant(tag)) {
                    String declared = tag.getChildValue("version").map(String::trim).orElse("");
                    String scope = mavenScope(getCursor());
                    String resolved = propertyName(tag).map(name -> propertyValue(properties, scope, name))
                            .orElse(declared);
                    if (eligibleGateVersion(resolved)) scopes.add(mavenScope(getCursor()));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return scopes;
    }

    private boolean scopeEligible(String scope, Set<String> gateScopes) {
        if (mode == Mode.STRICT_CORE) return true;
        return gateScopes.contains(scope) || gateScopes.contains("root");
    }

    private PropertyIndex propertyIndex(Xml.Document document, ExecutionContext ctx) {
        Map<String, Map<String, String>> values = new HashMap<>();
        Map<String, Integer> definitions = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (isProjectOrProfileProperty(getCursor(), visited)) {
                    String scope = mavenScope(getCursor());
                    definitions.merge(propertyKey(scope, visited.getName()), 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.computeIfAbsent(scope, ignored -> new HashMap<>())
                            .merge(visited.getName(), value.trim(),
                                    (left, right) -> left.equals(right) ? left : CONFLICT));
                }
                return visited;
            }
        }.visit(document, ctx);
        return new PropertyIndex(values, definitions);
    }

    private boolean hasGroovyGate(G.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] gate = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (isGradleDependencyInvocation(getCursor(), method) && coreMapVersion(method)
                        .filter(AbstractSelectedJclDependencyRecipe.this::eligibleGateVersion).isPresent()) {
                    gate[0] = true;
                }
                return gate[0] ? method : super.visitMethodInvocation(method, executionContext);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                if (isDirectGradleDependencyLiteral(getCursor()) && coordinate(literal, CORE)
                        .filter(AbstractSelectedJclDependencyRecipe.this::eligibleGateVersion).isPresent()) {
                    gate[0] = true;
                }
                return literal;
            }
        }.visit(unit, ctx);
        return gate[0];
    }

    private boolean hasKotlinGate(K.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] gate = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                if (isDirectGradleDependencyLiteral(getCursor()) && coordinate(literal, CORE)
                        .filter(AbstractSelectedJclDependencyRecipe.this::eligibleGateVersion).isPresent()) {
                    gate[0] = true;
                }
                return literal;
            }
        }.visit(unit, ctx);
        return gate[0];
    }

    private G.CompilationUnit migrateGroovy(G.CompilationUnit unit, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (!isGradleDependencyInvocation(getCursor(), visited) || hasGradleVariant(visited)) return visited;
                String artifact = invocationMapValue(visited, "name");
                String version = invocationMapValue(visited, "version");
                if (GROUP.equals(invocationMapValue(visited, "group")) && targetArtifacts().contains(artifact) &&
                    SOURCE_VERSIONS.contains(version)) {
                    return visited.withArguments(visited.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry ? upgradeVersion(entry) : argument).toList());
                }
                return visited.withArguments(visited.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map && eligibleMap(map) ? upgradeMap(map) : argument).toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(unit, ctx);
    }

    private K.CompilationUnit migrateKotlin(K.CompilationUnit unit, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(unit, ctx);
    }

    private boolean eligibleGateVersion(String version) {
        return mode == Mode.COMPANIONS_FOR_TARGET ? TARGET.equals(version) : SOURCE_VERSIONS.contains(version);
    }

    private Set<String> targetArtifacts() {
        return switch (mode) {
            case STRICT_CORE -> Set.of(CORE);
            case FAMILY_FROM_SOURCE -> FAMILY;
            case COMPANIONS_FOR_TARGET -> COMPANIONS;
        };
    }

    private boolean isCandidateDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && !hasMavenVariant(tag) &&
               GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               tag.getChildValue("artifactId").filter(targetArtifacts()::contains).isPresent();
    }

    private static boolean isCoreDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               CORE.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private Optional<String> coreMapVersion(J.MethodInvocation method) {
        if (hasGradleVariant(method)) return Optional.empty();
        if (GROUP.equals(invocationMapValue(method, "group")) && CORE.equals(invocationMapValue(method, "name"))) {
            return Optional.ofNullable(invocationMapValue(method, "version"));
        }
        return method.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                .filter(map -> !hasGradleVariant(map) && GROUP.equals(mapValue(map, "group")) &&
                               CORE.equals(mapValue(map, "name")))
                .map(map -> mapValue(map, "version")).filter(java.util.Objects::nonNull).findFirst();
    }

    private J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !GROUP.equals(parts[0]) || !targetArtifacts().contains(parts[1]) ||
            !SOURCE_VERSIONS.contains(parts[2])) return literal;
        return replaceLiteral(literal, value, GROUP + ":" + parts[1] + ":" + TARGET);
    }

    private static Optional<String> coordinate(J.Literal literal, String artifact) {
        if (!(literal.getValue() instanceof String value)) return Optional.empty();
        String[] parts = value.split(":", -1);
        return parts.length == 3 && GROUP.equals(parts[0]) && artifact.equals(parts[1])
                ? Optional.of(parts[2]) : Optional.empty();
    }

    private boolean eligibleMap(G.MapLiteral map) {
        return !hasGradleVariant(map) && GROUP.equals(mapValue(map, "group")) &&
               targetArtifacts().contains(mapValue(map, "name")) &&
               SOURCE_VERSIONS.contains(mapValue(map, "version"));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(AbstractSelectedJclDependencyRecipe::upgradeVersion).toList());
    }

    private static G.MapEntry upgradeVersion(G.MapEntry entry) {
        if (!"version".equals(mapKey(entry)) || !(entry.getValue() instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String value) || !SOURCE_VERSIONS.contains(value)) return entry;
        return entry.withValue(replaceLiteral(literal, value, TARGET));
    }

    static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").map(String::trim).orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static Set<String> ownedProperties(Map<String, Integer> all, Map<String, Integer> candidates,
                                               Map<String, Integer> definitions) {
        Set<String> owned = new HashSet<>();
        candidates.forEach((name, count) -> {
            if (count.equals(all.get(name)) && definitions.getOrDefault(name, 0) == 1) owned.add(name);
        });
        return owned;
    }

    private static void collectReferences(String value, String scope, PropertyIndex properties,
                                          Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(value);
        while (matcher.find()) {
            String key = effectivePropertyKey(properties, scope, matcher.group(1));
            if (key != null) references.merge(key, 1, Integer::sum);
        }
    }

    private static String propertyValue(PropertyIndex properties, String scope, String name) {
        String key = effectivePropertyKey(properties, scope, name);
        if (key == null || properties.definitions().getOrDefault(key, 0) != 1) return null;
        String ownerScope = key.substring(0, key.indexOf('|'));
        String value = properties.values().getOrDefault(ownerScope, Map.of()).get(name);
        return CONFLICT.equals(value) ? null : value;
    }

    private static String effectivePropertyKey(PropertyIndex properties, String scope, String name) {
        if (!"root".equals(scope) && properties.values().getOrDefault(scope, Map.of()).containsKey(name)) {
            return propertyKey(scope, name);
        }
        return properties.values().getOrDefault("root", Map.of()).containsKey(name)
                ? propertyKey("root", name) : null;
    }

    private static String propertyKey(String scope, String name) {
        return scope + "|" + name;
    }

    private record PropertyIndex(Map<String, Map<String, String>> values, Map<String, Integer> definitions) {
    }

    static boolean isProjectOrProfileProperty(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag properties) || !"properties".equals(properties.getName())) return false;
        Cursor owner = parent.getParentTreeCursor();
        return !"properties".equals(tag.getName()) && owner != null && owner.getValue() instanceof Xml.Tag ownerTag &&
               ("project".equals(ownerTag.getName()) || "profile".equals(ownerTag.getName()));
    }

    static String mavenScope(Cursor cursor) {
        for (Cursor ancestor = cursor; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return "profile:" + tag.getId();
            }
        }
        return "root";
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag container) || !"dependencies".equals(container.getName())) {
            return false;
        }
        Cursor owner = dependencies.getParentTreeCursor();
        if (owner == null || !(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if ("project".equals(ownerTag.getName()) || "profile".equals(ownerTag.getName())) return true;
        if (!"dependencyManagement".equals(ownerTag.getName())) return false;
        Cursor managedOwner = owner.getParentTreeCursor();
        return managedOwner != null && managedOwner.getValue() instanceof Xml.Tag managedOwnerTag &&
               ("project".equals(managedOwnerTag.getName()) || "profile".equals(managedOwnerTag.getName()));
    }

    static boolean isActiveProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!isProjectDependency(cursor, tag)) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        Cursor owner = dependencies == null ? null : dependencies.getParentTreeCursor();
        return owner != null && (!(owner.getValue() instanceof Xml.Tag ownerTag) ||
                                 !"dependencyManagement".equals(ownerTag.getName()));
    }

    static boolean hasMavenVariant(Xml.Tag dependency) {
        if (dependency.getChildValue("classifier").map(String::trim).filter(value -> !value.isEmpty()).isPresent()) {
            return true;
        }
        return dependency.getChildValue("type").map(String::trim)
                .filter(value -> !value.isEmpty() && !"jar".equals(value)).isPresent();
    }

    static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        boolean dependencies = false;
        for (Cursor ancestor = cursor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (!(ancestor.getValue() instanceof J.MethodInvocation owner)) continue;
            if (!dependencies) {
                if (!"dependencies".equals(owner.getSimpleName())) return false;
                dependencies = true;
            } else {
                return false;
            }
        }
        return dependencies;
    }

    static boolean hasGradleVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && isVariantKey(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && hasGradleVariant(map));
    }

    static boolean hasGradleVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> isVariantKey(mapKey(entry)));
    }

    private static boolean isVariantKey(String key) {
        return "classifier".equals(key) || "ext".equals(key) || "type".equals(key);
    }

    static String invocationMapValue(J.MethodInvocation invocation, String key) {
        String direct = invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
        if (direct != null) return direct;
        return invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                .map(map -> mapValue(map, key)).filter(java.util.Objects::nonNull).findFirst().orElse(null);
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

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }

    static boolean isProjectPath(Path path) {
        for (Path segment : path) {
            String lower = segment.toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(lower) || lower.startsWith("generated") || lower.startsWith("install")) return false;
        }
        return true;
    }
}
