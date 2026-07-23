package com.huawei.clouds.openrewrite.jettyhttp;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.semver.LatestRelease;
import org.openrewrite.xml.tree.Xml;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class JettyHttpSupport {
    static final String GROUP = "org.eclipse.jetty";
    static final String ARTIFACT = "jetty-http";
    static final String TARGET = "12.0.34";
    static final String DOWNGRADE_SOURCE = "12.1.0";
    static final String TARGET_CONFLICT = "目标版本冲突（禁止降级）";
    static final Set<String> AUTO_SOURCES = Set.of(
            "9.4.39.v20210325", "9.4.53.v20231009", "9.4.54.v20240208",
            "9.4.57.v20241219", "9.4.58.v20250814", "11.0.20",
            "12.0.12", "12.0.15", "12.0.16", "12.0.25");
    static final Set<String> WORKBOOK_SOURCES = Set.of(
            "9.4.39.v20210325", "9.4.53.v20231009", "9.4.54.v20240208",
            "9.4.57.v20241219", "9.4.58.v20250814", "11.0.20",
            "12.0.12", "12.0.15", "12.0.16", "12.0.25", DOWNGRADE_SOURCE);
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp");
    private static final Set<String> VARIANT_KEYS = Set.of("classifier", "ext", "type", "variant");
    private static final Pattern FIXED =
            Pattern.compile("^[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*$");
    private static final LatestRelease OFFICIAL_VERSION_COMPARATOR = new LatestRelease(null);
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".m2", ".idea",
            "node_modules", "vendor", ".cache", "coverage", "reports", "test-results", "tmp", "temp");

    private JettyHttpSupport() {
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

    static boolean fixedVersion(String value) {
        return value != null && FIXED.matcher(value.trim()).matches();
    }

    static boolean targetConflict(String value) {
        if (value == null || !FIXED.matcher(value.trim()).matches()) return false;
        String version = value.trim();
        try {
            return OFFICIAL_VERSION_COMPARATOR.compare(null, version, TARGET) > 0;
        } catch (NumberFormatException oversizedNumericSegment) {
            // Core compares numeric segments as long. Preserve no-downgrade for
            // arbitrarily large fixed versions with a BigInteger fallback.
        }
        String[] candidate = version.split("-", 2)[0].split("\\.");
        String[] target = TARGET.split("\\.");
        int length = Math.max(candidate.length, target.length);
        for (int index = 0; index < length; index++) {
            String segment = index < candidate.length ? candidate[index] : "0";
            BigInteger left = new BigInteger(segment.matches("[0-9]+") ? segment : "0");
            BigInteger right = new BigInteger(index < target.length ? target[index] : "0");
            int order = left.compareTo(right);
            if (order != 0) return order > 0;
        }
        return false;
    }

    static boolean isJettyHttpDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) &&
               GROUP.equals(tag.getChildValue("groupId").map(String::trim).orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").map(String::trim).orElse(null));
    }

    static boolean standardJar(Xml.Tag tag) {
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        boolean jar = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty())
                .map("jar"::equals).orElse(true);
        return noClassifier && jar;
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag container) ||
            !"dependencies".equals(container.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (project(owner) || profile(owner)) return true;
        if (!(owner.getValue() instanceof Xml.Tag management) ||
            !"dependencyManagement".equals(management.getName())) return false;
        return project(owner.getParentTreeCursor()) || profile(owner.getParentTreeCursor());
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        if (!(properties.getValue() instanceof Xml.Tag container) ||
            !"properties".equals(container.getName()) || "properties".equals(tag.getName())) return false;
        Cursor owner = properties.getParentTreeCursor();
        return project(owner) || profile(owner);
    }

    private static boolean project(Cursor cursor) {
        return cursor.getValue() instanceof Xml.Tag tag && "project".equals(tag.getName()) &&
               cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static boolean profile(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag) || !"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag container && "profiles".equals(container.getName()) &&
               project(profiles.getParentTreeCursor());
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
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
               isGradleDependencyInvocation(parent, invocation);
    }

    static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static String mapValue(J.MethodInvocation invocation, String key) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal && literal.getValue() instanceof String value) {
                return value;
            }
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

    static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
