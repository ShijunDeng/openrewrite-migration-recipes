package com.huawei.clouds.openrewrite.flyway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

class UpgradeFlywayCoreTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.flyway.UpgradeFlywayCoreDependencyTo11_14_1";
    private static final String PLUGIN_RECIPE =
            "com.huawei.clouds.openrewrite.flyway.UpgradeFlywayBuildPluginsTo11_14_1";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.flyway.MigrateFlywayTo11_14_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE))
                .parser(JavaParser.fromJavaVersion().classpath("flyway-core"));
    }

    @Test
    void sourceSetMatchesTheVisibleSpreadsheetRowsExactly() {
        assertEquals(java.util.Set.of(
                "5.2.1", "7.1.1", "7.8.2", "7.11.1", "7.15.0",
                "8.5.13", "9.16.3", "9.19.4", "9.20.0"), FlywayVersions.SOURCES);
        assertEquals("11.14.1", FlywayVersions.TARGET);
    }

    @ParameterizedTest(name = "upgrades spreadsheet version {0}")
    @ValueSource(strings = {
            "5.2.1", "7.1.1", "7.11.1", "7.15.0", "7.8.2",
            "8.5.13", "9.16.3", "9.19.4", "9.20.0"
    })
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>flyway-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.flywaydb</groupId>
                      <artifactId>flyway-core</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(oldVersion),
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>flyway-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.flywaydb</groupId>
                      <artifactId>flyway-core</artifactId>
                      <version>11.14.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void rejectsNinjaStyleUnlistedManagedDependency() {
        // Reduced from ninjaframework/ninja at f7b39ce:
        // https://github.com/ninjaframework/ninja/blob/f7b39ce103e547595585276857c3fc77d1e6f4f0/pom.xml#L713-L718
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.ninjaframework</groupId>
                  <artifactId>ninja</artifactId>
                  <version>1</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.flywaydb</groupId>
                        <artifactId>flyway-core</artifactId>
                        <version>8.2.2</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesSharedMavenVersionProperty() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>property-app</artifactId><version>1</version>
                  <properties><flyway.version>9.19.4</flyway.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>${flyway.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>property-app</artifactId><version>1</version>
                  <properties><flyway.version>11.14.1</flyway.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>${flyway.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesGroovyGradleDependency() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.flywaydb:flyway-core:7.15.0'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.flywaydb:flyway-core:11.14.1'
                }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly group: 'org.flywaydb', name: 'flyway-core', version: '8.5.13'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly group: 'org.flywaydb', name: 'flyway-core', version: '11.14.1'
                }
                """
        ));
    }

    @Test
    void upgradesKotlinGradleLiteralWithoutSemanticModel() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    implementation("org.flywaydb:flyway-core:9.20.0")
                }
                """,
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    implementation("org.flywaydb:flyway-core:11.14.1")
                }
                """
        ));
    }

    @Test
    void leavesSpringBootManagedDependencyWithoutVersionUntouched() {
        // Reduced from Testcontainers' Spring Boot quickstart at c2fa0b2. The Spring Boot
        // platform owns this version, so the narrow recipe deliberately does not force one.
        // https://github.com/testcontainers/testcontainers-java-spring-boot-quickstart/blob/c2fa0b2287a97026e153c9d9ee3aab5b7e96ba02/build.gradle
        rewriteRun(buildGradle(
                """
                plugins {
                    id 'org.springframework.boot' version '3.1.3'
                    id 'io.spring.dependency-management' version '1.1.3'
                    id 'java'
                }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
                    implementation 'org.flywaydb:flyway-core'
                    runtimeOnly 'org.postgresql:postgresql'
                }
                """
        ));
    }

    @Test
    void leavesMavenManagedDependencyWithoutVersionUntouched() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.1.3</version>
                  </parent>
                  <groupId>example</groupId><artifactId>boot-app</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesTargetAndLaterCoreVersionsUntouched() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>target</artifactId><version>1</version><dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>11.14.1</version></dependency></dependencies></project>
                        """
                ),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>later</artifactId><version>1</version><dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>12.1.0</version></dependency></dependencies></project>
                        """,
                        spec -> spec.path("later-pom.xml")
                ),
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies { implementation 'org.flywaydb:flyway-core:12.0.0' }
                        """,
                        spec -> spec.path("later.gradle")
                )
        );
    }

    @Test
    void leavesDatabaseModulesAndSimilarArtifactsUntouched() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>companions</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId><version>10.20.0</version></dependency>
                    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId><version>10.20.0</version></dependency>
                    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-sqlserver</artifactId><version>10.20.0</version></dependency>
                    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>9.20.0</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesExplicitFlywayMavenPlugin() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PLUGIN_RECIPE)),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-app</artifactId><version>1</version>
                          <build><plugins><plugin>
                            <groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>9.20.0</version>
                          </plugin></plugins></build>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-app</artifactId><version>1</version>
                          <build><plugins><plugin>
                            <groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>11.14.1</version>
                          </plugin></plugins></build>
                        </project>
                        """
                )
        );
    }

    @Test
    void upgradesMavenPluginVersionProperty() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PLUGIN_RECIPE)),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-property</artifactId><version>1</version>
                          <properties><flyway.plugin.version>8.5.13</flyway.plugin.version></properties>
                          <build><plugins><plugin>
                            <groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>${flyway.plugin.version}</version>
                          </plugin></plugins></build>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-property</artifactId><version>1</version>
                          <properties><flyway.plugin.version>11.14.1</flyway.plugin.version></properties>
                          <build><plugins><plugin>
                            <groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>${flyway.plugin.version}</version>
                          </plugin></plugins></build>
                        </project>
                        """
                )
        );
    }

    @Test
    void leavesVersionlessMavenPluginUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PLUGIN_RECIPE)),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>inherited-plugin</artifactId><version>1</version>
                          <build><plugins><plugin>
                            <groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId>
                          </plugin></plugins></build>
                        </project>
                        """
                )
        );
    }

    @Test
    void upgradesExplicitFlywayGradlePlugin() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PLUGIN_RECIPE)),
                buildGradle(
                        """
                        plugins {
                            id 'org.flywaydb.flyway' version '9.20.0'
                            id 'java'
                        }
                        """,
                        """
                        plugins {
                            id 'org.flywaydb.flyway' version '11.14.1'
                            id 'java'
                        }
                        """
                )
        );
    }

    @Test
    void leavesLaterAndRedgateGradlePluginsUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PLUGIN_RECIPE)),
                buildGradle(
                        """
                        plugins {
                            id 'org.flywaydb.flyway' version '12.0.0'
                            id 'com.redgate.flyway' version '11.14.1'
                        }
                        """
                )
        );
    }

    @Test
    void upgradesPropertyUsedByTwoOwnedCoreDeclarations() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>two-core</artifactId><version>1</version>
                  <properties><flyway.version>9.20.0</flyway.version></properties>
                  <dependencyManagement><dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>${flyway.version}</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>${flyway.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>two-core</artifactId><version>1</version>
                  <properties><flyway.version>11.14.1</flyway.version></properties>
                  <dependencyManagement><dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>${flyway.version}</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>${flyway.version}</version></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void preservesProfileShadowedPropertyAndCustomMavenVariants() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shadowed</artifactId><version>1</version>
                  <properties><flyway.version>9.20.0</flyway.version></properties>
                  <profiles><profile><id>legacy</id><properties><flyway.version>7.15.0</flyway.version></properties></profile></profiles>
                  <dependencies>
                    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>${flyway.version}</version></dependency>
                    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>9.20.0</version><classifier>tests</classifier></dependency>
                    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>9.20.0</version><type>zip</type></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void ignoresPluginDependenciesReportingPluginsAndGeneratedBuilds() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owners</artifactId><version>1</version>
                          <build><plugins><plugin><groupId>example</groupId><artifactId>codegen</artifactId><dependencies>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>9.20.0</version></dependency>
                          </dependencies></plugin></plugins></build>
                          <reporting><plugins><plugin><groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>9.20.0</version></plugin></plugins></reporting>
                        </project>
                        """),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>generated</artifactId><version>1</version>
                          <dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>9.20.0</version></dependency></dependencies>
                        </project>
                        """, source -> source.path("target/generated/pom.xml")),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>maven-internal</artifactId><version>1</version>
                          <dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>9.20.0</version></dependency></dependencies>
                        </project>
                        """, source -> source.path(".mvn/generated/pom.xml")),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>nested-owner</artifactId><version>1</version>
                          <build><plugins><plugin><groupId>example</groupId><artifactId>codegen</artifactId><configuration>
                            <project><dependencies>
                              <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>11.14.1</version></dependency>
                              <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.7</version></dependency>
                            </dependencies></project>
                          </configuration></plugin></plugins></build>
                        </project>
                        """, source -> source.path("nested-pom.xml"))
        );
    }

    @Test
    void ignoresFakeGradleDependencyAndPluginDslAndMapVariants() {
        rewriteRun(
                buildGradle("""
                        implementation 'org.flywaydb:flyway-core:9.20.0'
                        dependencies {
                            implementation group: 'org.flywaydb', name: 'flyway-core', version: '9.20.0', classifier: 'tests'
                        }
                        fake { dependencies { implementation 'org.flywaydb:flyway-core:9.20.0' } }
                        fake {
                            plugins { id 'org.flywaydb.flyway' version '9.20.0' }
                        }
                        """),
                buildGradleKts("""
                        implementation("org.flywaydb:flyway-core:9.20.0")
                        fake { plugins { id("org.flywaydb.flyway") version "9.20.0" } }
                        """, source -> source.path("fake.gradle.kts"))
        );
    }

    @Test
    void pluginRecipeIgnoresFakeDslReportingAndGeneratedTrees() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PLUGIN_RECIPE)),
                buildGradle("""
                        fake { plugins { id 'org.flywaydb.flyway' version '9.20.0' } }
                        """),
                buildGradleKts("""
                        fake { plugins { id("org.flywaydb.flyway") version "9.20.0" } }
                        """, source -> source.path("fake.gradle.kts")),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>report</artifactId><version>1</version>
                          <reporting><plugins><plugin><groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>9.20.0</version></plugin></plugins></reporting>
                        </project>
                        """, source -> source.path("report-pom.xml")),
                buildGradle("""
                        plugins { id 'org.flywaydb.flyway' version '9.20.0' }
                        """, source -> source.path("build/generated/build.gradle"))
        );
    }

    @Test
    void pluginRecipePreservesProfileShadowedProperty() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PLUGIN_RECIPE)),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-shadow</artifactId><version>1</version>
                          <properties><flyway.plugin.version>9.20.0</flyway.plugin.version></properties>
                          <profiles><profile><id>legacy</id><properties><flyway.plugin.version>7.15.0</flyway.plugin.version></properties></profile></profiles>
                          <build><plugins><plugin><groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>${flyway.plugin.version}</version></plugin></plugins></build>
                        </project>
                        """));
    }

    @Test
    void comprehensiveRecipeRenamesRemovedReportProperty() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                properties(
                        """
                        flyway.check.reportFilename=reports/flyway.html
                        flyway.cleanDisabled=true
                        """,
                        """
                        flyway.reportFilename=reports/flyway.html
                        flyway.cleanDisabled=true
                        """,
                        spec -> spec.path("flyway.conf")
                )
        );
    }

    @Test
    void leavesRealConfigurationSafetyControlsUntouched() {
        // Reduced from stitchfix/flotilla-os at 11568a7. The recipe must not enable
        // clean or rewrite connection and migration-location behavior.
        // https://github.com/stitchfix/flotilla-os/blob/11568a7acfb10880744dccd48fba5e99db995b55/.migrations/dev.conf
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                properties(
                        """
                        flyway.url=jdbc:postgresql://127.0.0.1:5432/flotilla
                        flyway.user=flotilla
                        flyway.cleanDisabled=true
                        flyway.group=true
                        flyway.locations=filesystem:.migrations
                        """,
                        """
                        ~~(Flyway 11 requires org.flywaydb:flyway-database-postgresql on the relevant application/plugin runtime classpath)~~>flyway.url=jdbc:postgresql://127.0.0.1:5432/flotilla
                        flyway.user=flotilla
                        flyway.cleanDisabled=true
                        flyway.group=true
                        ~~(filesystem locations discover SQL migrations only; add an explicit classpath location if Java migrations must still be discovered)~~>flyway.locations=filesystem:.migrations
                        """,
                        spec -> spec.path("dev.conf")
                )
        );
    }

    @Test
    void preservesCurrentFluentJavaApi() {
        // Reduced from Apache Gobblin at fcfb06b and type-attributed against Flyway 11.14.1:
        // https://github.com/apache/gobblin/blob/fcfb06b41d041cb797622264cf5322296753fdea/gobblin-metastore/src/main/java/org/apache/gobblin/metastore/DatabaseJobHistoryStore.java#L89-L96
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.sql.DataSource;
                        import org.flywaydb.core.Flyway;
                        import org.flywaydb.core.api.MigrationInfoService;

                        class DatabaseHistory {
                            MigrationInfoService inspect(DataSource dataSource) {
                                Flyway flyway = Flyway.configure().dataSource(dataSource).load();
                                flyway.migrate();
                                return flyway.info();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesAllRecipes() {
        Environment environment = environment();
        for (String recipeName : new String[]{DEPENDENCY_RECIPE, PLUGIN_RECIPE, MIGRATION_RECIPE}) {
            Recipe recipe = environment.activateRecipes(recipeName);
            assertTrue(recipe.validate().isValid(), () -> recipeName + " should be valid: " + recipe.validate());
        }
        assertTrue(environment.listRecipeDescriptors().stream()
                .anyMatch(descriptor -> MIGRATION_RECIPE.equals(descriptor.getName())));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }
}
