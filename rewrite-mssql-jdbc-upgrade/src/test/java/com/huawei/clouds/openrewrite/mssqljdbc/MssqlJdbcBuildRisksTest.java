package com.huawei.clouds.openrewrite.mssqljdbc;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class MssqlJdbcBuildRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.mssqljdbc.FindManualMssqlJdbc13BuildRisks";
    private static final String JAVA =
            "mssql-jdbc 13.2.1.jre11 requires Java 11 or newer; align compiler, toolchain, CI, container and runtime JDKs";
    private static final String OPTIONAL =
            "SQL Server JDBC optional runtime dependency detected; align it with the selected Entra, native authentication, Always Encrypted or vector feature and verify dependency convergence";
    private static final String DRIVER_OWNER =
            "This mssql-jdbc version is externally managed, dynamic, ranged, unresolved, or outside the workbook selection; migrate its actual owner and verify the resolved artifact is 13.2.1.jre11";
    private static final String DRIVER_VARIANT =
            "This classified or explicitly typed mssql-jdbc artifact is outside deterministic runtime upgrade scope; verify that 13.2.1.jre11 publishes the same artifact shape before migrating it";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE));
    }

    @Test
    void marksRootJavaPropertyAndOwnedCompilerPluginConfiguration() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>java8</artifactId><version>1</version>
                  <properties><java.version>1.8</java.version></properties>
                  <dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency></dependencies>
                  <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><release>10</release></configuration></plugin></plugins></build>
                  <configuration><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><release>8</release></configuration></plugin></plugins></configuration>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>java8</artifactId><version>1</version>
                  <properties><!--~~(%s)~~>--><java.version>1.8</java.version></properties>
                  <dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency></dependencies>
                  <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><!--~~(%s)~~>--><release>10</release></configuration></plugin></plugins></build>
                  <configuration><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><release>8</release></configuration></plugin></plugins></configuration>
                </project>
                """.formatted(JAVA, JAVA)
        ));
    }

    @Test
    void ignoresJavaAndOptionalDependenciesWithoutAnOwnedDriver() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unrelated</artifactId><version>1</version>
                  <properties><maven.compiler.release>8</maven.compiler.release></properties><dependencies>
                    <dependency><groupId>com.azure</groupId><artifactId>azure-identity</artifactId><version>1.16.2</version></dependency>
                    <dependency><groupId>com.google.code.gson</groupId><artifactId>gson</artifactId><version>2.13.1</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void ignoresPluginDependenciesButMarksAmbiguousDriverOwners() {
        rewriteRun(
                pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-only</artifactId><version>1</version>
                  <properties><java.version>8</java.version></properties>
                  <dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version><classifier>sources</classifier></dependency></dependencies>
                  <build><plugins><plugin><artifactId>example-plugin</artifactId><dependencies>
                    <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency>
                    <dependency><groupId>com.azure</groupId><artifactId>azure-identity</artifactId><version>1.16.2</version></dependency>
                  </dependencies></plugin></plugins></build>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-only</artifactId><version>1</version>
                  <properties><java.version>8</java.version></properties>
                  <dependencies><!--~~(%s)~~>--><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version><classifier>sources</classifier></dependency></dependencies>
                  <build><plugins><plugin><artifactId>example-plugin</artifactId><dependencies>
                    <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency>
                    <dependency><groupId>com.azure</groupId><artifactId>azure-identity</artifactId><version>1.16.2</version></dependency>
                  </dependencies></plugin></plugins></build>
                </project>
                """.formatted(DRIVER_VARIANT)),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>external</artifactId><version>1</version><dependencies>
                          <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>external</artifactId><version>1</version><dependencies>
                          <!--~~(%s)~~>--><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId></dependency>
                        </dependencies></project>
                        """.formatted(DRIVER_OWNER),
                        source -> source.path("external/pom.xml")),
                buildGradle(
                        """
                        dependencies {
                          implementation 'com.microsoft.sqlserver:mssql-jdbc:10.+'
                          runtimeOnly 'com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11:sources'
                        }
                        """,
                        """
                        dependencies {
                          implementation /*~~(%s)~~>*/'com.microsoft.sqlserver:mssql-jdbc:10.+'
                          runtimeOnly /*~~(%s)~~>*/'com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11:sources'
                        }
                        """.formatted(DRIVER_OWNER, DRIVER_VARIANT),
                        source -> source.path("ambiguous.gradle"))
        );
    }

    @Test
    void marksExactMavenOptionalRuntimeFamilies() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>optional</artifactId><version>1</version><dependencies>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency>
                  <dependency><groupId>com.microsoft.azure</groupId><artifactId>msal4j</artifactId><version>1.23.1</version></dependency>
                  <dependency><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId><version>1.81</version></dependency>
                  <dependency><groupId>org.bouncycastle</groupId><artifactId>bcpkix-jdk18on</artifactId><version>1.81</version></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>optional</artifactId><version>1</version><dependencies>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency>
                  <!--~~(%s)~~>--><dependency><groupId>com.microsoft.azure</groupId><artifactId>msal4j</artifactId><version>1.23.1</version></dependency>
                  <!--~~(%s)~~>--><dependency><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId><version>1.81</version></dependency>
                  <dependency><groupId>org.bouncycastle</groupId><artifactId>bcpkix-jdk18on</artifactId><version>1.81</version></dependency>
                </dependencies></project>
                """.formatted(OPTIONAL, OPTIONAL)
        ));
    }

    @Test
    void marksGroovyJavaBaselinesAndExactOptionalCoordinates() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = '10'
                java { toolchain { languageVersion = JavaLanguageVersion.of(8) } }
                dependencies {
                  implementation 'com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11'
                  runtimeOnly 'com.azure:azure-identity:1.16.2'
                }
                """,
                """
                plugins { id 'java' }
                sourceCompatibility = /*~~(%s)~~>*/JavaVersion.VERSION_1_8
                targetCompatibility = /*~~(%s)~~>*/'10'
                java { toolchain { languageVersion = /*~~(%s)~~>*/JavaLanguageVersion.of(8) } }
                dependencies {
                  implementation 'com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11'
                  runtimeOnly /*~~(%s)~~>*/'com.azure:azure-identity:1.16.2'
                }
                """.formatted(JAVA, JAVA, JAVA, OPTIONAL)
        ));
    }

    @Test
    void marksKotlinToolchainAndSourceCompatibility() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                java {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    toolchain { languageVersion = JavaLanguageVersion.of(10) }
                }
                dependencies {
                    implementation("com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11")
                    runtimeOnly("com.google.code.gson:gson:2.13.1")
                }
                """,
                """
                plugins { java }
                java {
                    sourceCompatibility = /*~~(%s)~~>*/JavaVersion.VERSION_1_8
                    toolchain { languageVersion = /*~~(%s)~~>*/JavaLanguageVersion.of(10) }
                }
                dependencies {
                    implementation("com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11")
                    runtimeOnly(/*~~(%s)~~>*/"com.google.code.gson:gson:2.13.1")
                }
                """.formatted(JAVA, JAVA, OPTIONAL)
        ));
    }

    @Test
    void ignoresNestedAndBuildscriptGradleLookalikes() {
        rewriteRun(buildGradle(
                """
                sourceCompatibility = JavaVersion.VERSION_1_8
                buildscript { dependencies { implementation 'com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11' } }
                dependencies { generated { implementation 'com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11' } }
                dependencies { runtimeOnly 'com.microsoft.sqlserver:mssql-jdbc_auth:13.2.1.x64' }
                """
        ));
    }

    @Test
    void skipsGeneratedBuildFiles() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                sourceCompatibility = JavaVersion.VERSION_1_8
                dependencies { implementation 'com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11' }
                """,
                source -> source.path("build/generated/build.gradle")
        ));
    }

    @Test
    void buildMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>idempotent</artifactId><version>1</version>
                          <properties><java.version>8</java.version></properties><dependencies><dependency>
                            <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version>
                          </dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>idempotent</artifactId><version>1</version>
                          <properties><!--~~(%s)~~>--><java.version>8</java.version></properties><dependencies><dependency>
                            <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version>
                          </dependency></dependencies>
                        </project>
                        """.formatted(JAVA)
                )
        );
    }
}
