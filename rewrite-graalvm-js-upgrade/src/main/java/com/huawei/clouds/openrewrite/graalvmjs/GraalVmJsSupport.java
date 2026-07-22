package com.huawei.clouds.openrewrite.graalvmjs;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class GraalVmJsSupport {
    static final String GROUP = "org.graalvm.js";
    static final String ARTIFACT = "js";
    static final String TARGET = "24.2.1";
    static final Set<String> SOURCES = Set.of("22.3.0", "22.3.1");
    static final Set<String> SELECTED = Set.of("22.3.0", "22.3.1", TARGET);
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp");
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", ".gradle", ".mvn", ".m2", ".idea", ".angular",
            ".nx", ".next", ".cache", ".output", "node_modules", "bower_components", "vendor",
            ".pnpm", ".yarn", ".npm", "coverage", "reports", "test-results", "storybook-static");

    private GraalVmJsSupport() {
    }

    /** Only parent components are filtered, so leaves such as install.java and install.gradle remain eligible. */
    static boolean excluded(Path path) {
        Path parent = path.normalize().getParent();
        if (parent == null) return false;
        for (Path component : parent) {
            String value = component.toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED_DIRECTORIES.contains(value) || value.startsWith("generated") ||
                value.startsWith("install")) return true;
        }
        return false;
    }

    static boolean isGraalJsDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean isCoordinate(Xml.Tag tag, String group, String artifact) {
        return group.equals(tag.getChildValue("groupId").orElse(null)) &&
               artifact.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean standardJar(Xml.Tag tag) {
        return tag.getChild("classifier").isEmpty() && "jar".equals(tag.getChildValue("type").orElse("jar"));
    }

    static boolean noClassifier(Xml.Tag tag) {
        return tag.getChild("classifier").isEmpty();
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependenciesCursor = cursor.getParentTreeCursor();
        if (!(dependenciesCursor.getValue() instanceof Xml.Tag dependencies) ||
            !"dependencies".equals(dependencies.getName())) return false;
        Cursor owner = dependenciesCursor.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if (isProjectOwner(owner) || isProfileOwner(owner)) return true;
        if (!"dependencyManagement".equals(ownerTag.getName())) return false;
        Cursor managedOwner = owner.getParentTreeCursor();
        return isProjectOwner(managedOwner) || isProfileOwner(managedOwner);
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag properties) || !"properties".equals(properties.getName()) ||
            "properties".equals(tag.getName())) return false;
        Cursor owner = parent.getParentTreeCursor();
        return isProjectOwner(owner) || isProfileOwner(owner);
    }

    private static boolean isProjectOwner(Cursor cursor) {
        return cursor.getValue() instanceof Xml.Tag tag && "project".equals(tag.getName()) &&
               cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static boolean isProfileOwner(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag) || !"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag profilesTag && "profiles".equals(profilesTag.getName()) &&
               isProjectOwner(profiles.getParentTreeCursor());
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName()) || invocation.getSelect() != null) return false;
        boolean foundRootDependencies = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if (!foundRootDependencies) {
                    if (!"dependencies".equals(ancestor.getSimpleName()) || ancestor.getSelect() != null) return false;
                    foundRootDependencies = true;
                } else {
                    return false;
                }
            }
        }
        return foundRootDependencies;
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
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    static J.Literal replaceLiteral(J.Literal literal, String newValue) {
        String oldValue = String.valueOf(literal.getValue());
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }

    static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
