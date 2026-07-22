package com.huawei.clouds.openrewrite.graalvmjs;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class GraalVmJsLayoutTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateGraalVmJsDependencyLayout());
    }

    @Test
    void migratesMavenLanguageSelectorAndAddsPolyglotApi() {
        rewriteRun(pomXml(
                project("""
                  <dependencies>
                    <dependency>
                      <groupId>org.graalvm.js</groupId>
                      <artifactId>js</artifactId>
                      <version>24.2.1</version>
                    </dependency>
                  </dependencies>
                """),
                project("""
                  <dependencies>
                    <dependency>
                      <groupId>org.graalvm.polyglot</groupId>
                      <artifactId>js</artifactId>
                      <version>24.2.1</version>
                      <type>pom</type>
                    </dependency>
                    <dependency>
                      <groupId>org.graalvm.polyglot</groupId>
                      <artifactId>polyglot</artifactId>
                      <version>24.2.1</version>
                    </dependency>
                  </dependencies>
                """)));
    }

    @Test
    void changesExplicitJarTypeToPom() {
        rewriteRun(pomXml(
                project("<dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId>" +
                        "<version>24.2.1</version><type>jar</type></dependency></dependencies>"),
                project("<dependencies><dependency><groupId>org.graalvm.polyglot</groupId><artifactId>js</artifactId>" +
                        "<version>24.2.1</version><type>pom</type></dependency><dependency>" +
                        "<groupId>org.graalvm.polyglot</groupId><artifactId>polyglot</artifactId>" +
                        "<version>24.2.1</version></dependency></dependencies>")));
    }

    @Test
    void preservesScopeAndOptionalOnAddedApi() {
        rewriteRun(pomXml(
                project("""
                  <dependencies>
                    <dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>24.2.1</version><scope>test</scope><optional>true</optional></dependency>
                  </dependencies>
                """),
                project("""
                  <dependencies>
                    <dependency><groupId>org.graalvm.polyglot</groupId><artifactId>js</artifactId><version>24.2.1</version><scope>test</scope><optional>true</optional><type>pom</type></dependency>
                    <dependency><groupId>org.graalvm.polyglot</groupId><artifactId>polyglot</artifactId><version>24.2.1</version><scope>test</scope><optional>true</optional></dependency>
                  </dependencies>
                """)));
    }

    @Test
    void usesOwnedTargetPropertyForBothPomDependencies() {
        rewriteRun(pomXml(
                project("""
                  <properties><graal.version>24.2.1</graal.version></properties>
                  <dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>${graal.version}</version></dependency></dependencies>
                """),
                project("""
                  <properties><graal.version>24.2.1</graal.version></properties>
                  <dependencies><dependency><groupId>org.graalvm.polyglot</groupId><artifactId>js</artifactId><version>${graal.version}</version><type>pom</type></dependency><dependency><groupId>org.graalvm.polyglot</groupId><artifactId>polyglot</artifactId><version>${graal.version}</version></dependency></dependencies>
                """)));
    }

    @Test
    void profileOverrideControlsCoordinateMigration() {
        rewriteRun(pomXml(
                project("""
                  <properties><g>23.0.0</g></properties>
                  <profiles><profile><id>new</id><properties><g>24.2.1</g></properties><dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>${g}</version></dependency></dependencies></profile></profiles>
                """),
                project("""
                  <properties><g>23.0.0</g></properties>
                  <profiles><profile><id>new</id><properties><g>24.2.1</g></properties><dependencies><dependency><groupId>org.graalvm.polyglot</groupId><artifactId>js</artifactId><version>${g}</version><type>pom</type></dependency><dependency><groupId>org.graalvm.polyglot</groupId><artifactId>polyglot</artifactId><version>${g}</version></dependency></dependencies></profile></profiles>
                """)));
    }

    @Test
    void migratesVersionlessDependenciesOwnedByLocalDependencyManagement() {
        rewriteRun(pomXml(
                project("""
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>24.2.1</version></dependency>
                  </dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId></dependency>
                  </dependencies>
                """),
                project("""
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.graalvm.polyglot</groupId><artifactId>js</artifactId><version>24.2.1</version><type>pom</type></dependency>
                    <dependency><groupId>org.graalvm.polyglot</groupId><artifactId>polyglot</artifactId><version>24.2.1</version></dependency>
                  </dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.graalvm.polyglot</groupId><artifactId>js</artifactId><type>pom</type></dependency>
                    <dependency><groupId>org.graalvm.polyglot</groupId><artifactId>polyglot</artifactId></dependency>
                  </dependencies>
                """)));
    }

    @Test
    void localManagementProfileOverrideWinsAndDoesNotLeak() {
        rewriteRun(xml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>scopes</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>24.2.1</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId></dependency></dependencies>
                  <profiles>
                    <profile><id>override</id><dependencyManagement><dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>23.0.0</version></dependency></dependencies></dependencyManagement><dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId></dependency></dependencies></profile>
                    <profile><id>inherits-root</id><dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId></dependency></dependencies></profile>
                    <profile><id>local-target</id><dependencyManagement><dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>24.2.1</version></dependency></dependencies></dependencyManagement><dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId></dependency></dependencies></profile>
                  </profiles>
                </project>
                """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    org.junit.jupiter.api.Assertions.assertEquals(2, occurrences(printed, "<groupId>org.graalvm.js</groupId>"));
                    org.junit.jupiter.api.Assertions.assertTrue(
                            occurrences(printed, "<groupId>org.graalvm.polyglot</groupId>") >= 8, printed);
                })));
    }

    @Test
    void doesNotDuplicateExistingPolyglotApi() {
        rewriteRun(pomXml(
                project("<dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId>" +
                        "<version>24.2.1</version></dependency><dependency><groupId>org.graalvm.polyglot</groupId>" +
                        "<artifactId>polyglot</artifactId><version>24.2.1</version></dependency></dependencies>"),
                project("<dependencies><dependency><groupId>org.graalvm.polyglot</groupId><artifactId>js</artifactId>" +
                        "<version>24.2.1</version><type>pom</type></dependency><dependency>" +
                        "<groupId>org.graalvm.polyglot</groupId><artifactId>polyglot</artifactId>" +
                        "<version>24.2.1</version></dependency></dependencies>")));
    }

    @Test
    void migratesLiteralGraalSdkAndScriptEngineCompanions() {
        rewriteRun(pomXml(
                project("""
                  <dependencies>
                    <dependency><groupId>org.graalvm.sdk</groupId><artifactId>graal-sdk</artifactId><version>22.3.0</version></dependency>
                    <dependency><groupId>org.graalvm.js</groupId><artifactId>js-scriptengine</artifactId><version>22.3.1</version></dependency>
                  </dependencies>
                """),
                project("""
                  <dependencies>
                    <dependency><groupId>org.graalvm.polyglot</groupId><artifactId>polyglot</artifactId><version>24.2.1</version></dependency>
                    <dependency><groupId>org.graalvm.js</groupId><artifactId>js-scriptengine</artifactId><version>24.2.1</version></dependency>
                  </dependencies>
                """)));
    }

    @Test
    void protectsPropertyBackedCompanionsAndClassifier() {
        String before = project("""
                  <properties><g>22.3.1</g></properties>
                  <dependencies>
                    <dependency><groupId>org.graalvm.sdk</groupId><artifactId>graal-sdk</artifactId><version>${g}</version></dependency>
                    <dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>24.2.1</version><classifier>linux</classifier></dependency>
                  </dependencies>
                """);
        rewriteRun(pomXml(before));
    }

    @Test
    void migratesGradleCoordinatesAndExistingCompanions() {
        rewriteRun(buildGradle(
                """
                dependencies {
                    implementation('org.graalvm.js:js:24.2.1')
                    implementation('org.graalvm.sdk:graal-sdk:22.3.0')
                    runtimeOnly('org.graalvm.js:js-scriptengine:22.3.1')
                }
                """,
                """
                dependencies {
                    implementation('org.graalvm.polyglot:js:24.2.1')
                    implementation('org.graalvm.polyglot:polyglot:24.2.1')
                    runtimeOnly('org.graalvm.js:js-scriptengine:24.2.1')
                }
                """));
    }

    @Test
    void migratesGroovyMapNotation() {
        rewriteRun(buildGradle(
                "dependencies { implementation group: 'org.graalvm.js', name: 'js', version: '24.2.1' }",
                "dependencies { implementation group: 'org.graalvm.polyglot', name: 'js', version: '24.2.1' }"));
    }

    @Test
    void migratesKotlinCoordinate() {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.graalvm.js:js:24.2.1\") }",
                "dependencies { implementation(\"org.graalvm.polyglot:js:24.2.1\") }"));
    }

    @Test
    void protectsNestedAndVariantGradleCoordinates() {
        String before = """
                subprojects { dependencies { implementation('org.graalvm.js:js:24.2.1') } }
                dependencies {
                    custom.implementation('org.graalvm.js:js:24.2.1')
                    implementation group: 'org.graalvm.js', name: 'js', version: '24.2.1', ext: 'jar'
                }
                """;
        rewriteRun(buildGradle(before));
    }

    @Test
    void layoutRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradleKts("dependencies { implementation(\"org.graalvm.js:js:24.2.1\") }",
                        "dependencies { implementation(\"org.graalvm.polyglot:js:24.2.1\") }"));
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>app</artifactId>" +
               "<version>1</version>" + body + "</project>";
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        for (int index = text.indexOf(token); index >= 0; index = text.indexOf(token, index + token.length())) count++;
        return count;
    }
}
