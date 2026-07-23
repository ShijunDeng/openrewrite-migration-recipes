package com.huawei.clouds.openrewrite.springsecuritycore;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SpringSecurityCoreSupport {
    static final String GROUP = "org.springframework.security";
    static final String ARTIFACT = "spring-security-core";
    static final String TARGET = "6.5.11";
    static final String TARGET_CONFLICT = "目标版本冲突（禁止降级）";
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "5.3.10.RELEASE", "5.7.1", "5.8.5",
            "6.4.4", "6.4.6", "6.4.10", "6.5.1");

    static final Pattern FIXED_VERSION =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[.-][A-Za-z0-9][A-Za-z0-9.-]*)?$");
    static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp");
    private static final Set<String> VARIANT_KEYS = Set.of("classifier", "ext", "type", "variant");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".m2",
            ".idea", "node_modules", "vendor", ".cache", "coverage", "reports", "test-results", "tmp", "temp");

    private SpringSecurityCoreSupport() {
    }

    static boolean selected(SourceFile source) {
        return source.getMarkers().findFirst(SpringSecurityCoreProjectMarker.class).isPresent();
    }

    static boolean generated(Path sourcePath) {
        Path parent = sourcePath.normalize().getParent();
        if (parent == null) return false;
        for (Path part : parent) {
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (GENERATED_DIRECTORIES.contains(value) || value.startsWith("generated") ||
                value.startsWith("install")) return true;
        }
        return false;
    }

    static boolean targetConflict(String version) {
        return versionGreaterThan(version, TARGET);
    }

    static boolean versionGreaterThan(String version, String baseline) {
        BigInteger[] candidate = numericVersion(version);
        BigInteger[] target = numericVersion(baseline);
        if (candidate == null || target == null) return false;
        for (int i = 0; i < candidate.length; i++) {
            int order = candidate[i].compareTo(target[i]);
            if (order != 0) return order > 0;
        }
        return false;
    }

    private static BigInteger[] numericVersion(String version) {
        if (version == null) return null;
        Matcher matcher = FIXED_VERSION.matcher(version);
        if (!matcher.matches()) return null;
        return new BigInteger[]{
                new BigInteger(matcher.group(1)),
                new BigInteger(matcher.group(2)),
                new BigInteger(matcher.group(3))
        };
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag container) ||
            !"dependencies".equals(container.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (projectOrProfile(owner)) return true;
        if (!(owner.getValue() instanceof Xml.Tag management) ||
            !"dependencyManagement".equals(management.getName())) return false;
        return projectOrProfile(owner.getParentTreeCursor());
    }

    static boolean isTargetDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) &&
               GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean standardJar(Xml.Tag tag) {
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        boolean jar = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty())
                .map("jar"::equals).orElse(true);
        return noClassifier && jar;
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        return properties.getValue() instanceof Xml.Tag container &&
               "properties".equals(container.getName()) &&
               !"properties".equals(tag.getName()) &&
               projectOrProfile(properties.getParentTreeCursor());
    }

    private static boolean projectOrProfile(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag)) return false;
        if ("project".equals(tag.getName())) {
            return cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
        }
        if (!"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag container && "profiles".equals(container.getName()) &&
               profiles.getParentTreeCursor().getValue() instanceof Xml.Tag project &&
               "project".equals(project.getName());
    }

    static boolean isRootGradleDependency(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName()) || invocation.getSelect() != null) return false;
        boolean dependencies = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if (!dependencies) {
                    if (!"dependencies".equals(ancestor.getSimpleName()) || ancestor.getSelect() != null) return false;
                    dependencies = true;
                } else {
                    return false;
                }
            }
        }
        return dependencies;
    }

    static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isRootGradleDependency(parent, invocation);
    }

    static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static String assignmentKey(J.Assignment assignment) {
        return assignment.getVariable() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static String mapValue(J.MethodInvocation invocation, String key) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Assignment assignment && key.equals(assignmentKey(assignment)) &&
                assignment.getAssignment() instanceof J.Literal literal &&
                literal.getValue() instanceof String value) return value;
            if (argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal &&
                literal.getValue() instanceof String value) return value;
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

    static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof J.Assignment assignment && VARIANT_KEYS.contains(assignmentKey(assignment)) ||
                argument instanceof G.MapEntry entry && VARIANT_KEYS.contains(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && hasVariant(map));
    }

    static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> VARIANT_KEYS.contains(mapKey(entry)));
    }

    static J.Literal replaceLiteral(J.Literal literal, String replacement) {
        String old = String.valueOf(literal.getValue());
        String source = literal.getValueSource();
        return literal.withValue(replacement)
                .withValueSource(source == null ? null : source.replace(old, replacement));
    }

    static PomProperties analyzeProperties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    definitions.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profileNames.add(key.name());
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyKey, Integer> references = new HashMap<>();
        Map<PropertyKey, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, references,
                        targetVersionReference(getCursor(), visited.getText()) ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, references, null);
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyKey> safe = new HashSet<>();
        ownedReferences.forEach((key, count) -> {
            if (definitions.getOrDefault(key, 0) == 1 && count.equals(references.get(key)) &&
                !("ROOT".equals(key.scope()) && profileNames.contains(key.name()))) safe.add(key);
        });
        return new PomProperties(definitions, values, Set.copyOf(safe));
    }

    private static void collectReferences(String text, Cursor cursor,
                                          Map<PropertyKey, Integer> definitions,
                                          Map<PropertyKey, Integer> references,
                                          Map<PropertyKey, Integer> ownedReferences) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            PropertyKey key = resolveOwner(cursor, matcher.group(1), definitions);
            references.merge(key, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(key, 1, Integer::sum);
        }
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        if (!PROPERTY_REFERENCE.matcher(text.trim()).matches()) return false;
        Cursor version = cursor.getParentTreeCursor();
        if (!(version.getValue() instanceof Xml.Tag versionTag) || !"version".equals(versionTag.getName())) {
            return false;
        }
        Cursor dependency = version.getParentTreeCursor();
        return dependency.getValue() instanceof Xml.Tag dependencyTag &&
               isTargetDependency(dependency, dependencyTag) && standardJar(dependencyTag);
    }

    static PropertyKey propertyKey(Cursor cursor, String name) {
        return new PropertyKey(scope(cursor), name);
    }

    static PropertyKey resolveOwner(Cursor cursor, String name, Map<PropertyKey, Integer> definitions) {
        String current = scope(cursor);
        PropertyKey local = new PropertyKey(current, name);
        return !"ROOT".equals(current) && definitions.containsKey(local)
                ? local : new PropertyKey("ROOT", name);
    }

    static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }

    record PropertyKey(String scope, String name) {
    }

    record PomProperties(Map<PropertyKey, Integer> definitions,
                         Map<PropertyKey, String> values,
                         Set<PropertyKey> safe) {
        String resolve(String raw, Cursor cursor) {
            String value = raw == null ? "" : raw.trim();
            if (FIXED_VERSION.matcher(value).matches()) return value;
            Matcher matcher = PROPERTY_REFERENCE.matcher(value);
            if (!matcher.matches()) return null;
            PropertyKey key = resolveOwner(cursor, matcher.group(1), definitions);
            return definitions.getOrDefault(key, 0) == 1 ? values.get(key) : null;
        }

        String resolveSafe(String raw, Cursor cursor) {
            String value = raw == null ? "" : raw.trim();
            if (FIXED_VERSION.matcher(value).matches()) return value;
            Matcher matcher = PROPERTY_REFERENCE.matcher(value);
            if (!matcher.matches()) return null;
            PropertyKey key = resolveOwner(cursor, matcher.group(1), definitions);
            return safe.contains(key) ? values.get(key) : null;
        }
    }
}
