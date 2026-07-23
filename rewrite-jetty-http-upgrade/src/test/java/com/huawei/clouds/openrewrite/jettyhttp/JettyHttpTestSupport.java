package com.huawei.clouds.openrewrite.jettyhttp;

import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;

final class JettyHttpTestSupport {
    private JettyHttpTestSupport() {
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.jettyhttp",
                "org.openrewrite.java",
                "org.openrewrite.java.migrate").build();
    }

    static Recipe recipe(String name) {
        return environment().activateRecipes(name);
    }

    static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().classpath("jetty-http", "jetty-util", "jetty-io", "slf4j-api");
    }

    static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>client</artifactId><version>1</version>" + body + "</project>";
    }

    static String pom(String version) {
        return project("<dependencies>" + dependency(version, "") + "</dependencies>");
    }

    static String dependency(String version, String extra) {
        return "<dependency><groupId>org.eclipse.jetty</groupId><artifactId>jetty-http</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + extra + "</dependency>";
    }

    static String dependency(String group, String artifact, String version) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + "</dependency>";
    }

    static int occurrences(String text, String token) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
