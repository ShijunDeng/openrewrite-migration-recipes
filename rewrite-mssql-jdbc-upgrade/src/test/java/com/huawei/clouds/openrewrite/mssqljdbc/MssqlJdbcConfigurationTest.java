package com.huawei.clouds.openrewrite.mssqljdbc;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class MssqlJdbcConfigurationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.mssqljdbc.MigrateDeterministicMssqlJdbcAuthentication";
    private static final String RISKS =
            "com.huawei.clouds.openrewrite.mssqljdbc.FindManualMssqlJdbc13ConfigurationRisks";
    private static final String URL_MESSAGE =
            "SQL Server JDBC URL detected; retest 13.2 TLS/encrypt/certificate policy, 30-second login timeout, Entra authentication, failover and pooling";
    private static final String PROPERTY_MESSAGE =
            "SQL Server JDBC connection property detected; review its 13.2 TLS, timeout, authentication, Always Encrypted, vector or pooled-session semantics";
    private static final String NATIVE_MESSAGE =
            "Native SQL Server authentication library detected; deploy the matching 13.2 OS/architecture binary and retest Kerberos, NTLM and integrated security";

    @Test
    void migratesCompletePropertiesJdbcUrl() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUTO)),
                properties(
                        "spring.datasource.url=jdbc:sqlserver://db;authentication=DefaultAzureCredential;encrypt=true\n",
                        "spring.datasource.url=jdbc:sqlserver://db;authentication=ActiveDirectoryDefault;encrypt=true\n"
                )
        );
    }

    @Test
    void migratesCompleteYamlJdbcUrlAndPreservesQuoting() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUTO)),
                yaml(
                        "datasource:\n  url: 'jdbc:sqlserver://db;authentication=DefaultAzureCredential'\n",
                        "datasource:\n  url: 'jdbc:sqlserver://db;authentication=ActiveDirectoryDefault'\n"
                )
        );
    }

    @Test
    void migratesXmlTextAndAttributeValuesButNotPomConfiguration() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUTO)),
                xml(
                        "<datasource url=\"jdbc:sqlserver://db;authentication=DefaultAzureCredential\"><url>jdbc:sqlserver://db;authentication=DefaultAzureCredential;</url></datasource>",
                        "<datasource url=\"jdbc:sqlserver://db;authentication=ActiveDirectoryDefault\"><url>jdbc:sqlserver://db;authentication=ActiveDirectoryDefault;</url></datasource>",
                        source -> source.path("src/main/resources/sqlserver.xml")
                ),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>config-owner</artifactId><version>1</version>
                          <build><plugins><plugin><artifactId>example</artifactId><configuration><url>jdbc:sqlserver://db;authentication=DefaultAzureCredential;</url></configuration></plugin></plugins></build>
                        </project>
                        """
                )
        );
    }

    @Test
    void migratesEnvAndJsonCompleteUrls() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUTO)),
                text(
                        "DATABASE_URL=jdbc:sqlserver://db;authentication=DefaultAzureCredential;encrypt=true",
                        "DATABASE_URL=jdbc:sqlserver://db;authentication=ActiveDirectoryDefault;encrypt=true",
                        source -> source.path("runtime.env")
                ),
                text(
                        "{\"url\":\"jdbc:sqlserver://db;authentication=DefaultAzureCredential\"}",
                        "{\"url\":\"jdbc:sqlserver://db;authentication=ActiveDirectoryDefault\"}",
                        source -> source.path("connection.json")
                )
        );
    }

    @Test
    void leavesFragmentsCredentialClassNamesAndUnrelatedFormatsAlone() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUTO)),
                properties("authentication=DefaultAzureCredential\ncredential.class=DefaultAzureCredential\n"),
                yaml("authentication: DefaultAzureCredential\ncredentialClass: DefaultAzureCredential\n"),
                text("jdbc:sqlserver://db;authentication=DefaultAzureCredential;", source -> source.path("notes.txt"))
        );
    }

    @Test
    void authenticationConfigMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUTO)).cycles(2).expectedCyclesThatMakeChanges(1),
                properties(
                        "url=jdbc:sqlserver://db;authentication=DefaultAzureCredential;\n",
                        "url=jdbc:sqlserver://db;authentication=ActiveDirectoryDefault;\n"
                )
        );
    }

    @Test
    void skipsGeneratedJavaAndConfigurationDuringAutoMigration() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUTO)),
                java(
                        "class Generated { String url = \"jdbc:sqlserver://db;authentication=DefaultAzureCredential;\"; }",
                        source -> source.path("target/generated-sources/Generated.java")
                ),
                properties(
                        "url=jdbc:sqlserver://db;authentication=DefaultAzureCredential;\n",
                        source -> source.path("build/generated/application.properties")
                )
        );
    }

    @Test
    void marksPropertiesUrlAndOnlyContextOwnedConnectionKeys() {
        rewriteRun(
                spec -> spec.recipe(recipe(RISKS)),
                properties(
                        """
                        spring.datasource.url=jdbc:sqlserver://db;databaseName=app
                        spring.datasource.hikari.data-source-properties.trustServerCertificate=false
                        application.encrypt=true
                        """,
                        """
                        ~~(%s)~~>spring.datasource.url=jdbc:sqlserver://db;databaseName=app
                        ~~(%s)~~>spring.datasource.hikari.data-source-properties.trustServerCertificate=false
                        application.encrypt=true
                        """.formatted(URL_MESSAGE, PROPERTY_MESSAGE)
                ),
                properties("encrypt=true\nloginTimeout=5\n", source -> source.path("unrelated.properties")),
                properties(
                        "# jdbc:sqlserver://comment-only\nspring.datasource.encrypt=true\n",
                        source -> source.path("comment-only.properties"))
        );
    }

    @Test
    void marksYamlAndXmlStructuredNodes() {
        rewriteRun(
                spec -> spec.recipe(recipe(RISKS)),
                yaml(
                        """
                        datasource:
                          url: jdbc:sqlserver://db;databaseName=app
                          encrypt: strict
                        """,
                        """
                        datasource:
                          ~~(%s)~~>url: jdbc:sqlserver://db;databaseName=app
                          ~~(%s)~~>encrypt: strict
                        """.formatted(URL_MESSAGE, PROPERTY_MESSAGE)
                ),
                xml(
                        "<datasource><url>jdbc:sqlserver://db;databaseName=app</url><encrypt>strict</encrypt></datasource>",
                        "<datasource><!--~~(%s)~~>--><url>jdbc:sqlserver://db;databaseName=app</url><!--~~(%s)~~>--><encrypt>strict</encrypt></datasource>"
                                .formatted(URL_MESSAGE, PROPERTY_MESSAGE),
                        source -> source.path("src/main/resources/sqlserver.xml")
                ),
                yaml(
                        "# jdbc:sqlserver://comment-only\ndatasource:\n  encrypt: strict\n",
                        source -> source.path("src/main/resources/comment-only.yml")
                ),
                xml(
                        "<datasource><!-- jdbc:sqlserver://comment-only --><encrypt>strict</encrypt></datasource>",
                        source -> source.path("src/main/resources/comment-only.xml")
                )
        );
    }

    @Test
    void marksNativeLibraryInScriptAndIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(recipe(RISKS)).cycles(2).expectedCyclesThatMakeChanges(1),
                text(
                        "export AUTH_DLL=/opt/sqljdbc_auth.dll",
                        "~~(%s)~~>export AUTH_DLL=/opt/sqljdbc_auth.dll".formatted(NATIVE_MESSAGE),
                        source -> source.path("docker-entrypoint.sh")
                )
        );
    }

    @Test
    void skipsGeneratedConfigurationMarkers() {
        rewriteRun(
                spec -> spec.recipe(recipe(RISKS)),
                properties(
                        "url=jdbc:sqlserver://db;encrypt=true\n",
                        source -> source.path("target/generated-test-sources/application.properties")
                ),
                text(
                        "AUTH_DLL=mssql-jdbc_auth.dll",
                        source -> source.path("build/generated/runtime.env")
                )
        );
    }

    private static org.openrewrite.Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }
}
