package com.huawei.clouds.openrewrite.log4jcore;

import org.openrewrite.Cursor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class Log4jCoreSupport {
    static final String GROUP = "org.apache.logging.log4j";
    static final String ARTIFACT = "log4j-core";
    static final String TARGET = "2.25.5";
    static final Set<String> SOURCES = Set.of(
            "2.13.3", "2.17.0", "2.17.1", "2.17.2", "2.18.0",
            "2.19.0", "2.20.0", "2.23.1", "2.24.1", "2.25.3");
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly", "kapt", "ksp");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".idea", ".m2",
            "node_modules", "vendor", ".cache", "coverage", "reports", "test-results", "tmp", "temp");

    private Log4jCoreSupport() {
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

    static boolean log4jProperties(Path sourcePath) {
        String file = sourcePath.getFileName().toString().toLowerCase(Locale.ROOT);
        return file.startsWith("log4j2") && file.endsWith(".properties");
    }

    static boolean log4jStructuredText(Path sourcePath) {
        String file = sourcePath.getFileName().toString().toLowerCase(Locale.ROOT);
        return file.startsWith("log4j2") &&
               (file.endsWith(".json") || file.endsWith(".yaml") || file.endsWith(".yml"));
    }

    static boolean log4jXml(Xml.Document document) {
        return "Configuration".equals(document.getRoot().getName());
    }

    static boolean isCoreDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean standardJar(Xml.Tag tag) {
        return tag.getChild("classifier").isEmpty() && "jar".equals(tag.getChildValue("type").orElse("jar"));
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag d) || !"dependencies".equals(d.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (project(owner) || profile(owner)) return true;
        if (!(owner.getValue() instanceof Xml.Tag dm) || !"dependencyManagement".equals(dm.getName())) return false;
        return project(owner.getParentTreeCursor()) || profile(owner.getParentTreeCursor());
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        if (!(properties.getValue() instanceof Xml.Tag p) || !"properties".equals(p.getName()) ||
            "properties".equals(tag.getName())) return false;
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
        return profiles.getValue() instanceof Xml.Tag p && "profiles".equals(p.getName()) &&
               project(profiles.getParentTreeCursor());
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName()) || invocation.getSelect() != null) return false;
        boolean foundRoot = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if (!foundRoot) {
                    if (!"dependencies".equals(ancestor.getSimpleName()) || ancestor.getSelect() != null) return false;
                    foundRoot = true;
                } else return false;
            }
        }
        return foundRoot;
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
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue).filter(J.Literal.class::isInstance)
                .map(J.Literal.class::cast).map(J.Literal::getValue).filter(String.class::isInstance)
                .map(String.class::cast).findFirst().orElse(null);
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
        return map.getElements().stream()
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    static J.Literal replaceLiteral(J.Literal literal, String value) {
        String old = String.valueOf(literal.getValue());
        String source = literal.getValueSource();
        return literal.withValue(value).withValueSource(source == null ? null : source.replace(old, value));
    }
}
