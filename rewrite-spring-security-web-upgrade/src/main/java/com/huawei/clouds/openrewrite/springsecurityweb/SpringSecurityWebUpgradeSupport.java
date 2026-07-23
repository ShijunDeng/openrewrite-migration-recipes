package com.huawei.clouds.openrewrite.springsecurityweb;

import org.openrewrite.Cursor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.semver.LatestRelease;
import org.openrewrite.xml.tree.Xml;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SpringSecurityWebUpgradeSupport {
    static final String GROUP = "org.springframework.security";
    static final String ARTIFACT = "spring-security-web";
    static final String TARGET = "6.5.11";
    static final String TARGET_CONFLICT = "目标版本冲突（禁止降级）";

    static final Set<String> WORKBOOK_VERSIONS = Set.of(
            "5.1.5.RELEASE", "5.6.12", "5.7.14", "5.8.3", "5.8.6", "5.8.7", "5.8.8",
            "5.8.11", "5.8.15", "5.8.16", "6.2.2", "6.2.3", "6.2.6", "6.2.7", "6.2.8",
            "6.3.4", "6.4.4", "6.4.6", "6.4.10", "6.4.11", "6.4.13", "6.5.1",
            "6.5.9", "6.5.10", "7.0.0", "7.0.4");
    static final Set<String> UPGRADE_VERSIONS = Set.of(
            "5.1.5.RELEASE", "5.6.12", "5.7.14", "5.8.3", "5.8.6", "5.8.7", "5.8.8",
            "5.8.11", "5.8.15", "5.8.16", "6.2.2", "6.2.3", "6.2.6", "6.2.7", "6.2.8",
            "6.3.4", "6.4.4", "6.4.6", "6.4.10", "6.4.11", "6.4.13", "6.5.1",
            "6.5.9", "6.5.10");
    static final Set<String> CONFLICT_VERSIONS = Set.of("7.0.0", "7.0.4");

    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp");
    private static final Set<String> VARIANT_KEYS = Set.of("classifier", "ext", "type", "variant");
    private static final Pattern FIXED_VERSION =
            Pattern.compile("^[0-9]+(?:\\.[0-9]+)+(?:[.-][A-Za-z0-9]+)*$");
    private static final Pattern SOURCE_NUMERIC =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[.-][A-Za-z0-9][A-Za-z0-9.-]*)?$");
    private static final Pattern RELEASE = Pattern.compile("^(\\d+)\\.(\\d+)$");
    private static final LatestRelease OFFICIAL_VERSION_COMPARATOR = new LatestRelease(null);
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".m2",
            ".idea", "node_modules", "vendor", ".cache", "coverage", "reports", "test-results", "tmp", "temp");

    private SpringSecurityWebUpgradeSupport() {
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

    static boolean fixedVersion(String version) {
        return version != null && FIXED_VERSION.matcher(version).matches();
    }

    static boolean targetConflict(String version) {
        if (!fixedVersion(version)) return false;
        try {
            return OFFICIAL_VERSION_COMPARATOR.compare(null, version, TARGET) > 0;
        } catch (NumberFormatException oversizedNumericSegment) {
            // Core uses long numeric segments. Preserve no-downgrade for
            // arbitrarily large, still-fixed versions with BigInteger.
        }
        String[] candidate = version.split("-", 2)[0].split("\\.");
        String[] target = TARGET.split("\\.");
        int length = Math.max(candidate.length, target.length);
        for (int i = 0; i < length; i++) {
            String segment = i < candidate.length ? candidate[i] : "0";
            BigInteger left = new BigInteger(segment.matches("[0-9]+") ? segment : "0");
            BigInteger right = new BigInteger(i < target.length ? target[i] : "0");
            int comparison = left.compareTo(right);
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    static boolean requiresMigrationTo(String sourceVersion, String targetRelease) {
        if (sourceVersion == null || targetRelease == null) return false;
        Matcher source = SOURCE_NUMERIC.matcher(sourceVersion);
        Matcher target = RELEASE.matcher(targetRelease);
        if (!source.matches() || !target.matches()) return false;
        BigInteger sourceMajor = new BigInteger(source.group(1));
        BigInteger sourceMinor = new BigInteger(source.group(2));
        BigInteger targetMajor = new BigInteger(target.group(1));
        BigInteger targetMinor = new BigInteger(target.group(2));
        int majorOrder = sourceMajor.compareTo(targetMajor);
        return majorOrder < 0 ||
               majorOrder == 0 && sourceMinor.compareTo(targetMinor) < 0;
    }

    static boolean isPrimaryDependency(Cursor cursor, Xml.Tag tag) {
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
}
