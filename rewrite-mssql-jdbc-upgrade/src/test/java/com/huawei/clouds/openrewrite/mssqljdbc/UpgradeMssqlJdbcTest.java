package com.huawei.clouds.openrewrite.mssqljdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeMssqlJdbcTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.mssqljdbc.UpgradeMssqlJdbcTo13_2_1Jre11";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.mssqljdbc.MigrateDeterministicMssqlJdbcAuthentication";
    private static final String JAVA_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.mssqljdbc.FindManualMssqlJdbc13JavaRisks";
    private static final String BUILD_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.mssqljdbc.FindManualMssqlJdbc13BuildRisks";
    private static final String CONFIG_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.mssqljdbc.FindManualMssqlJdbc13ConfigurationRisks";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.mssqljdbc.MigrateMssqlJdbcTo13_2_1Jre11";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(DEPENDENCY_RECIPE));
    }

    @ParameterizedTest(name = "upgrades exact spreadsheet version {0}")
    @ValueSource(strings = {
            "7.2.2.jre8", "9.4.1.jre11", "10.2.1.jre8",
            "10.2.3.jre8", "10.2.3.jre17", "11.2.2.jre11",
            "12.2.0.jre11", "12.3.0.jre17-preview"
    })
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(pomXml(pom(oldVersion), pom("13.2.1.jre11")));
    }

    @Test
    void upgradesDependencyManagementLiteralAndPreservesVersionlessUse() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesApacheFlinkRealIsolatedProperty() {
        // apache/flink-connector-jdbc, fixed commit 140f179d019aba6a3f52e17d180c8d329ccdb8b6
        // https://github.com/apache/flink-connector-jdbc/blob/140f179d019aba6a3f52e17d180c8d329ccdb8b6/flink-connector-jdbc-sqlserver/pom.xml
        rewriteRun(pomXml(
                propertyPom("10.2.1.jre8", "sqlserver.version"),
                propertyPom("13.2.1.jre11", "sqlserver.version")
        ));
    }

    @Test
    void upgradesAbsaRealManagedProperty() {
        // AbsaOSS/inception, fixed commit e752c4b0f1d9843b749a9389b58a0deb0f558f22
        // https://github.com/AbsaOSS/inception/blob/e752c4b0f1d9843b749a9389b58a0deb0f558f22/src/pom.xml
        rewriteRun(pomXml(
                managedPropertyPom("9.4.1.jre11"),
                managedPropertyPom("13.2.1.jre11")
        ));
    }

    @Test
    void upgradesUsaceRealProvidedDependency() {
        // USACE/data-query, fixed commit e106e50751fd8f7e4c6b524468d3015058d5e678
        // https://github.com/USACE/data-query/blob/e106e50751fd8f7e4c6b524468d3015058d5e678/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>mil.army.usace</groupId><artifactId>data-query</artifactId><version>1</version><dependencies><dependency>
                  <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>11.2.2.jre11</version><scope>provided</scope>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>mil.army.usace</groupId><artifactId>data-query</artifactId><version>1</version><dependencies><dependency>
                  <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version><scope>provided</scope>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void preservesSharedMavenProperty() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><shared.version>10.2.3.jre17</shared.version></properties><dependencies>
                    <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>${shared.version}</version></dependency>
                    <dependency><groupId>com.example</groupId><artifactId>database-adapter</artifactId><version>${shared.version}</version></dependency>
                  </dependencies>
                </project>
                """,
                source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesUnresolvedRangeUnlistedTargetVariantAndFutureVersions() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>safety</artifactId><version>1</version><dependencies>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>${missing.version}</version></dependency>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>[10,12)</version></dependency>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.2.jre8</version></dependency>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>12.10.0.jre11</version></dependency>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre8</version></dependency>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.4.0.jre11</version></dependency>
                </dependencies></project>
                """,
                source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesExternalBomManagedVersionlessDependency() {
        // Azure-Samples/azure-spring-boot-samples, fixed commit 7a287e56608bf691239323610c890de95f33d435
        // https://github.com/Azure-Samples/azure-spring-boot-samples/blob/7a287e56608bf691239323610c890de95f33d435/spring-cloud-azure-testcontainers/service-bus/spring-messaging/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.1.0</version></parent>
                  <groupId>com.azure.spring</groupId><artifactId>spring-cloud-azure-testcontainers-for-service-bus-spring-messaging-sample</artifactId><version>1.0.0</version><dependencies>
                    <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesGroovyStringMapAndKotlinLiteralNotation() {
        rewriteRun(
                buildGradle(
                        "plugins { id 'java' }\ndependencies { runtimeOnly 'com.microsoft.sqlserver:mssql-jdbc:7.2.2.jre8' }",
                        "plugins { id 'java' }\ndependencies { runtimeOnly 'com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11' }",
                        source -> source.path("string.gradle")
                ),
                buildGradle(
                        "plugins { id 'java' }\ndependencies { testImplementation group: 'com.microsoft.sqlserver', name: 'mssql-jdbc', version: '10.2.3.jre17' }",
                        "plugins { id 'java' }\ndependencies { testImplementation group: 'com.microsoft.sqlserver', name: 'mssql-jdbc', version: '13.2.1.jre11' }",
                        source -> source.path("map.gradle")
                ),
                buildGradleKts(
                        "plugins { java }\ndependencies { implementation(\"com.microsoft.sqlserver:mssql-jdbc:11.2.2.jre11\") }",
                        "plugins { java }\ndependencies { implementation(\"com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11\") }"
                )
        );
    }

    @Test
    void preservesGradleInterpolationAndCatalogAlias() {
        rewriteRun(
                buildGradle(
                        """
                        plugins { id 'java' }
                        ext { sqlServerVersion = '10.2.3.jre8' }
                        dependencies {
                          runtimeOnly "com.microsoft.sqlserver:mssql-jdbc:${sqlServerVersion}"
                          runtimeOnly libs.mssql.jdbc
                        }
                        """
                ),
                buildGradleKts(
                        """
                        plugins { java }
                        val sqlServerVersion = "10.2.3.jre8"
                        dependencies { runtimeOnly("com.microsoft.sqlserver:mssql-jdbc:$sqlServerVersion") }
                        """
                )
        );
    }

    @Test
    void preservesSimilarCoordinatesAndAuthArtifact() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>other</artifactId><version>1</version><dependencies>
                  <dependency><groupId>example</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.1.jre8</version></dependency>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc_auth</artifactId><version>10.2.1.x64</version></dependency>
                  <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId></dependency>
                </dependencies></project>
                """,
                source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesClassifierVariantWithoutChangingItsShape() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shape</artifactId><version>1</version><dependencies><dependency>
                  <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version><classifier>sources</classifier><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>com.azure</groupId><artifactId>*</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                source -> source.path("pom.xml")
        ));
    }

    @Test
    void dependencyUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("7.2.2.jre8"), pom("13.2.1.jre11")));
    }

    @Test
    void migratesOfficialAuthenticationRenameInCompleteJdbcUrl() {
        rewriteRun(
                sourceSpec(),
                java(
                        """
                        package example;
                        class ConnectionFactory {
                            String url = "jdbc:sqlserver://db;databaseName=app;authentication=DefaultAzureCredential;encrypt=true";
                        }
                        """,
                        """
                        package example;
                        class ConnectionFactory {
                            String url = "jdbc:sqlserver://db;databaseName=app;authentication=ActiveDirectoryDefault;encrypt=true";
                        }
                        """
                )
        );
    }

    @Test
    void migratesOfficialAuthenticationRenameInTypedDataSourceSetter() {
        rewriteRun(
                sourceSpec(),
                java(
                        """
                        package example;
                        import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
                        class DataSourceConfig { void configure(SQLServerDataSource ds) { ds.setAuthentication("DefaultAzureCredential"); } }
                        """,
                        """
                        package example;
                        import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
                        class DataSourceConfig { void configure(SQLServerDataSource ds) { ds.setAuthentication("ActiveDirectoryDefault"); } }
                        """
                )
        );
    }

    @Test
    void doesNotRenameAzureSdkClassNameOrUntypedString() {
        rewriteRun(
                sourceSpec(),
                java(
                        """
                        package example;
                        class AzureSdkConfig { String credentialClass = "DefaultAzureCredential"; String fragment = "authentication=DefaultAzureCredential;"; }
                        """
                )
        );
    }

    @Test
    void authenticationMigrationIsIdempotent() {
        rewriteRun(
                spec -> {
                    sourceSpec().accept(spec);
                    spec.cycles(2).expectedCyclesThatMakeChanges(1);
                },
                java(
                        "class App { String url = \"jdbc:sqlserver://db;authentication=DefaultAzureCredential;\"; }",
                        "class App { String url = \"jdbc:sqlserver://db;authentication=ActiveDirectoryDefault;\"; }"
                )
        );
    }

    @Test
    void marksUrlWithoutExplicitTlsAndTimeoutPolicy() {
        rewriteRun(
                riskSpec(),
                java(
                        "class App { String url = \"primary=jdbc:sqlserver://localhost;databaseName=app\"; }",
                        "class App { String url = /*~~(SQL Server JDBC 13.2: encrypt now defaults to true; define and test certificate policy; loginTimeout default changed from 15s to 30s)~~>*/\"primary=jdbc:sqlserver://localhost;databaseName=app\"; }"
                )
        );
    }

    @Test
    void marksPentahoRealTlsAlwaysEncryptedAndEntraFragments() {
        // pentaho/pentaho-kettle, fixed commit a0e1ed445ba96e8f1105caa878d7526231f6a037
        // https://github.com/pentaho/pentaho-kettle/blob/a0e1ed445ba96e8f1105caa878d7526231f6a037/core/src/main/java/org/pentaho/di/core/database/AzureSqlDataBaseMeta.java
        rewriteRun(
                riskSpec(),
                java(
                        """
                        package example;
                        class AzureSqlDataBaseMeta {
                            String getURL(String host, String port, String database) {
                                String url = "jdbc:sqlserver://" + host + ":" + port + ";database=" + database + ";encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;";
                                url += "columnEncryptionSetting=Enabled;keyVaultProviderClientId=id;keyVaultProviderClientKey=secret;";
                                return url + "authentication=ActiveDirectoryPassword;";
                            }
                        }
                        """,
                        """
                        package example;
                        class AzureSqlDataBaseMeta {
                            String getURL(String host, String port, String database) {
                                String url = /*~~(SQL Server JDBC 13.2: encrypt now defaults to true; define and test certificate policy; loginTimeout default changed from 15s to 30s)~~>*/"jdbc:sqlserver://" + host + ":" + port + ";database=" + database + ";encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;";
                                url += /*~~(Always Encrypted connection fragment detected; verify key provider, optional dependencies, attestation, metadata cache and failover behavior)~~>*/"columnEncryptionSetting=Enabled;keyVaultProviderClientId=id;keyVaultProviderClientKey=secret;";
                                return url + /*~~(Microsoft Entra connection fragment detected; review deprecated modes/properties, token callbacks and Azure Identity/MSAL dependencies)~~>*/"authentication=ActiveDirectoryPassword;";
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksTypedTlsTimeoutAuthEncryptionVectorAndSessionCalls() {
        rewriteRun(
                riskSpec(),
                java(
                        """
                        import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
                        class Risks { void configure(SQLServerDataSource ds) {
                            ds.setEncrypt("strict");
                            ds.setLoginTimeout(15);
                            ds.setAADSecurePrincipalId("client");
                            ds.setColumnEncryptionSetting("Enabled");
                            ds.setVectorTypeSupport("off");
                            ds.setQuotedIdentifier(true);
                        } }
                        """,
                        """
                        import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
                        class Risks { void configure(SQLServerDataSource ds) {
                            /*~~(Retest SQL Server JDBC 13.2 encryption defaults, strict/TDS 8.0, certificate chain and hostname validation)~~>*/ds.setEncrypt("strict");
                            /*~~(13.2 inherits the 30-second default and revised socket/login timeout interaction; verify failover, pool and probe budgets)~~>*/ds.setLoginTimeout(15);
                            /*~~(Review Microsoft Entra/integrated authentication: deprecated AAD properties, renamed modes, optional Azure Identity/MSAL dependencies and token callbacks)~~>*/ds.setAADSecurePrincipalId("client");
                            /*~~(Always Encrypted or enclave API detected; verify key provider, attestation, metadata cache, optional dependencies and failover behavior)~~>*/ds.setColumnEncryptionSetting("Enabled");
                            /*~~(Verify SQL Server 13.2 native vector representation and bulk-copy/ResultSet/ORM mappings; use off only as a temporary compatibility choice)~~>*/ds.setVectorTypeSupport("off");
                            /*~~(Verify quotedIdentifier/concatNullYieldsNull on new and pooled connections; session state affects SQL semantics)~~>*/ds.setQuotedIdentifier(true);
                        } }
                        """
                )
        );
    }

    @Test
    void marksVectorSqlAndNativeAuthenticationLibrary() {
        rewriteRun(
                riskSpec(),
                java(
                        """
                        class NativeAndVector {
                            String create = "CREATE TABLE embeddings (v VECTOR(1536))";
                            String dll = "mssql-jdbc_auth-10.2.3.x64.dll";
                        }
                        """,
                        """
                        class NativeAndVector {
                            String create = /*~~(SQL Server VECTOR detected: 13.2 returns native vector values unless vectorTypeSupport=off; update ResultSet/ORM mappings deliberately)~~>*/"CREATE TABLE embeddings (v VECTOR(1536))";
                            String dll = /*~~(Native integrated-authentication library reference detected; deploy the 13.2 architecture-specific mssql-jdbc_auth binary and retest Kerberos/NTLM)~~>*/"mssql-jdbc_auth-10.2.3.x64.dll";
                        }
                        """
                )
        );
    }

    @Test
    void javaRiskMarkersAreIdempotent() {
        rewriteRun(
                spec -> {
                    riskSpec().accept(spec);
                    spec.cycles(2).expectedCyclesThatMakeChanges(1);
                },
                java(
                        "class App { String url = \"jdbc:sqlserver://db;databaseName=app\"; }",
                        "class App { String url = /*~~(SQL Server JDBC 13.2: encrypt now defaults to true; define and test certificate policy; loginTimeout default changed from 15s to 30s)~~>*/\"jdbc:sqlserver://db;databaseName=app\"; }"
                )
        );
    }

    @Test
    void javaRiskSearchSkipsGeneratedAndInstallSources() {
        rewriteRun(
                riskSpec(),
                java(
                        "class Generated { String url = \"jdbc:sqlserver://db;encrypt=true\"; }",
                        source -> source.path("target/generated-sources/Generated.java")
                ),
                java(
                        "class Installed { String dll = \"mssql-jdbc_auth.dll\"; }",
                        source -> source.path("install/sources/Installed.java")
                )
        );
    }

    @Test
    void buildSearchMarksJava8AndOptionalAuthEncryptionDependencies() {
        rewriteRun(
                spec -> spec.recipe(recipe(BUILD_RISK_RECIPE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>sqlserver</artifactId><version>1</version>
                        <properties><maven.compiler.release>8</maven.compiler.release></properties><dependencies>
                          <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency>
                          <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc_auth</artifactId><version>13.2.1.x64</version></dependency>
                          <dependency><groupId>com.azure</groupId><artifactId>azure-identity</artifactId><version>1.16.2</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>sqlserver</artifactId><version>1</version>
                        <properties><!--~~(mssql-jdbc 13.2.1.jre11 requires Java 11 or newer; align compiler, toolchain, CI, container and runtime JDKs)~~>--><maven.compiler.release>8</maven.compiler.release></properties><dependencies>
                          <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version></dependency>
                          <!--~~(SQL Server JDBC optional runtime dependency detected; align it with the selected Entra, native authentication, Always Encrypted or vector feature and verify dependency convergence)~~>--><dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc_auth</artifactId><version>13.2.1.x64</version></dependency>
                          <!--~~(SQL Server JDBC optional runtime dependency detected; align it with the selected Entra, native authentication, Always Encrypted or vector feature and verify dependency convergence)~~>--><dependency><groupId>com.azure</groupId><artifactId>azure-identity</artifactId><version>1.16.2</version></dependency>
                        </dependencies></project>
                        """
                )
        );
    }

    @Test
    void configurationSearchMarksExactConnectionPropertyAndNativeDll() {
        rewriteRun(
                spec -> spec.recipe(recipe(CONFIG_RISK_RECIPE)),
                properties(
                        "spring.datasource.url=jdbc:sqlserver://db;databaseName=app\nspring.datasource.hikari.data-source-properties.encrypt=true\n",
                        "~~(SQL Server JDBC URL detected; retest 13.2 TLS/encrypt/certificate policy, 30-second login timeout, Entra authentication, failover and pooling)~~>spring.datasource.url=jdbc:sqlserver://db;databaseName=app\n~~(SQL Server JDBC connection property detected; review its 13.2 TLS, timeout, authentication, Always Encrypted, vector or pooled-session semantics)~~>spring.datasource.hikari.data-source-properties.encrypt=true\n",
                        source -> source.path("application.properties")
                ),
                text("AUTH_DLL=mssql-jdbc_auth-10.2.3.x64.dll", "~~(Native SQL Server authentication library detected; deploy the matching 13.2 OS/architecture binary and retest Kerberos, NTLM and integrated security)~~>AUTH_DLL=mssql-jdbc_auth-10.2.3.x64.dll",
                        source -> source.path("runtime.env"))
        );
    }

    @Test
    void compositeUpgradesRenamesAuthenticationAndMarksRemainingUrlRisks() {
        rewriteRun(
                spec -> {
                    sourceSpec().accept(spec);
                    spec.recipe(recipe(MIGRATION_RECIPE)).typeValidationOptions(TypeValidation.none());
                },
                pomXml(pom("10.2.3.jre17"), pom("13.2.1.jre11")),
                java(
                        "class App { String url = \"jdbc:sqlserver://db;authentication=DefaultAzureCredential;\"; }",
                        "class App { String url = /*~~(SQL Server JDBC 13.2: encrypt now defaults to true; define and test certificate policy; loginTimeout default changed from 15s to 30s; review Microsoft Entra authentication and optional Azure/MSAL dependencies)~~>*/\"jdbc:sqlserver://db;authentication=ActiveDirectoryDefault;\"; }"
                )
        );
    }

    @Test
    void allRecipesAreDiscoverableAndValid() {
        Environment environment = environment();
        String[] names = {
                DEPENDENCY_RECIPE, SOURCE_RECIPE, JAVA_RISK_RECIPE, BUILD_RISK_RECIPE,
                CONFIG_RISK_RECIPE, MIGRATION_RECIPE
        };
        for (String name : names) {
            Recipe recipe = environment.activateRecipes(name);
            assertEquals(name, recipe.getName());
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
        }
    }

    private Recipe recipe(String name) {
        return environment().activateRecipes(name);
    }

    private static Consumer<RecipeSpec> sourceSpec() {
        return spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE))
                .parser(mssqlParser()).typeValidationOptions(TypeValidation.none());
    }

    private static Consumer<RecipeSpec> riskSpec() {
        return spec -> spec.recipe(environment().activateRecipes(JAVA_RISK_RECIPE))
                .parser(mssqlParser()).typeValidationOptions(TypeValidation.none());
    }

    private static JavaParser.Builder<?, ?> mssqlParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package com.microsoft.sqlserver.jdbc;
                public class SQLServerDataSource {
                    public void setAuthentication(String v) {} public void setEncrypt(String v) {}
                    public void setTrustServerCertificate(boolean v) {} public void setLoginTimeout(int v) {}
                    public void setAADSecurePrincipalId(String v) {} public void setAADSecurePrincipalSecret(String v) {}
                    public void setColumnEncryptionSetting(String v) {} public void setVectorTypeSupport(String v) {}
                    public String getVectorTypeSupport() { return null; } public void setQuotedIdentifier(boolean v) {}
                    public void setConcatNullYieldsNull(boolean v) {}
                }
                """
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>driver</artifactId><version>1</version><dependencies><dependency>
                 <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static String propertyPom(String version, String property) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>org.apache.flink</groupId><artifactId>flink-connector-jdbc-sqlserver</artifactId><version>4.0</version>
                 <properties><%1$s>%2$s</%1$s></properties><dependencies><dependency>
                   <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>${%1$s}</version><scope>provided</scope>
                 </dependency></dependencies>
               </project>
               """.formatted(property, version);
    }

    private static String managedPropertyPom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>za.co.absa</groupId><artifactId>inception</artifactId><version>1</version>
                 <properties><mssql-jdbc.version>%s</mssql-jdbc.version></properties><dependencyManagement><dependencies><dependency>
                   <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>${mssql-jdbc.version}</version>
                 </dependency></dependencies></dependencyManagement>
               </project>
               """.formatted(version);
    }
}
