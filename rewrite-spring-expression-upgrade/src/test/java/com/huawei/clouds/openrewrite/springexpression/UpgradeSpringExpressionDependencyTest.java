package com.huawei.clouds.openrewrite.springexpression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeSpringExpressionDependencyTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springexpression.UpgradeSpringExpressionTo6_2_19";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.springexpression.MigrateSpringExpressionTo6_2_19";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(UPGRADE));
    }

    @ParameterizedTest(name = "Maven upgrades exact source {0}")
    @ValueSource(strings = {
            "5.2.5.RELEASE",
            "5.3.20", "5.3.21", "5.3.27", "5.3.32", "5.3.33", "5.3.34", "5.3.39",
            "6.1.14",
            "6.2.0", "6.2.7", "6.2.8", "6.2.10", "6.2.11", "6.2.12", "6.2.17", "6.2.18"
    })
    void upgradesEverySelectedMavenVersion(String version) {
        rewriteRun(xml(pom(version), pom("6.2.19"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "Groovy upgrades exact source {0}")
    @ValueSource(strings = {
            "5.2.5.RELEASE",
            "5.3.20", "5.3.21", "5.3.27", "5.3.32", "5.3.33", "5.3.34", "5.3.39",
            "6.1.14",
            "6.2.0", "6.2.7", "6.2.8", "6.2.10", "6.2.11", "6.2.12", "6.2.17", "6.2.18"
    })
    void upgradesEverySelectedGroovyVersion(String version) {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.springframework:spring-expression:" + version + "' }",
                "dependencies { implementation 'org.springframework:spring-expression:6.2.19' }"));
    }

    @ParameterizedTest(name = "Kotlin upgrades exact source {0}")
    @ValueSource(strings = {
            "5.2.5.RELEASE",
            "5.3.20", "5.3.21", "5.3.27", "5.3.32", "5.3.33", "5.3.34", "5.3.39",
            "6.1.14",
            "6.2.0", "6.2.7", "6.2.8", "6.2.10", "6.2.11", "6.2.12", "6.2.17", "6.2.18"
    })
    void upgradesEverySelectedKotlinVersion(String version) {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.springframework:spring-expression:" + version + "\") }",
                "dependencies { implementation(\"org.springframework:spring-expression:6.2.19\") }"));
    }

    @ParameterizedTest(name = "Preserves fixed non-selected version {0}")
    @ValueSource(strings = {
            "5.2.4.RELEASE", "5.2.6.RELEASE", "5.3.19", "5.3.22", "5.3.38", "5.3.40",
            "6.0.0", "6.1.13", "6.1.15", "6.2.1", "6.2.9", "6.2.13", "6.2.16",
            "6.2.19", "6.2.20", "6.3.0", "7.0.0", "7.1.3"
    })
    void preservesEveryVersionOutsideExactSelection(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveRootAndProfileProperties() {
        rewriteRun(
                xml(project("""
                        <properties><spel.version>5.3.39</spel.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("${spel.version}", ""))),
                    project("""
                        <properties><spel.version>6.2.19</spel.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("${spel.version}", ""))),
                    source -> source.path("root/pom.xml")),
                xml(project("""
                        <profiles><profile><id>legacy</id>
                          <properties><spel.version>6.1.14</spel.version></properties>
                          <dependencies>%s</dependencies>
                        </profile></profiles>
                        """.formatted(dependency("${spel.version}", ""))),
                    project("""
                        <profiles><profile><id>legacy</id>
                          <properties><spel.version>6.2.19</spel.version></properties>
                          <dependencies>%s</dependencies>
                        </profile></profiles>
                        """.formatted(dependency("${spel.version}", ""))),
                    source -> source.path("profile/pom.xml"))
        );
    }

    @Test
    void preservesSharedDuplicateMissingRangedAggregatedAndShadowedOwners() {
        rewriteRun(
                xml(project("""
                        <properties><spring.version>5.3.39</spring.version></properties>
                        <dependencies>
                          %s
                          <dependency><groupId>x</groupId><artifactId>other</artifactId><version>${spring.version}</version></dependency>
                        </dependencies>
                        """.formatted(dependency("${spring.version}", ""))),
                    source -> source.path("shared/pom.xml")),
                xml(project("""
                        <properties><spring.version>5.3.39</spring.version><spring.version>6.1.14</spring.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("${spring.version}", ""))),
                    source -> source.path("duplicate/pom.xml")),
                xml(project("<dependencies>" +
                            dependency("${missing}", "") + dependency("[5.3,6.3)", "") +
                            dependency("5.3.39,6.1.14", "") + "</dependencies>"),
                    source -> source.path("unowned/pom.xml")),
                xml(project("""
                        <properties><spring.version>5.3.39</spring.version></properties>
                        <dependencies>%s</dependencies>
                        <profiles><profile><id>p</id><properties><spring.version>6.1.14</spring.version></properties></profile></profiles>
                        """.formatted(dependency("${spring.version}", ""))),
                    source -> source.path("shadowed/pom.xml"))
        );
    }

    @Test
    void upgradesGroovyMapAndPreservesMetadata() {
        rewriteRun(buildGradle(
                """
                dependencies {
                  runtimeOnly group: 'org.springframework', name: 'spring-expression', version: '6.2.18'
                  testImplementation('org.springframework:spring-expression:5.3.34') { transitive = false }
                }
                """,
                """
                dependencies {
                  runtimeOnly group: 'org.springframework', name: 'spring-expression', version: '6.2.19'
                  testImplementation('org.springframework:spring-expression:6.2.19') { transitive = false }
                }
                """));
    }

    @Test
    void upgradesKotlinNamedArgumentsAndPreservesVariantNamedArguments() {
        rewriteRun(
                buildGradleKts(
                        """
                        dependencies {
                          implementation(
                            group = "org.springframework",
                            name = "spring-expression",
                            version = "6.2.17"
                          )
                        }
                        """,
                        """
                        dependencies {
                          implementation(
                            group = "org.springframework",
                            name = "spring-expression",
                            version = "6.2.19"
                          )
                        }
                        """,
                        source -> source.path("named.gradle.kts")),
                buildGradleKts(
                        """
                        dependencies {
                          implementation(
                            group = "org.springframework",
                            name = "spring-expression",
                            version = "6.2.17",
                            classifier = "sources"
                          )
                        }
                        """,
                        source -> source.path("variant.gradle.kts"))
        );
    }

    @Test
    void preservesVariantsCatalogsInterpolationNestedLookalikesAndGeneratedFiles() {
        rewriteRun(
                xml(project("<dependencies>" +
                            dependency("5.3.39", "<classifier>sources</classifier>") +
                            dependency("6.1.14", "<type>test-jar</type>") +
                            "<dependency><groupId>x</groupId><artifactId>spring-expression</artifactId><version>5.3.39</version></dependency>" +
                            "</dependencies>"), source -> source.path("pom.xml")),
                xml("<root>" + pom("5.3.39") + "</root>", source -> source.path("nested/pom.xml")),
                xml(pom("5.3.39"), source -> source.path("target/generated/pom.xml")),
                buildGradle("""
                        def springVersion = '5.3.39'
                        dependencies {
                          implementation "org.springframework:spring-expression:${springVersion}"
                          implementation libs.spring.expression
                          implementation 'org.springframework:spring-expression:5.3.39:sources'
                          implementation 'org.springframework:spring-expression:5.3.39@zip'
                          implementation group: 'org.springframework', name: 'spring-expression',
                              version: '5.3.39', classifier: 'sources'
                        }
                        """),
                buildGradleKts("""
                        val springVersion = "5.3.39"
                        dependencies {
                          implementation("org.springframework:spring-expression:$springVersion")
                          implementation(libs.spring.expression)
                        }
                        """),
                buildGradle(
                        "dependencies { implementation 'org.springframework:spring-expression:5.3.39' }",
                        source -> source.path("build/generated/build.gradle"))
        );
    }

    @Test
    void isIdempotentAndPublishesRecipesInOrder() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("6.2.18"), pom("6.2.19"), source -> source.path("pom.xml")));

        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        String[] names = {
                UPGRADE,
                "com.huawei.clouds.openrewrite.springexpression.ConfigureSpringExpression6Build",
                "com.huawei.clouds.openrewrite.springexpression.FindSpringExpression6_2Risks",
                "com.huawei.clouds.openrewrite.springexpression.PreservePre6_2_19UnlimitedSpelOperations",
                MIGRATE
        };
        for (String name : names) assertEquals(name, environment.activateRecipes(name).getName());
        Recipe aggregate = environment.activateRecipes(MIGRATE);
        assertEquals(3, aggregate.getRecipeList().size());
        assertEquals(UPGRADE, aggregate.getRecipeList().get(0).getName());
        assertTrue(aggregate.validate().isValid(), aggregate.validate().toString());
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static String pom(String version) {
        return project("<dependencies>" + dependency(version, "") + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dependency(String version, String metadata) {
        return "<dependency><groupId>org.springframework</groupId><artifactId>spring-expression</artifactId>" +
               "<version>" + version + "</version>" + metadata + "</dependency>";
    }
}
