package com.huawei.clouds.openrewrite.okhttp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeOkHttpTest implements RewriteTest {
    private static final String UPGRADE_RECIPE =
            "com.huawei.clouds.openrewrite.okhttp.UpgradeOkHttpTo5_3_0";
    private static final String MAVEN_JVM_RECIPE =
            "com.huawei.clouds.openrewrite.okhttp.MigrateMavenJvmOkHttpTo5_3_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE_RECIPE));
    }

    @ParameterizedTest(name = "upgrades workbook-visible source {0}")
    @ValueSource(strings = {
            "3.14.4", "3.14.9", "4.8.0", "4.9.1", "4.9.2",
            "4.9.3", "4.10.0", "4.11.0", "5.0.0-alpha.11"
    })
    void upgradesEveryWorkbookSourceVersion(String version) {
        rewriteRun(pomXml(
                directPom(version),
                directPom("5.3.0")
        ));
    }

    @Test
    void sourceVersionWhitelistExactlyMatchesWorkbookCells() {
        assertEquals(Set.of(
                        "3.14.4", "3.14.9", "4.8.0", "4.9.1", "4.9.2",
                        "4.9.3", "4.10.0", "4.11.0", "5.0.0-alpha.11"),
                UpgradeSelectedOkHttpDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesApacheFlinkStyleSharedPropertyAndManagedDependency() {
        // Reduced from Apache Flink at f16dd6e7:
        // https://github.com/apache/flink/blob/f16dd6e7c230ce92fd8c87c3122ba5e188416a02/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.apache.flink</groupId>
                  <artifactId>flink-parent</artifactId>
                  <version>2.3-SNAPSHOT</version>
                  <properties><okhttp.version>3.14.9</okhttp.version></properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.squareup.okhttp3</groupId>
                        <artifactId>okhttp</artifactId>
                        <version>${okhttp.version}</version>
                      </dependency>
                      <dependency>
                        <groupId>com.squareup.okhttp3</groupId>
                        <artifactId>logging-interceptor</artifactId>
                        <version>${okhttp.version}</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.apache.flink</groupId>
                  <artifactId>flink-parent</artifactId>
                  <version>2.3-SNAPSHOT</version>
                  <properties><okhttp.version>5.3.0</okhttp.version></properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.squareup.okhttp3</groupId>
                        <artifactId>okhttp</artifactId>
                        <version>${okhttp.version}</version>
                      </dependency>
                      <dependency>
                        <groupId>com.squareup.okhttp3</groupId>
                        <artifactId>logging-interceptor</artifactId>
                        <version>${okhttp.version}</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesCimStyleDirectMavenDependency() {
        // Reduced from crossoverJie/cim at 8863d9f:
        // https://github.com/crossoverJie/cim/blob/8863d9f6d76d0ad55a27bd0d6f05d6476937f0e8/pom.xml
        rewriteRun(pomXml(
                directPom("4.9.2"),
                directPom("5.3.0")
        ));
    }

    @Test
    void upgradesEveryOtherStableVersionListedInTheSpreadsheet() {
        rewriteRun(
                versionedPom("4.8.0", "4-8-pom.xml"),
                versionedPom("4.9.1", "4-9-1-pom.xml"),
                versionedPom("4.9.3", "4-9-3-pom.xml"),
                versionedPom("4.10.0", "4-10-pom.xml"),
                versionedPom("4.11.0", "4-11-pom.xml")
        );
    }

    @Test
    void upgradesAlphaVersionListedInTheSpreadsheet() {
        rewriteRun(pomXml(
                directPom("5.0.0-alpha.11"),
                directPom("5.3.0")
        ));
    }

    @Test
    void upgradesSyntheaStyleGroovyGradleDependency() {
        // Reduced from synthetichealth/synthea at 3ffe7bc:
        // https://github.com/synthetichealth/synthea/blob/3ffe7bc7f13990b3b13dcebd3bcc3586042b80c3/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'com.squareup.okhttp3:okhttp:4.10.0'
                    testImplementation 'com.squareup.okhttp3:mockwebserver:4.10.0'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'com.squareup.okhttp3:okhttp:5.3.0'
                    testImplementation 'com.squareup.okhttp3:mockwebserver:4.10.0'
                }
                """
        ));
    }

    @Test
    void upgradesDirectKotlinGradleDependencyWithoutAProjectModel() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    implementation("com.squareup.okhttp3:okhttp:4.9.3")
                }
                """,
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    implementation("com.squareup.okhttp3:okhttp:5.3.0")
                }
                """
        ));
    }

    @Test
    void upgradesGroovyGradleMapNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation group: 'com.squareup.okhttp3', name: 'okhttp', version: '3.14.9'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation group: 'com.squareup.okhttp3', name: 'okhttp', version: '5.3.0'
                }
                """
        ));
    }

    @Test
    void leavesGradleVersionPropertyForItsOwner() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                ext { okhttpVersion = '4.11.0' }
                dependencies {
                    implementation "com.squareup.okhttp3:okhttp:${okhttpVersion}"
                }
                """
        ));
    }

    @Test
    void upgradesImportedMavenBomProperty() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom-app</artifactId><version>1</version>
                  <properties><okhttp3.version>4.11.0</okhttp3.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-bom</artifactId><version>${okhttp3.version}</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency></dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom-app</artifactId><version>1</version>
                  <properties><okhttp3.version>5.3.0</okhttp3.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-bom</artifactId><version>${okhttp3.version}</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesLiteralMavenBomVersion() {
        rewriteRun(pomXml(
                bomPom("4.9.3"),
                bomPom("5.3.0")
        ));
    }

    @Test
    void upgradesGradlePlatformBom() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation platform('com.squareup.okhttp3:okhttp-bom:4.11.0')
                    implementation 'com.squareup.okhttp3:okhttp'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation platform('com.squareup.okhttp3:okhttp-bom:5.3.0')
                    implementation 'com.squareup.okhttp3:okhttp'
                }
                """
        ));
    }

    @Test
    void leavesTargetVersionUntouched() {
        rewriteRun(pomXml(directPom("5.3.0")));
    }

    @Test
    void leavesApacheBookKeeperStyleLaterBomUntouched() {
        // Reduced from Apache BookKeeper at 68cc8dc:
        // https://github.com/apache/bookkeeper/blob/68cc8dcbd1e8a7e95c68e70d183e178ad6d84ede/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.apache.bookkeeper</groupId><artifactId>bookkeeper</artifactId><version>4.18.0-SNAPSHOT</version>
                  <properties><okhttp3.version>5.3.1</okhttp3.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-bom</artifactId><version>${okhttp3.version}</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void leavesLaterCoreAndBomVersionsUntouched() {
        rewriteRun(
                pomXml(directPom("5.4.0"), spec -> spec.path("later-core-pom.xml")),
                pomXml(bomPom("5.4.0"), spec -> spec.path("later-bom-pom.xml"))
        );
    }

    @Test
    void leavesCompanionArtifactsUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>companions</artifactId><version>1</version><dependencies>
                  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>logging-interceptor</artifactId><version>4.11.0</version></dependency>
                  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>mockwebserver</artifactId><version>4.11.0</version></dependency>
                  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-tls</artifactId><version>4.11.0</version></dependency>
                  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-urlconnection</artifactId><version>4.11.0</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void leavesSameArtifactFromAnotherGroupUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unrelated</artifactId><version>1</version><dependencies>
                  <dependency><groupId>com.squareup.okhttp</groupId><artifactId>okhttp</artifactId><version>2.7.5</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void upgradesLocallyManagedVersionForVersionlessDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>locally-managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.9.3</version></dependency></dependencies></dependencyManagement>
                  <dependencies>
                  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>locally-managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>5.3.0</version></dependency></dependencies></dependencyManagement>
                  <dependencies>
                  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void leavesClassifiedDependencyUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>classified</artifactId><version>1</version><dependencies><dependency>
                  <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.9.1</version><classifier>tests</classifier><scope>test</scope>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void requiresOwnedMavenAndGradleDependencyNodesAndStandardVariants() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-owner</artifactId><version>1</version>
                          <build><plugins><plugin><groupId>example</groupId><artifactId>tool</artifactId><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version>
                          </dependency></dependencies></plugin></plugins></build>
                        </project>
                        """,
                        source -> source.path("plugin/pom.xml")
                ),
                xml(
                        "<catalog><dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version></dependency></dependencies></catalog>",
                        source -> source.path("catalog.xml")
                ),
                xml(
                        "<root><project><properties><okhttp.version>4.11.0</okhttp.version></properties><dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>${okhttp.version}</version></dependency></dependencies></project></root>",
                        source -> source.path("nested/pom.xml")
                ),
                buildGradle(
                        """
                        implementation 'com.squareup.okhttp3:okhttp:4.11.0'
                        dependencies {
                            constraints { implementation 'com.squareup.okhttp3:okhttp:4.11.0' }
                            implementation group: 'com.squareup.okhttp3', name: 'okhttp', version: '4.11.0', classifier: 'tests'
                            implementation group: 'com.squareup.okhttp3', name: 'okhttp', version: '4.11.0', ext: 'aar'
                        }
                        fake { dependencies { implementation 'com.squareup.okhttp3:okhttp:4.11.0' } }
                        """,
                        source -> source.path("strict.gradle")
                ),
                buildGradleKts(
                        "implementation(\"com.squareup.okhttp3:okhttp:4.11.0\")",
                        source -> source.path("outside.gradle.kts")
                ),
                pomXml(
                        directPom("4.11.0"),
                        source -> source.path("target/generated-poms/pom.xml")
                ),
                buildGradle(
                        "dependencies { implementation 'com.squareup.okhttp3:okhttp:4.11.0' }",
                        source -> source.path("install/templates/build.gradle")
                )
        );
    }

    @Test
    void upgradesProfileLocalPropertyButProtectsDuplicateAndSharedProperties() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiles</artifactId><version>1</version>
                          <profiles><profile><id>jdk</id><properties><okhttp.version>4.9.3</okhttp.version></properties><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>${okhttp.version}</version>
                          </dependency></dependencies></profile></profiles>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiles</artifactId><version>1</version>
                          <profiles><profile><id>jdk</id><properties><okhttp.version>5.3.0</okhttp.version></properties><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>${okhttp.version}</version>
                          </dependency></dependencies></profile></profiles>
                        </project>
                        """,
                        source -> source.path("profile/pom.xml")
                ),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicates</artifactId><version>1</version>
                          <properties><okhttp.version>4.9.3</okhttp.version></properties>
                          <profiles><profile><id>duplicate</id><properties><okhttp.version>4.9.3</okhttp.version></properties></profile></profiles>
                          <dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>${okhttp.version}</version></dependency></dependencies>
                        </project>
                        """,
                        source -> source.path("duplicates/pom.xml")
                ),
                pomXml(
                        """
                        <project name="${okhttp.version}"><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                          <properties><okhttp.version>4.9.3</okhttp.version></properties>
                          <dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>${okhttp.version}</version></dependency></dependencies>
                        </project>
                        """,
                        source -> source.path("shared/pom.xml")
                ),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>wrong-scope</artifactId><version>1</version>
                          <profiles><profile><id>only-here</id><properties><okhttp.version>4.9.3</okhttp.version></properties></profile></profiles>
                          <dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>${okhttp.version}</version></dependency></dependencies>
                        </project>
                        """, source -> source.path("wrong-scope/pom.xml")
                )
        );
    }

    @Test
    void protectsInvalidBomShapesAndDynamicVersions() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bad-bom</artifactId><version>1</version><dependencies>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-bom</artifactId><version>4.11.0</version></dependency>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>[4.9,5)</version></dependency>
                        </dependencies></project>
                        """
                ),
                buildGradle(
                        """
                        dependencies {
                            implementation 'com.squareup.okhttp3:okhttp:4.+'
                            implementation platform('com.squareup.okhttp3:okhttp:4.11.0@pom')
                            implementation 'com.squareup.okhttp3:okhttp:4.11.0:tests'
                        }
                        """
                )
        );
    }

    @Test
    void optionalRecipeMigratesDirectMavenJvmCoordinate() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MAVEN_JVM_RECIPE)),
                pomXml(
                        directPom("4.11.0"),
                        directPom("5.3.0").replace("<artifactId>okhttp</artifactId>", "<artifactId>okhttp-jvm</artifactId>")
                )
        );
    }

    @Test
    void optionalRecipeMigratesManagedMavenCoordinateAndProperty() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MAVEN_JVM_RECIPE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-jvm</artifactId><version>1</version>
                          <properties><okhttp.version>3.14.9</okhttp.version></properties>
                          <dependencyManagement><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>${okhttp.version}</version>
                          </dependency></dependencies></dependencyManagement>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-jvm</artifactId><version>1</version>
                          <properties><okhttp.version>5.3.0</okhttp.version></properties>
                          <dependencyManagement><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-jvm</artifactId><version>${okhttp.version}</version>
                          </dependency></dependencies></dependencyManagement>
                        </project>
                        """
                )
        );
    }

    @Test
    void optionalRecipeMigratesVersionlessConsumerOnlyWithLocalManagement() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MAVEN_JVM_RECIPE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>local-jvm</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version>
                          </dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>local-jvm</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-jvm</artifactId><version>5.3.0</version>
                          </dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-jvm</artifactId></dependency></dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void optionalRecipeKeepsProfileLocalManagementInItsOwningProfile() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MAVEN_JVM_RECIPE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-jvm</artifactId><version>1</version><profiles>
                          <profile><id>owned</id><dependencyManagement><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version>
                          </dependency></dependencies></dependencyManagement><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId>
                          </dependency></dependencies></profile>
                          <profile><id>external</id><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId>
                          </dependency></dependencies></profile>
                        </profiles></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-jvm</artifactId><version>1</version><profiles>
                          <profile><id>owned</id><dependencyManagement><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-jvm</artifactId><version>5.3.0</version>
                          </dependency></dependencies></dependencyManagement><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-jvm</artifactId>
                          </dependency></dependencies></profile>
                          <profile><id>external</id><dependencies><!--~~(This OkHttp version is inherited or managed externally; resolve the effective BOM, parent, catalog, constraint, or profile owner and migrate it deliberately to 5.3.0)~~>--><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId>
                          </dependency></dependencies></profile>
                        </profiles></project>
                        """
                )
        );
    }

    @Test
    void optionalRecipeProtectsExternalVersionlessPluginClassifierAndGeneratedPom() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MAVEN_JVM_RECIPE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>external</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency>
                            <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>5.3.0</version><classifier>tests</classifier></dependency>
                          </dependencies>
                          <build><plugins><plugin><groupId>example</groupId><artifactId>tool</artifactId><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>5.3.0</version>
                          </dependency></dependencies></plugin></plugins></build>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>external</artifactId><version>1</version>
                          <dependencies>
                            <!--~~(This OkHttp version is inherited or managed externally; resolve the effective BOM, parent, catalog, constraint, or profile owner and migrate it deliberately to 5.3.0)~~>--><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency>
                            <!--~~(This OkHttp classifier/type/ext or extended coordinate is a nonstandard variant; verify its JVM/Android artifact, capability, and classpath before migrating it manually)~~>--><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>5.3.0</version><classifier>tests</classifier></dependency>
                          </dependencies>
                          <build><plugins><plugin><groupId>example</groupId><artifactId>tool</artifactId><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>5.3.0</version>
                          </dependency></dependencies></plugin></plugins></build>
                        </project>
                        """,
                        source -> source.path("external/pom.xml")
                ),
                pomXml(
                        directPom("4.11.0"),
                        source -> source.path("target/generated-poms/pom.xml")
                ),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-property-only</artifactId><version>1</version>
                          <profiles><profile><id>only-here</id><properties><okhttp.version>5.3.0</okhttp.version></properties></profile></profiles>
                          <dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>${okhttp.version}</version></dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-property-only</artifactId><version>1</version>
                          <profiles><profile><id>only-here</id><properties><okhttp.version>5.3.0</okhttp.version></properties></profile></profiles>
                          <dependencies><dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><!--~~(This OkHttp version is indirect, dynamic, ranged, or property-owned and cannot be selected safely; pin or migrate its exact owner without widening the workbook version whitelist)~~>--><version>${okhttp.version}</version></dependency></dependencies>
                        </project>
                        """, source -> source.path("profile-property-only/pom.xml")
                )
        );
    }

    @Test
    void optionalRecipeUpgradesExistingMavenJvmArtifact() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MAVEN_JVM_RECIPE)),
                pomXml(
                        directPom("5.0.0-alpha.11").replace("<artifactId>okhttp</artifactId>", "<artifactId>okhttp-jvm</artifactId>"),
                        directPom("5.3.0").replace("<artifactId>okhttp</artifactId>", "<artifactId>okhttp-jvm</artifactId>")
                )
        );
    }

    @Test
    void optionalRecipeRetainsGradlePlatformArtifact() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MAVEN_JVM_RECIPE)),
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies { implementation 'com.squareup.okhttp3:okhttp:4.11.0' }
                        """,
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies { implementation 'com.squareup.okhttp3:okhttp:5.3.0' }
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = environment();
        Recipe upgrade = environment.activateRecipes(UPGRADE_RECIPE);
        Recipe mavenJvm = environment.activateRecipes(MAVEN_JVM_RECIPE);

        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> UPGRADE_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MAVEN_JVM_RECIPE.equals(recipe.getName())));
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(mavenJvm.validate().isValid(), () -> mavenJvm.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.okhttp")
                .scanYamlResources()
                .build();
    }

    private static String directPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>okhttp-app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.squareup.okhttp3</groupId>
                      <artifactId>okhttp</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(version);
    }

    private static String bomPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>okhttp-bom-app</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-bom</artifactId><version>%s</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """.formatted(version);
    }

    private static org.openrewrite.test.SourceSpecs versionedPom(String before, String path) {
        return pomXml(directPom(before), directPom("5.3.0"), spec -> spec.path(path + "/pom.xml"));
    }
}
