package com.huawei.clouds.openrewrite.mssqljdbc;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class MssqlJdbcDependencySafetyTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.mssqljdbc.UpgradeMssqlJdbcTo13_2_1Jre11";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE));
    }

    @Test
    void preservesOrdinaryMavenMetadataWhileUpgradingTheRuntimeJar() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>metadata</artifactId><version>1</version><dependencies><dependency>
                  <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>12.2.0.jre11</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>com.azure</groupId><artifactId>*</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>metadata</artifactId><version>1</version><dependencies><dependency>
                  <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>com.azure</groupId><artifactId>*</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void upgradesOnlyProjectDependencyAndNotPluginDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-safety</artifactId><version>1</version>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>codegen</artifactId><version>1</version><dependencies><dependency>
                    <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version>
                  </dependency></dependencies><configuration>
                    <profiles><profile><dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version></dependency></dependencies></profile></profiles>
                    <project><dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version></dependency></dependencies></project>
                  </configuration></plugin></plugins></build>
                  <dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-safety</artifactId><version>1</version>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>codegen</artifactId><version>1</version><dependencies><dependency>
                    <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version>
                  </dependency></dependencies><configuration>
                    <profiles><profile><dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version></dependency></dependencies></profile></profiles>
                    <project><dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version></dependency></dependencies></project>
                  </configuration></plugin></plugins></build>
                  <dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesMavenClassifierAndExplicitTypeVariants() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>variants</artifactId><version>1</version><dependencies>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version><classifier>sources</classifier></dependency>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version><type>jar</type></dependency>
                </dependencies></project>
                """,
                source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesPropertyReferencedFromAnXmlAttribute() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>attribute-owner</artifactId><version>1</version>
                  <properties><mssql.version>10.2.3.jre17</mssql.version></properties>
                  <dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>${mssql.version}</version></dependency></dependencies>
                  <build><plugins><plugin><artifactId>example-plugin</artifactId><configuration><argument value="${mssql.version}"/></configuration></plugin></plugins></build>
                </project>
                """,
                source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesDuplicateRootPropertyDeclarations() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicates</artifactId><version>1</version>
                  <properties><mssql.version>10.2.3.jre8</mssql.version><mssql.version>10.2.3.jre8</mssql.version></properties>
                  <dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>${mssql.version}</version></dependency></dependencies>
                </project>
                """,
                source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesRootPropertyWhenAProfileShadowsItsName() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-shadow</artifactId><version>1</version>
                  <properties><mssql.version>10.2.3.jre8</mssql.version></properties>
                  <profiles><profile><id>legacy</id><properties><mssql.version>10.2.3.jre8</mssql.version></properties><dependencies><dependency>
                    <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>${mssql.version}</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                source -> source.path("pom.xml")
        ));
    }

    @Test
    void upgradesAnExclusivelyOwnedRootPropertyUsedFromAProfile() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-use</artifactId><version>1</version>
                  <properties><mssql.version>12.3.0.jre17-preview</mssql.version></properties>
                  <profiles><profile><id>sqlserver</id><dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>${mssql.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-use</artifactId><version>1</version>
                  <properties><mssql.version>13.2.1.jre11</mssql.version></properties>
                  <profiles><profile><id>sqlserver</id><dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>${mssql.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void preservesNestedGradleDslThatOnlyLooksLikeDependencies() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                dependencies {
                  generatedSources {
                    implementation 'com.microsoft.sqlserver:mssql-jdbc:10.2.3.jre8'
                  }
                }
                """
        ));
    }

    @Test
    void preservesBuildscriptDependencyDsl() {
        rewriteRun(buildGradle(
                """
                buildscript {
                  dependencies {
                    implementation 'com.microsoft.sqlserver:mssql-jdbc:10.2.3.jre8'
                  }
                }
                """
        ));
    }

    @Test
    void preservesGroovyMapVariants() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                dependencies {
                  implementation group: 'com.microsoft.sqlserver', name: 'mssql-jdbc', version: '10.2.3.jre8', classifier: 'sources'
                  implementation group: 'com.microsoft.sqlserver', name: 'mssql-jdbc', version: '10.2.3.jre8', ext: 'zip'
                  implementation([group: 'com.microsoft.sqlserver', name: 'mssql-jdbc', version: '10.2.3.jre8', type: 'test-fixtures'])
                }
                """
        ));
    }

    @Test
    void preservesDynamicRangeAndFourPartGradleCoordinates() {
        rewriteRun(
                buildGradle(
                        """
                        plugins { id 'java' }
                        dependencies {
                          implementation 'com.microsoft.sqlserver:mssql-jdbc:10.+'
                          runtimeOnly 'com.microsoft.sqlserver:mssql-jdbc:[10,12)'
                          testImplementation 'com.microsoft.sqlserver:mssql-jdbc:10.2.3.jre8:sources'
                        }
                        """
                ),
                buildGradleKts(
                        """
                        plugins { java }
                        dependencies {
                            implementation("com.microsoft.sqlserver:mssql-jdbc:10.+")
                            runtimeOnly("com.microsoft.sqlserver:mssql-jdbc:10.2.3.jre8:sources")
                        }
                        """
                )
        );
    }

    @Test
    void skipsGeneratedAndInstallTrees() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>generated</artifactId><version>1</version><dependencies><dependency>
                          <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version>
                        </dependency></dependencies></project>
                        """,
                        source -> source.path("target/generated-sources/pom.xml")
                ),
                buildGradle(
                        "plugins { id 'java' }\ndependencies { implementation 'com.microsoft.sqlserver:mssql-jdbc:10.2.3.jre8' }",
                        source -> source.path("install/build.gradle")
                ),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>mvn-cache</artifactId><version>1</version><dependencies><dependency>
                          <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version>
                        </dependency></dependencies></project>
                        """,
                        source -> source.path(".mvn/cache/pom.xml")
                )
        );
    }
}
