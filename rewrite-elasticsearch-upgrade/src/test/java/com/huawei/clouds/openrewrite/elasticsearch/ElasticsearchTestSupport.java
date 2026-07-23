package com.huawei.clouds.openrewrite.elasticsearch;

import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;

final class ElasticsearchTestSupport {
    private ElasticsearchTestSupport() {
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.elasticsearch",
                "org.openrewrite.java.testing.testcontainers").build();
    }

    static Recipe recipe(String name) {
        return environment().activateRecipes(name);
    }

    static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath());
    }

    static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>client</artifactId><version>1</version>" + body + "</project>";
    }

    static String pom(String version) {
        return project("<dependencies>" + testcontainersDependency(version, "") + "</dependencies>");
    }

    static String testcontainersDependency(String version, String extra) {
        return dependency("org.testcontainers", "elasticsearch", version, extra);
    }

    static String serverDependency(String version) {
        return dependency("org.elasticsearch", "elasticsearch", version, "");
    }

    static String dependency(String group, String artifact, String version, String extra) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + extra + "</dependency>";
    }

    static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
