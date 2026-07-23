package com.huawei.clouds.openrewrite.trinojdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeTrinoJdbcDependencyTest implements RewriteTest {
    @Override public void defaults(RecipeSpec spec) { spec.recipe(new UpgradeSelectedTrinoJdbcDependency()); }

    @Test void upgradesWorkbookSource() {
        rewriteRun(xml(pom("418"), pom("453"), s -> s.path("pom.xml")));
    }

    @Test void whitelistMatchesWorkbookRow601() {
        assertEquals(Set.of("418"), TrinoJdbcSupport.SOURCES);
        assertEquals("453", TrinoJdbcSupport.TARGET);
    }

    @Test void rootProfileAndDependencyManagementAreOwned() {
        String before = project("<dependencies>" + dep("418") + "</dependencies><dependencyManagement><dependencies>" + dep("418") +
                "</dependencies></dependencyManagement><profiles><profile><id>it</id><dependencies>" + dep("418") + "</dependencies></profile></profiles>");
        rewriteRun(xml(before, before.replace("<version>418</version>", "<version>453</version>"), s -> s.path("pom.xml")));
    }

    @Test void upgradesExclusiveRootPropertyAndProfileOverride() {
        rewriteRun(
                xml(project("<properties><trino.version>418</trino.version></properties><dependencies>" + dep("${trino.version}") + "</dependencies>"),
                        project("<properties><trino.version>453</trino.version></properties><dependencies>" + dep("${trino.version}") + "</dependencies>"), s -> s.path("pom.xml")),
                xml(project("<properties><trino.version>417</trino.version></properties><profiles><profile><id>it</id><properties><trino.version>418</trino.version></properties><dependencies>" + dep("${trino.version}") + "</dependencies></profile></profiles>"),
                        project("<properties><trino.version>417</trino.version></properties><profiles><profile><id>it</id><properties><trino.version>453</trino.version></properties><dependencies>" + dep("${trino.version}") + "</dependencies></profile></profiles>"), s -> s.path("nested/pom.xml")));
    }

    @ParameterizedTest(name = "preserves {0}") @MethodSource("ambiguousProperties")
    void preservesSharedOrAmbiguousProperty(String label, String source) {
        rewriteRun(xml(source, s -> s.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("another dependency", project("<properties><v>418</v></properties><dependencies>" + dep("${v}") + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${v}</version></dependency></dependencies>")),
                Arguments.of("build value", project("<properties><v>418</v></properties><dependencies>" + dep("${v}") + "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("duplicate definition", project("<properties><v>418</v><v>418</v></properties><dependencies>" + dep("${v}") + "</dependencies>")),
                Arguments.of("attribute reference", project("<properties><v>418</v></properties><dependencies>" + dep("${v}") + "</dependencies><x value=\"${v}\"/>")),
                Arguments.of("profile falls back to shared root", project("<properties><v>418</v></properties><profiles><profile><id>it</id><dependencies>" + dep("${v}") + "</dependencies></profile></profiles><name>${v}</name>"))
        );
    }

    @ParameterizedTest @ValueSource(strings = {"351", "417", "419", "430", "431", "449", "452", "453", "454", "470", "999"})
    void doesNotWidenSources(String version) {
        rewriteRun(xml(pom(version), s -> s.path("pom.xml")));
    }

    @Test void protectsVariantsVersionlessPluginAndCustomOwners() {
        rewriteRun(
                xml(project("<dependencies>" + noVersion() + dep("418", "<classifier>tests</classifier>") + dep("418", "<type>zip</type>") + "</dependencies>"), s -> s.path("pom.xml")),
                xml(project("<build><plugins><plugin><artifactId>x</artifactId><dependencies>" + dep("418") + "</dependencies></plugin></plugins></build>"), s -> s.path("pom.xml")),
                xml(project("<company><dependencies>" + dep("418") + "</dependencies></company>"), s -> s.path("pom.xml")));
    }

    @Test void upgradesRootGradleStringAndMapForms() {
        rewriteRun(
                buildGradle("dependencies { implementation 'io.trino:trino-jdbc:418' }", "dependencies { implementation 'io.trino:trino-jdbc:453' }", s -> s.path("build.gradle")),
                buildGradleKts("dependencies { implementation(\"io.trino:trino-jdbc:418\") }", "dependencies { implementation(\"io.trino:trino-jdbc:453\") }", s -> s.path("build.gradle.kts")),
                buildGradle("dependencies { compileOnly group: 'io.trino', name: 'trino-jdbc', version: '418' }", "dependencies { compileOnly group: 'io.trino', name: 'trino-jdbc', version: '453' }"),
                buildGradle("dependencies { runtimeOnly([group: 'io.trino', name: 'trino-jdbc', version: '418']) }", "dependencies { runtimeOnly([group: 'io.trino', name: 'trino-jdbc', version: '453']) }"));
    }

    @ParameterizedTest(name = "nested Gradle {0}") @MethodSource("nestedGradle")
    void ignoresNestedGradle(String label, String source) { rewriteRun(buildGradle(source)); }

    static Stream<Arguments> nestedGradle() {
        String coordinate = "'io.trino:trino-jdbc:418'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + coordinate + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + coordinate + " } }"),
                Arguments.of("project", "project(':a') { dependencies { implementation " + coordinate + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + coordinate + " } }"),
                Arguments.of("custom", "custom { dependencies { implementation " + coordinate + " } }"),
                Arguments.of("selected invocation", "dependencies { project(':a').implementation " + coordinate + " }"));
    }

    @ParameterizedTest @ValueSource(strings = {"target", "build", "generated", "generatedSources", "installation", "INSTALL-cache", ".gradle", ".m2", "node_modules"})
    void ignoresGeneratedParents(String parent) {
        rewriteRun(xml(pom("418"), s -> s.path(parent + "/pom.xml")));
    }

    @Test void variantsVariablesAndLeafNamesArePreservedOrMigratedIdempotently() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradle("dependencies { implementation 'io.trino:trino-jdbc:418' }", "dependencies { implementation 'io.trino:trino-jdbc:453' }", s -> s.path("install.gradle")),
                buildGradle("dependencies { implementation 'io.trino:trino-jdbc:418:tests' }"),
                buildGradle("dependencies { implementation 'io.trino:trino-jdbc:418@zip' }"),
                buildGradle("dependencies { implementation \"io.trino:trino-jdbc:$trinoVersion\" }"));
    }

    @Test void publicRecipesHaveStrictComposition() {
        Environment env = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.trinojdbc").build();
        var upgrade = env.activateRecipes("com.huawei.clouds.openrewrite.trinojdbc.UpgradeTrinoJdbcTo453");
        var migrate = env.activateRecipes("com.huawei.clouds.openrewrite.trinojdbc.MigrateTrinoJdbcTo453");
        assertEquals(1, upgrade.getRecipeList().size());
        assertEquals(UpgradeSelectedTrinoJdbcDependency.class, upgrade.getRecipeList().get(0).getClass());
        assertEquals("com.huawei.clouds.openrewrite.trinojdbc.UpgradeTrinoJdbcTo453", migrate.getRecipeList().get(0).getName());
    }

    @Test void recommendedRecipeExecutesDependencyAndUrlAutomation() {
        Environment env = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.trinojdbc").build();
        rewriteRun(spec -> spec.recipe(env.activateRecipes("com.huawei.clouds.openrewrite.trinojdbc.MigrateTrinoJdbcTo453"))
                        .parser(JavaParser.fromJavaVersion().classpath("trino-jdbc")),
                xml(pom("418"), pom("453"), s -> s.path("pom.xml")),
                java("class A { String url = \"jdbc:trino://host/catalog?legacyPreparedStatements=false\"; }",
                        "class A { String url = /*~~(Review Trino JDBC URL policy: authentication/TLS validation changed and 453 adds hostnameInCertificate, timezone, explicitPrepare, assumeNullCatalogMeansCurrent, session authorization, SQL PATH and OS-keystore support)~~>*/\"jdbc:trino://host/catalog?explicitPrepare=false\"; }",
                        s -> s.afterRecipe(after -> {
                            String out = after.printAll();
                            org.junit.jupiter.api.Assertions.assertTrue(out.contains("explicitPrepare=false"), out);
                            org.junit.jupiter.api.Assertions.assertTrue(out.contains("Review Trino JDBC URL policy"), out);
                        })));
    }

    static String pom(String version) { return project("<dependencies>" + dep(version) + "</dependencies>"); }
    static String project(String body) { return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId><version>1</version>" + body + "</project>"; }
    static String dep(String version) { return dep(version, ""); }
    static String dep(String version, String extra) { return "<dependency><groupId>io.trino</groupId><artifactId>trino-jdbc</artifactId><version>" + version + "</version>" + extra + "</dependency>"; }
    static String noVersion() { return "<dependency><groupId>io.trino</groupId><artifactId>trino-jdbc</artifactId></dependency>"; }
}
