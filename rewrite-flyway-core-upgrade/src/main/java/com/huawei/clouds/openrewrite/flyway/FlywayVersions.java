package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.Set;

final class FlywayVersions {
    static final String TARGET = "11.14.1";
    static final String GROUP = "org.flywaydb";
    static final String CORE = "flyway-core";
    static final String MAVEN_PLUGIN = "flyway-maven-plugin";
    static final String GRADLE_PLUGIN = "org.flywaydb.flyway";
    static final Set<String> SOURCES = Set.of(
            "5.2.1", "7.1.1", "7.8.2", "7.11.1", "7.15.0",
            "8.5.13", "9.16.3", "9.19.4", "9.20.0"
    );
    static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".idea", ".mvn", ".m2",
            "node_modules", "vendor"
    );

    private FlywayVersions() {
    }

    static boolean isSource(String version) {
        return version != null && SOURCES.contains(version.trim());
    }

    static boolean isProjectPath(Path path) {
        for (Path part : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(part.toString())) return false;
        }
        return true;
    }

    static boolean hasCoreCoordinates(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               CORE.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean isStandardJar(Xml.Tag tag) {
        boolean standardType = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty())
                .map("jar"::equals).orElse(true);
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim)
                .filter(value -> !value.isEmpty()).isEmpty();
        return standardType && noClassifier;
    }

    static boolean isMavenDependencyBlock(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag parent) || !"dependencies".equals(parent.getName())) {
            return false;
        }
        Cursor owner = dependencies.getParentTreeCursor();
        if (isProjectOrProfile(owner)) return true;
        if (owner == null || !(owner.getValue() instanceof Xml.Tag management) ||
            !"dependencyManagement".equals(management.getName())) return false;
        return isProjectOrProfile(owner.getParentTreeCursor());
    }

    static boolean isMavenCoreDependency(Cursor cursor, Xml.Tag tag) {
        return isMavenDependencyBlock(cursor, tag) && hasCoreCoordinates(tag) && isStandardJar(tag);
    }

    static boolean isMavenBuildPlugin(Cursor cursor, Xml.Tag tag) {
        if (!"plugin".equals(tag.getName())) return false;
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag pluginsTag) || !"plugins".equals(pluginsTag.getName())) return false;
        Cursor owner = plugins.getParentTreeCursor();
        if (owner == null || !(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if ("build".equals(ownerTag.getName())) return isProjectOrProfile(owner.getParentTreeCursor());
        if (!"pluginManagement".equals(ownerTag.getName())) return false;
        Cursor build = owner.getParentTreeCursor();
        return build != null && build.getValue() instanceof Xml.Tag buildTag && "build".equals(buildTag.getName()) &&
               isProjectOrProfile(build.getParentTreeCursor());
    }

    static boolean hasFlywayMavenPluginCoordinates(Xml.Tag tag) {
        return "plugin".equals(tag.getName()) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               MAVEN_PLUGIN.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean isProjectPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        if (!(properties.getValue() instanceof Xml.Tag propertiesTag) ||
            !"properties".equals(propertiesTag.getName())) return false;
        return isProject(properties.getParentTreeCursor());
    }

    static boolean isPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof Xml.Tag properties && "properties".equals(properties.getName()) &&
               !"properties".equals(tag.getName());
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        return hasTopLevelAncestorInvocation(cursor, "dependencies");
    }

    static boolean isDirectGradleDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    static boolean hasAncestorInvocation(Cursor cursor, String name) {
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation invocation &&
                name.equals(invocation.getSimpleName())) return true;
        }
        return false;
    }

    static boolean hasTopLevelAncestorInvocation(Cursor cursor, String name) {
        Cursor owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation invocation && name.equals(invocation.getSimpleName())) {
                owner = current;
                break;
            }
        }
        if (owner == null) return false;
        for (Cursor current = owner.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation) return false;
        }
        return true;
    }

    static boolean isProjectOrProfile(Cursor owner) {
        if (isProject(owner)) return true;
        if (owner == null || !(owner.getValue() instanceof Xml.Tag profile) ||
            !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        return profiles != null && profiles.getValue() instanceof Xml.Tag profilesTag &&
               "profiles".equals(profilesTag.getName()) && isProject(profiles.getParentTreeCursor());
    }

    static boolean isProject(Cursor cursor) {
        if (cursor == null || !(cursor.getValue() instanceof Xml.Tag project) ||
            !"project".equals(project.getName())) return false;
        Cursor document = cursor.getParentTreeCursor();
        return document != null && document.getValue() instanceof Xml.Document;
    }
}
