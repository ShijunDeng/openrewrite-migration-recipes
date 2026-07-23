package com.huawei.clouds.openrewrite.guava;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.migrate.guava.NoGuavaCreateTempDir;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeGuavaTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.guava.UpgradeGuavaTo33_5_0Jre";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.guava.MigrateGuavaTo33_5_0Jre";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "upgrades exact spreadsheet Guava version {0}")
    @ValueSource(strings = {
            "21.0", "29.0-jre", "30.1-jre", "30.1.1-jre", "31.1-jre",
            "32.0.0-jre", "32.0.1-jre", "32.1.0-jre", "32.1.1-android", "32.1.1-jre"
    })
    void upgradesEveryResolvableSpreadsheetSourceVersion(String version) {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pomWithVersion(version), pomWithVersion("33.5.0-jre"))
        );
    }

    @Test
    void upgradesLiteralTwentyOneExactlyAsWrittenInWorkbook() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedGuavaDependency()),
                xml(pomWithVersion("21"), pomWithVersion("33.5.0-jre"), source -> source.path("pom.xml"))
        );
    }

    @ParameterizedTest(name = "leaves unlisted Guava selector {0} unchanged")
    @ValueSource(strings = {
            "20.0", "28.2-jre", "30.0-jre", "31.0.1-jre", "32.1.2-jre", "33.4.8-jre",
            "33.5.0-jre", "34.0.0-jre", "[31,33)", "LATEST", "31.+"
    })
    void rejectsUnlistedTargetNewerRangesAndDynamicVersions(String version) {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedGuavaDependency()),
                xml(pomWithVersion(version), source -> source.path("pom.xml"))
        );
    }

    @Test
    void upgradesExclusiveMavenProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                  <properties><guava.version>31.1-jre</guava.version></properties>
                  <dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>${guava.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                  <properties><guava.version>33.5.0-jre</guava.version></properties>
                  <dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>${guava.version}</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesExclusiveProfileProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>legacy</id><properties><guava.version>31.1-jre</guava.version></properties>
                    <dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>${guava.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>legacy</id><properties><guava.version>33.5.0-jre</guava.version></properties>
                    <dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>${guava.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void leavesDuplicateMavenPropertyDefinitionsUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicates</artifactId><version>1</version>
                  <properties><guava.version>31.1-jre</guava.version></properties>
                  <dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>${guava.version}</version></dependency></dependencies>
                  <profiles><profile><id>other</id><properties><guava.version>30.1-jre</guava.version></properties></profile></profiles>
                </project>
                """));
    }

    @Test
    void leavesMavenPropertySharedWithProjectMetadataUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>${shared.version}</version>
                  <properties><shared.version>31.1-jre</shared.version></properties>
                  <dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>${shared.version}</version></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void leavesMavenPropertySharedWithAnotherDependencyUntouched() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedGuavaDependency()),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                          <properties><shared.version>31.1-jre</shared.version></properties><dependencies>
                            <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>${shared.version}</version></dependency>
                            <dependency><groupId>example</groupId><artifactId>other</artifactId><version>${shared.version}</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.path("pom.xml")
                )
        );
    }

    @Test
    void upgradesLocalDependencyManagementWithoutAddingOverride() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>31.1-jre</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>33.5.0-jre</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesDependencyInsideMavenProfile() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiled</artifactId><version>1</version>
                  <profiles><profile><id>tools</id><dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>30.1-jre</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiled</artifactId><version>1</version>
                  <profiles><profile><id>tools</id><dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>33.5.0-jre</version></dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void upgradesGradleGroovyKotlinAndMapNotations() {
        rewriteRun(
                buildGradle(
                        "plugins { id 'java' }\ndependencies { implementation 'com.google.guava:guava:29.0-jre' }",
                        "plugins { id 'java' }\ndependencies { implementation 'com.google.guava:guava:33.5.0-jre' }"
                ),
                buildGradle(
                        "plugins { id 'java-library' }\ndependencies { compileOnly group: 'com.google.guava', name: 'guava', version: '32.0.1-jre' }",
                        "plugins { id 'java-library' }\ndependencies { compileOnly group: 'com.google.guava', name: 'guava', version: '33.5.0-jre' }",
                        source -> source.path("map/build.gradle")
                ),
                buildGradleKts(
                        "plugins { java }\ndependencies { implementation(\"com.google.guava:guava:32.1.0-jre\") }",
                        "plugins { java }\ndependencies { implementation(\"com.google.guava:guava:33.5.0-jre\") }",
                        source -> source.path("kotlin/build.gradle.kts")
                )
        );
    }

    @Test
    void leavesGradleVariablesRangesAndOtherArtifactsUntouched() {
        rewriteRun(buildGradle("""
                plugins { id 'java' }
                def guavaVersion = '31.1-jre'
                dependencies {
                    implementation "com.google.guava:guava:$guavaVersion"
                    implementation 'com.google.guava:guava:[31,33)'
                    implementation 'com.google.guava:guava-gwt:31.1-jre'
                    implementation 'com.google.guava:failureaccess:1.0.2'
                }
        """));
    }

    @Test
    void requiresRealMavenAndGradleDependencyOwnershipAndStandardVariants() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedGuavaDependency()),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owners</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>31.1-jre</version><classifier>tests</classifier></dependency>
                            <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>31.1-jre</version><type>test-jar</type></dependency>
                          </dependencies>
                          <build><plugins><plugin><artifactId>generator</artifactId><dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>31.1-jre</version>
                          </dependency></dependencies><configuration><fixture><dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>31.1-jre</version>
                          </dependency></dependencies></fixture></configuration></plugin></plugins></build>
                        </project>
                        """,
                        source -> source.path("pom.xml")
                ),
                buildGradle("""
                        plugins { id 'java' }
                        implementation 'com.google.guava:guava:31.1-jre'
                        dependencies {
                          implementation group: 'com.google.guava', name: 'guava', version: '31.1-jre', classifier: 'tests'
                          implementation([group: 'com.google.guava', name: 'guava', version: '31.1-jre', ext: 'zip'])
                        }
                        """)
        );
    }

    @Test
    void ignoresGeneratedBuildDescriptors() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedGuavaDependency()),
                xml(pomWithVersion("31.1-jre"), source -> source.path("target/generated/pom.xml")),
                buildGradle("dependencies { implementation 'com.google.guava:guava:31.1-jre' }",
                        source -> source.path(".idea/generated/build.gradle"))
        );
    }

    @Test
    void xkcodingSpringBootDemoPomShapeUpgrades() {
        // Reduced from xkcoding/spring-boot-demo at 87a142f9604c1a5365b4d24d22c2c11c26a9d5ab:
        // https://github.com/xkcoding/spring-boot-demo/blob/87a142f9604c1a5365b4d24d22c2c11c26a9d5ab/pom.xml
        rewriteRun(pomXml(pomWithVersion("29.0-jre"), pomWithVersion("33.5.0-jre")));
    }

    @Test
    void netflixCommonsGradleShapeUpgrades() {
        // Reduced from Netflix/netflix-commons at 57bb2571c16064708c168039dc7c8e40d76dadcd:
        // https://github.com/Netflix/netflix-commons/blob/57bb2571c16064708c168039dc7c8e40d76dadcd/build.gradle
        rewriteRun(buildGradle(
                "plugins { id 'java-library' }\ndependencies { api 'com.google.guava:guava:31.1-jre' }",
                "plugins { id 'java-library' }\ndependencies { api 'com.google.guava:guava:33.5.0-jre' }"
        ));
    }

    @Test
    void gradleDocumentationSnippetShapeUpgrades() {
        // Reduced from gradle/gradle at a3f10fe284959c1ed1889c6f79f877d778590270:
        // https://github.com/gradle/gradle/blob/a3f10fe284959c1ed1889c6f79f877d778590270/platforms/documentation/docs/src/snippets/unused/plugins/simple/groovy/sub-project-a/build.gradle
        rewriteRun(buildGradle(
                "plugins { id 'java' }\ndependencies { implementation 'com.google.guava:guava:32.1.1-jre' }",
                "plugins { id 'java' }\ndependencies { implementation 'com.google.guava:guava:33.5.0-jre' }"
        ));
    }

    @Test
    void doesNotOverrideExternallyManagedDependency() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>external</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava-bom</artifactId><version>31.1-jre</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void marksOnlyTheAndroidFlavorSwitch() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(
                                "com.huawei.clouds.openrewrite.guava.FindGuavaAndroidFlavorMigration"))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(
                        pomWithVersion("32.1.1-android"),
                        pomWithVersion("32.1.1-android").replace(
                                "<dependency>", "<!--~~(The spreadsheet target switches Guava from android to jre; verify Android minSdk, desugaring, and runtime variants before accepting the upgrade)~~>--><dependency>")
                )
        );
    }

    @Test
    void marksExactGradleAndroidFlavorButNotJreOrOtherArtifacts() {
        rewriteRun(
                spec -> spec.recipe(new FindGuavaAndroidFlavorMigration()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        dependencies {
                            implementation 'com.google.guava:guava:32.1.1-android'
                            implementation 'com.google.guava:guava:32.1.1-jre'
                            implementation 'com.google.guava:guava-gwt:32.1.1-android'
                        }
                        """,
                        """
                        plugins { id 'java' }
                        dependencies {
                            implementation /*~~(The spreadsheet target switches Guava from android to jre; verify Android minSdk, desugaring, and runtime variants before accepting the upgrade)~~>*/'com.google.guava:guava:32.1.1-android'
                            implementation 'com.google.guava:guava:32.1.1-jre'
                            implementation 'com.google.guava:guava-gwt:32.1.1-android'
                        }
                        """
                ),
                buildGradle(
                        "implementation 'com.google.guava:guava:32.1.1-android'",
                        source -> source.path("outside/build.gradle")
                ),
                buildGradle(
                        "dependencies { implementation 'com.google.guava:guava:32.1.1-android' }",
                        source -> source.path("build/generated/build.gradle")
                )
        );
    }

    @Test
    void discoversAndValidatesPublicRecipes() {
        Environment environment = environment();
        assertEquals("3.40.0",
                NoGuavaCreateTempDir.class.getPackage().getImplementationVersion());
        assertTrue(NoGuavaCreateTempDir.class.getProtectionDomain().getCodeSource()
                        .getLocation().toString().contains("rewrite-migrate-java-3.40.0.jar"),
                "Official Guava recipes must come from the pinned binary artifact");
        assertEquals(Set.of(
                "21", "21.0", "29.0-jre", "30.1-jre", "30.1.1-jre", "31.1-jre",
                "32.0.0-jre", "32.0.1-jre", "32.1.0-jre", "32.1.1-android", "32.1.1-jre"
        ), UpgradeSelectedGuavaDependency.SOURCE_VERSIONS);
        for (String name : new String[]{
                UPGRADE, MIGRATE,
                "com.huawei.clouds.openrewrite.guava.MigrateSelectedGuavaSources",
                "com.huawei.clouds.openrewrite.guava.InlineSelectedGuavaMethodsOnJava11",
                "com.huawei.clouds.openrewrite.guava.FindSelectedGuavaMigrationRisks",
                "com.huawei.clouds.openrewrite.guava.FindGuavaAndroidFlavorMigration",
                "com.huawei.clouds.openrewrite.guava.FindGuavaBuildMigrationRisks",
                "org.openrewrite.java.migrate.guava.NoGuavaCreateTempDir",
                "org.openrewrite.java.migrate.guava.NoGuavaDirectExecutor",
                "com.google.guava.InlineGuavaMethods"
        }) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        }
    }

    private static String pomWithVersion(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>guava-app</artifactId><version>1.0.0</version>
                 <dependencies><dependency>
                   <groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>%s</version>
                 </dependency></dependencies>
               </project>
               """.formatted(version);
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath(
                        "com.huawei.clouds.openrewrite.guava",
                        "org.openrewrite.java.migrate.guava")
                .scanYamlResources()
                .build();
    }
}
