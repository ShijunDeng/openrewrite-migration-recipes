package com.huawei.clouds.openrewrite.junrar;

import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.semver.LatestRelease;
import org.openrewrite.xml.tree.Xml;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class JunrarSupport {
    static final String GROUP = "com.github.junrar";
    static final String ARTIFACT = "junrar";
    static final String TARGET = "7.5.10";
    static final Set<String> SOURCES = Set.of("7.5.5", "7.5.8");
    static final String TARGET_CONFLICT = "目标版本冲突（禁止降级）";
    static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> VARIANT_KEYS = Set.of("classifier", "ext", "type", "variant");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".m2", ".idea",
            "node_modules", "bower_components", "vendor", ".pnpm", ".yarn", ".npm", ".angular", ".nx",
            ".next", ".nuxt", ".cache", ".git", ".vscode", ".turbo", ".parcel-cache", ".vite",
            "coverage", ".output", "tmp", "temp", "report", "reports", "storybook-static", "test-results"
    );
    private static final LatestRelease OFFICIAL_VERSION_COMPARATOR = new LatestRelease(null);

    private JunrarSupport() {
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

    static boolean selected(SourceFile source) {
        return source.getMarkers().findFirst(JunrarProjectMarker.class).isPresent();
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag container) ||
            !"dependencies".equals(container.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if (isProjectOrProfile(owner)) return true;
        return "dependencyManagement".equals(ownerTag.getName()) &&
               isProjectOrProfile(owner.getParentTreeCursor());
    }

    static boolean isTargetDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) &&
               GROUP.equals(tag.getChildValue("groupId").map(String::trim).orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").map(String::trim).orElse(null));
    }

    static boolean standardArtifact(Xml.Tag tag) {
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        boolean jar = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty())
                .map("jar"::equals).orElse(true);
        return noClassifier && jar;
    }

    static boolean isPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        return properties.getValue() instanceof Xml.Tag container &&
               "properties".equals(container.getName()) && !"properties".equals(tag.getName()) &&
               isProjectOrProfile(properties.getParentTreeCursor());
    }

    private static boolean isProjectOrProfile(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag)) return false;
        if ("project".equals(tag.getName())) {
            return cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
        }
        if (!"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag profilesTag &&
               "profiles".equals(profilesTag.getName()) &&
               profiles.getParentTreeCursor().getValue() instanceof Xml.Tag project &&
               "project".equals(project.getName()) &&
               profiles.getParentTreeCursor().getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName()) || invocation.getSelect() != null) {
            return false;
        }
        boolean dependencies = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if (!dependencies) {
                    if (!"dependencies".equals(ancestor.getSimpleName()) || ancestor.getSelect() != null) {
                        return false;
                    }
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
               isGradleDependencyInvocation(parent, invocation);
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
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal &&
            literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && VARIANT_KEYS.contains(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && hasVariant(map));
    }

    static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> VARIANT_KEYS.contains(mapKey(entry)));
    }

    static boolean higherThanTarget(String version) {
        if (!FIXED.matcher(version).matches()) return false;
        try {
            return OFFICIAL_VERSION_COMPARATOR.compare(null, version, TARGET) > 0;
        } catch (NumberFormatException oversizedNumericSegment) {
            // Core uses long numeric segments. Preserve no-downgrade for arbitrarily large,
            // still-fixed versions with a BigInteger numeric fallback.
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
}
