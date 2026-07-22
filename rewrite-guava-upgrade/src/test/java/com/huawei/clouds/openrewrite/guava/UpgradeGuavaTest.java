package com.huawei.clouds.openrewrite.guava;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeGuavaTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.guava.UpgradeGuavaTo33_5_0Jre";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.guava.MigrateGuavaTo33_5_0Jre";
    private static final String[] LISTED_VERSIONS = {
            "21.0", "29.0-jre", "30.1-jre", "30.1.1-jre", "31.1-jre",
            "32.0.0-jre", "32.0.1-jre", "32.1.0-jre", "32.1.1-android", "32.1.1-jre"
    };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @Test
    void upgradesEverySpreadsheetSourceVersionAndIsIdempotent() {
        for (String version : LISTED_VERSIONS) {
            rewriteRun(
                    spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                    pomXml(pomWithVersion(version), pomWithVersion("33.5.0-jre"))
            );
        }
    }

    @Test
    void upgradesLocalManagedVersionWithoutAddingAnOverride() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>managed-guava-app</artifactId>
                          <version>1.0.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>com.google.guava</groupId>
                                <artifactId>guava</artifactId>
                                <version>31.1-jre</version>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>com.google.guava</groupId>
                              <artifactId>guava</artifactId>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>managed-guava-app</artifactId>
                          <version>1.0.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>com.google.guava</groupId>
                                <artifactId>guava</artifactId>
                                <version>33.5.0-jre</version>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>com.google.guava</groupId>
                              <artifactId>guava</artifactId>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void upgradesGradleStringNotation() {
        rewriteRun(
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies { implementation 'com.google.guava:guava:29.0-jre' }
                        """,
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies { implementation 'com.google.guava:guava:33.5.0-jre' }
                        """
                )
        );
    }

    @Test
    void leavesUnlistedAndTargetVersionsUnchanged() {
        rewriteRun(
                pomXml(pomWithVersion("28.2-jre")),
                pomXml(pomWithVersion("33.5.0-jre"))
        );
    }

    @Test
    void doesNotOverrideExternallyManagedDependency() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>externally-managed</artifactId>
                          <version>1.0.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>com.google.guava</groupId>
                                <artifactId>guava-bom</artifactId>
                                <version>31.1-jre</version>
                                <type>pom</type>
                                <scope>import</scope>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>com.google.guava</groupId>
                              <artifactId>guava</artifactId>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void marksOnlyTheAndroidFlavorSwitch() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(
                        "com.huawei.clouds.openrewrite.guava.FindGuavaAndroidFlavorMigration")),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>android-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.google.guava</groupId>
                              <artifactId>guava</artifactId>
                              <version>32.1.1-android</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>android-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <!--~~>--><dependency>
                              <groupId>com.google.guava</groupId>
                              <artifactId>guava</artifactId>
                              <version>32.1.1-android</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesPublicRecipes() {
        Environment environment = environment();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);

        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> UPGRADE.equals(candidate.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> MIGRATE.equals(candidate.getName())));
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
    }

    private static String pomWithVersion(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId>
                 <artifactId>guava-app</artifactId>
                 <version>1.0.0</version>
                 <dependencies>
                   <dependency>
                     <groupId>com.google.guava</groupId>
                     <artifactId>guava</artifactId>
                     <version>%s</version>
                   </dependency>
                 </dependencies>
               </project>
               """.formatted(version);
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.guava")
                .scanYamlResources()
                .build();
    }
}
