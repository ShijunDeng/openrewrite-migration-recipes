package com.huawei.clouds.openrewrite.springbootactuator;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SpringBootActuatorSupport {
    static final String GROUP = "org.springframework.boot";
    static final String ARTIFACT = "spring-boot-starter-actuator";
    static final String BOM = "spring-boot-dependencies";
    static final String PARENT = "spring-boot-starter-parent";
    static final String TARGET = "3.5.15";
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "2.2.6.RELEASE", "2.6.6", "2.7.9", "2.7.10", "2.7.12", "2.7.16", "2.7.17", "2.7.18",
            "3.4.0", "3.4.3", "3.4.5", "3.4.6", "3.4.9", "3.4.12"
    );
    static final Set<String> METRICS_PRODUCTS = Set.of(
            "appoptics", "atlas", "datadog", "defaults", "dynatrace", "elastic", "ganglia", "graphite",
            "humio", "influx", "jmx", "kairos", "newrelic", "prometheus", "signalfx", "simple",
            "stackdriver", "statsd"
    );
    static final Pattern FIXED_VERSION =
            Pattern.compile("[0-9]+(?:\\.[0-9]+)+(?:[.-][A-Za-z0-9]+)*");
    static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> MAP_VARIANT_KEYS = Set.of("classifier", "ext", "type", "variant");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".m2", ".idea",
            "node_modules", "vendor", ".git", ".cache", "coverage", "reports", "test-results"
    );

    private SpringBootActuatorSupport() {
    }

    static boolean generated(Path path) {
        Path parent = path.normalize().getParent();
        if (parent == null) return false;
        for (Path part : parent) {
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (GENERATED_DIRECTORIES.contains(value) || value.startsWith("generated") ||
                value.startsWith("install")) return true;
        }
        return false;
    }

    static boolean isProjectOrProfile(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag)) return false;
        if ("project".equals(tag.getName())) {
            return cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
        }
        if (!"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag profilesTag && "profiles".equals(profilesTag.getName()) &&
               profiles.getParentTreeCursor().getValue() instanceof Xml.Tag project &&
               "project".equals(project.getName()) &&
               profiles.getParentTreeCursor().getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag container) ||
            !"dependencies".equals(container.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if (isProjectOrProfile(owner)) return true;
        if (!"dependencyManagement".equals(ownerTag.getName())) return false;
        return isProjectOrProfile(owner.getParentTreeCursor());
    }

    static boolean isTargetDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) &&
               GROUP.equals(tag.getChildValue("groupId").orElse("")) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(""));
    }

    static boolean isBootBom(Cursor cursor, Xml.Tag tag) {
        if (!isProjectDependency(cursor, tag) ||
            !GROUP.equals(tag.getChildValue("groupId").orElse("")) ||
            !BOM.equals(tag.getChildValue("artifactId").orElse(""))) return false;
        return "pom".equals(tag.getChildValue("type").orElse("")) &&
               "import".equals(tag.getChildValue("scope").orElse(""));
    }

    static boolean isStandardArtifact(Xml.Tag tag) {
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        boolean jar = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty())
                .map("jar"::equals).orElse(true);
        return noClassifier && jar;
    }

    static boolean isBootParent(Cursor cursor, Xml.Tag tag) {
        if (!"parent".equals(tag.getName()) ||
            !GROUP.equals(tag.getChildValue("groupId").orElse("")) ||
            !PARENT.equals(tag.getChildValue("artifactId").orElse(""))) return false;
        Cursor project = cursor.getParentTreeCursor();
        return project.getValue() instanceof Xml.Tag projectTag && "project".equals(projectTag.getName()) &&
               project.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    static boolean containsTargetStarter(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (isTargetDependency(getCursor(), t) && isStandardArtifact(t)) found[0] = true;
                return t;
            }
        }.visitNonNull(document, ctx);
        return found[0];
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        if (!(properties.getValue() instanceof Xml.Tag container) ||
            !"properties".equals(container.getName()) || "properties".equals(tag.getName())) return false;
        return isProjectOrProfile(properties.getParentTreeCursor());
    }

    static PomProperties analyzeProperties(Xml.Document document, ExecutionContext ctx) {
        boolean hasStarter = containsTargetStarter(document, ctx);
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (isMavenPropertyDefinition(getCursor(), t)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), t.getName());
                    definitions.merge(key, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profileNames.add(key.name());
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyKey, Integer> references = new HashMap<>();
        Map<PropertyKey, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData c = super.visitCharData(charData, ec);
                Matcher matcher = PROPERTY_REFERENCE.matcher(c.getText());
                while (matcher.find()) {
                    PropertyKey key = resolveOwner(getCursor(), matcher.group(1), definitions);
                    references.merge(key, 1, Integer::sum);
                    if (ownedVersionReference(getCursor(), c.getText(), hasStarter)) {
                        ownedReferences.merge(key, 1, Integer::sum);
                    }
                }
                return c;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyKey> safe = new HashSet<>();
        ownedReferences.forEach((key, count) -> {
            if (definitions.getOrDefault(key, 0) == 1 &&
                (SOURCE_VERSIONS.contains(values.get(key)) || TARGET.equals(values.get(key))) &&
                count > 0 && count.equals(references.get(key)) &&
                !("ROOT".equals(key.scope()) && profileNames.contains(key.name()))) safe.add(key);
        });
        return new PomProperties(definitions, values, Set.copyOf(safe));
    }

    private static boolean ownedVersionReference(Cursor cursor, String text, boolean hasStarter) {
        if (!PROPERTY_REFERENCE.matcher(text.trim()).matches()) return false;
        Cursor version = cursor.getParentTreeCursor();
        if (!(version.getValue() instanceof Xml.Tag versionTag) || !"version".equals(versionTag.getName())) {
            return false;
        }
        Cursor owner = version.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        return isTargetDependency(owner, ownerTag) ||
               hasStarter && (isBootBom(owner, ownerTag) || isBootParent(owner, ownerTag));
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

    static boolean isRootGradleDependency(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName()) || invocation.getSelect() != null) return false;
        boolean foundDependencies = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if (!foundDependencies) {
                    if (!"dependencies".equals(ancestor.getSimpleName()) || ancestor.getSelect() != null) return false;
                    foundDependencies = true;
                } else {
                    return false;
                }
            }
        }
        return foundDependencies;
    }

    static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isRootGradleDependency(parent, invocation);
    }

    static String mapValue(J.MethodInvocation invocation, String key) {
        for (J argument : invocation.getArguments()) {
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

    static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal &&
            literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && MAP_VARIANT_KEYS.contains(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && hasVariant(map));
    }

    static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> MAP_VARIANT_KEYS.contains(mapKey(entry)));
    }

    static J.Literal replaceLiteral(J.Literal literal, String replacement) {
        String old = String.valueOf(literal.getValue());
        String source = literal.getValueSource();
        return literal.withValue(replacement)
                .withValueSource(source == null ? null : source.replace(old, replacement));
    }

    static boolean isTargetCoordinate(String value) {
        String[] parts = value.split(":", -1);
        return parts.length == 3 && GROUP.equals(parts[0]) && ARTIFACT.equals(parts[1]);
    }

    static String coordinateVersion(String value) {
        String[] parts = value.split(":", -1);
        return parts.length == 3 ? parts[2] : null;
    }

    static boolean targetConflict(String version) {
        if (version == null) return false;
        Matcher matcher = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
                .matcher(version.trim());
        if (!matcher.find()) return false;
        int comparison = compareComponent(matcher.group(1), 3);
        if (comparison != 0) return comparison > 0;
        comparison = compareComponent(matcher.group(2), 5);
        if (comparison != 0) return comparison > 0;
        return compareComponent(
                matcher.group(3) == null ? "0" : matcher.group(3), 15) > 0;
    }

    static boolean belowJava17(String raw) {
        if (raw == null) return false;
        Matcher matcher = Pattern.compile("^(?:1\\.)?([0-9]+)").matcher(raw.trim());
        return matcher.find() && compareComponent(matcher.group(1), 17) < 0;
    }

    private static int compareComponent(String raw, int target) {
        String normalized = raw.replaceFirst("^0+(?!$)", "");
        String expected = Integer.toString(target);
        if (normalized.length() != expected.length()) {
            return Integer.compare(normalized.length(), expected.length());
        }
        return normalized.compareTo(expected);
    }

    static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }

    static Xml.Tag markVersionOrOwner(Xml.Tag owner, String message) {
        return owner.getChild("version").map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    record PropertyKey(String scope, String name) {
    }

    record PomProperties(Map<PropertyKey, Integer> definitions,
                         Map<PropertyKey, String> values,
                         Set<PropertyKey> safe) {
        boolean safeReference(String raw, Cursor cursor) {
            if (raw == null) return false;
            Matcher matcher = PROPERTY_REFERENCE.matcher(raw.trim());
            if (!matcher.matches()) return FIXED_VERSION.matcher(raw.trim()).matches();
            return safe.contains(resolveOwner(cursor, matcher.group(1), definitions));
        }

        String resolveUnique(String raw, Cursor cursor) {
            String value = raw == null ? "" : raw.trim();
            if (FIXED_VERSION.matcher(value).matches()) return value;
            Matcher matcher = PROPERTY_REFERENCE.matcher(value);
            if (!matcher.matches()) return null;
            PropertyKey key = resolveOwner(cursor, matcher.group(1), definitions);
            return definitions.getOrDefault(key, 0) == 1 ? values.get(key) : null;
        }
    }
}
