package com.huawei.clouds.openrewrite.springfoxswagger2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringfoxSwagger2UpgradeTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springfoxswagger2.UpgradeSpringfoxSwagger2To1_1_2";
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.springfoxswagger2.AuditSpringfoxSwagger2WorkbookTarget";
    private static final String MIGRATE_ALIAS =
            "com.huawei.clouds.openrewrite.springfoxswagger2.MigrateSpringfoxSwagger2To1_1_2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @Test
    void sourceWhitelistExactlyMatchesVisibleWorkbookRows() {
        assertEquals(Set.of("3.0.0", "2.10.5", "2.6.1", "2.7.0", "2.9.2", "1.0.1"),
                UpgradeSelectedSpringfoxSwagger2Dependency.SOURCE_VERSIONS);
        assertEquals("1.1.2", UpgradeSelectedSpringfoxSwagger2Dependency.TARGET);
    }

    @ParameterizedTest(name = "Maven {0} -> literal workbook target")
    @ValueSource(strings = {"3.0.0", "2.10.5", "2.6.1", "2.7.0", "2.9.2"})
    void upgradesEveryPublishedWorkbookMavenSource(String source) {
        rewriteRun(pomXml(pom(source), pom("1.1.2")));
    }

    @Test
    void appliesUnpublishedWorkbookSourceAsLiteralGradleInput() {
        rewriteRun(buildGradle(
                "dependencies { implementation 'io.springfox:springfox-swagger2:1.0.1' }",
                "dependencies { implementation 'io.springfox:springfox-swagger2:1.1.2' }"));
    }

    @Test
    void appliesUnpublishedWorkbookSourceAsLiteralMavenXmlInput() {
        // Generic XML is intentional: Maven resolution cannot parse this nonexistent source coordinate.
        rewriteRun(xml(pom("1.0.1"), pom("1.1.2"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRealMyBatisPlusFixtureAtFixedCommit() {
        // baomidou/mybatis-plus@db0b3c4bb58a38bad9c3d78b7269d8a477cc6a63, mybatis-plus-generator/build.gradle
        rewriteRun(buildGradle(
                """
                dependencies {
                    implementation "${lib.enjoy}"
                    implementation "${lib.'swagger-annotations'}"
                    implementation "io.springfox:springfox-swagger2:3.0.0"
                }
                """,
                """
                dependencies {
                    implementation "${lib.enjoy}"
                    implementation "${lib.'swagger-annotations'}"
                    implementation "io.springfox:springfox-swagger2:1.1.2"
                }
                """));
    }

    @Test
    void upgradesRealCourseSpring5FixtureAtFixedCommit() {
        // vityaman-edu/lecture-2023-spring@8a817847702265da37ab7003f93229eb0a52c92d, build.gradle#L56-L61
        rewriteRun(buildGradle(
                "dependencies {\n    implementation \"io.springfox:springfox-swagger2:2.10.5\"\n    implementation \"org.springdoc:springdoc-openapi-ui:1.6.14\"\n}",
                "dependencies {\n    implementation \"io.springfox:springfox-swagger2:1.1.2\"\n    implementation \"org.springdoc:springdoc-openapi-ui:1.6.14\"\n}"));
    }

    @Test
    void upgradesRealNetflixExampleFixtureAtFixedCommit() {
        // yidongnan/spring-cloud-netflix-example@3b86bf0e20a7c7da8f4e3e7e2cb15bf4cd407743, service-b/build.gradle
        rewriteRun(buildGradle(
                "dependencies { compile 'io.springfox:springfox-swagger2:2.6.1' }",
                "dependencies { compile 'io.springfox:springfox-swagger2:1.1.2' }"));
    }

    @Test
    void upgradesRealFlycloudFixtureAtFixedCommit() {
        // mxdldev/spring-cloud-flycloud@d5507da91c146661ffdd395d3d15d25ad351a5a2, lib_common/build.gradle
        rewriteRun(buildGradle(
                "dependencies {\n    compile 'io.springfox:springfox-swagger2:2.7.0'\n    compile 'io.springfox:springfox-swagger-ui:2.7.0'\n}",
                "dependencies {\n    compile 'io.springfox:springfox-swagger2:1.1.2'\n    compile 'io.springfox:springfox-swagger-ui:2.7.0'\n}"));
    }

    @Test
    void upgradesRealScenariooFixtureAtFixedCommit() {
        // imloama/api-server-seed@9545a6caccfe51f9d0299afcff01500041bea52c, build.gradle#L58-L63
        rewriteRun(buildGradle(
                "dependencies {\n    implementation(\"io.springfox:springfox-swagger2:2.9.2\")\n    implementation('io.springfox:springfox-swagger-ui:2.9.2')\n    implementation(\"io.springfox:springfox-staticdocs:2.6.1\")\n}",
                "dependencies {\n    implementation(\"io.springfox:springfox-swagger2:1.1.2\")\n    implementation('io.springfox:springfox-swagger-ui:2.9.2')\n    implementation(\"io.springfox:springfox-staticdocs:2.6.1\")\n}"));
    }

    @Test
    void upgradesExclusivelyOwnedMavenProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                  <properties><springfox.version>2.9.2</springfox.version></properties><dependencies><dependency>
                    <groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version>
                  </dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                  <properties><springfox.version>1.1.2</springfox.version></properties><dependencies><dependency>
                    <groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version>
                  </dependency></dependencies>
                </project>
                """));
    }

    @Test
    void leavesSharedMavenPropertyForCompanionOwner() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><springfox.version>2.9.2</springfox.version></properties><dependencies>
                    <dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version></dependency>
                    <dependency><groupId>io.springfox</groupId><artifactId>springfox-core</artifactId><version>${springfox.version}</version></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void leavesPropertyReferencedByPluginConfiguration() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared-plugin</artifactId><version>1</version>
                  <properties><springfox.version>2.9.2</springfox.version></properties>
                  <dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version></dependency></dependencies>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>audit</artifactId><configuration><baseline>${springfox.version}</baseline></configuration></plugin></plugins></build>
                </project>
                """));
    }

    @Test
    void rootPropertyIsVisibleToProfileDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>root-owner</artifactId><version>1</version>
                  <properties><springfox.version>2.9.2</springfox.version></properties><profiles><profile><id>docs</id><dependencies><dependency>
                    <groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>root-owner</artifactId><version>1</version>
                  <properties><springfox.version>1.1.2</springfox.version></properties><profiles><profile><id>docs</id><dependencies><dependency>
                    <groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void profilePropertyDoesNotLeakIntoRootDependency() {
        // Generic XML is intentional: Maven correctly rejects the profile-only property at root scope.
        rewriteRun(xml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-leak</artifactId><version>1</version>
                  <dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version></dependency></dependencies>
                  <profiles><profile><id>docs</id><properties><springfox.version>2.9.2</springfox.version></properties></profile></profiles>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void profilePropertyOverridesRootPropertyWithinItsScope() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>override</artifactId><version>1</version>
                  <properties><springfox.version>2.9.2</springfox.version></properties>
                  <dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version></dependency></dependencies>
                  <profiles><profile><id>docs</id><properties><springfox.version>2.7.0</springfox.version></properties><dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>override</artifactId><version>1</version>
                  <properties><springfox.version>1.1.2</springfox.version></properties>
                  <dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version></dependency></dependencies>
                  <profiles><profile><id>docs</id><properties><springfox.version>1.1.2</springfox.version></properties><dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void upgradesProfileAndPreservesMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version><profiles><profile><id>docs</id><dependencies><dependency>
                  <groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>2.7.0</version><scope>test</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId></exclusion></exclusions>
                </dependency></dependencies></profile></profiles></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version><profiles><profile><id>docs</id><dependencies><dependency>
                  <groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>1.1.2</version><scope>test</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId></exclusion></exclusions>
                </dependency></dependencies></profile></profiles></project>
                """));
    }

    @Test
    void leavesDependencyManagementForItsOwner() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version><dependencyManagement><dependencies>
                  <dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>2.9.2</version></dependency>
                </dependencies></dependencyManagement><dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId></dependency></dependencies></project>
                """));
    }

    @ParameterizedTest(name = "strict recipe does not widen to {0}")
    @ValueSource(strings = {"2.0.1", "2.5.0", "2.6.0", "2.8.0", "2.9.1", "2.10.0", "2.10.4"})
    void doesNotWidenToOtherVersions(String version) {
        rewriteRun(pomXml(pom(version)));
    }

    @Test
    void leavesLiteralTargetAlone() {
        rewriteRun(buildGradle("dependencies { implementation 'io.springfox:springfox-swagger2:1.1.2' }"));
    }

    @Test
    void leavesVariantsVersionlessAndOtherCoordinates() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>negative</artifactId><version>1</version><dependencies>
                  <dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId></dependency>
                  <dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>2.9.2</version><classifier>sources</classifier></dependency>
                  <dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>2.7.0</version><type>test-jar</type></dependency>
                  <dependency><groupId>io.springfox</groupId><artifactId>springfox-core</artifactId><version>2.9.2</version></dependency>
                  <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-ui</artifactId><version>1.6.15</version></dependency>
                </dependencies></project>
                """));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target/pom.xml", "build/pom.xml", "out/pom.xml", "dist/pom.xml", "generated/pom.xml",
            "generated-sources/pom.xml", "install-cache/pom.xml", "vendor/pom.xml", "node_modules/pom.xml",
            "bower_components/pom.xml", ".m2/pom.xml", ".pnpm/pom.xml", ".yarn/pom.xml", ".npm/pom.xml",
            ".angular/pom.xml", ".nx/pom.xml", ".next/pom.xml", ".cache/pom.xml"})
    void skipsGeneratedAndInstalledTrees(String path) {
        rewriteRun(pomXml(pom("2.9.2"), source -> source.path(path)));
    }

    @Test
    void upgradesGroovyStringAndBothMapNotations() {
        rewriteRun(buildGradle(
                """
                dependencies {
                    implementation 'io.springfox:springfox-swagger2:2.6.1'
                    testImplementation group: 'io.springfox', name: 'springfox-swagger2', version: '2.7.0'
                    runtimeOnly([group: 'io.springfox', name: 'springfox-swagger2', version: '2.9.2'])
                }
                """,
                """
                dependencies {
                    implementation 'io.springfox:springfox-swagger2:1.1.2'
                    testImplementation group: 'io.springfox', name: 'springfox-swagger2', version: '1.1.2'
                    runtimeOnly([group: 'io.springfox', name: 'springfox-swagger2', version: '1.1.2'])
                }
                """));
    }

    @Test
    void upgradesKotlinDslLiteral() {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"io.springfox:springfox-swagger2:2.10.5\") }",
                "dependencies { implementation(\"io.springfox:springfox-swagger2:1.1.2\") }"));
    }

    @Test
    void leavesGradleVariablesCatalogsPlatformsVariantsAndLookalikeText() {
        rewriteRun(buildGradle(
                """
                def springfoxVersion = '2.9.2'
                dependencies {
                    implementation "io.springfox:springfox-swagger2:${springfoxVersion}"
                    implementation libs.springfox.swagger2
                    implementation platform('io.springfox:springfox-bom:2.9.2')
                    implementation 'io.springfox:springfox-swagger2:2.9.2:sources'
                    implementation group: 'io.springfox', name: 'springfox-swagger2', version: '2.7.0', classifier: 'sources'
                    implementation([group: 'io.springfox', name: 'springfox-swagger2', version: '2.6.1', ext: 'zip'])
                }
                println 'io.springfox:springfox-swagger2:3.0.0'
                """));
    }

    @Test
    void leavesDependenciesNestedInCustomDslAlone() {
        rewriteRun(buildGradle(
                "subprojects { dependencies { implementation 'io.springfox:springfox-swagger2:2.9.2' } }"));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("3.0.0"), pom("1.1.2")),
                buildGradleKts(
                        "dependencies { testImplementation(\"io.springfox:springfox-swagger2:2.6.1\") }",
                        "dependencies { testImplementation(\"io.springfox:springfox-swagger2:1.1.2\") }"));
    }

    @Test
    void recipesAreDiscoverableAndValid() {
        Recipe upgrade = environment().activateRecipes(UPGRADE);
        Recipe audit = environment().activateRecipes(AUDIT);
        Recipe migrateAlias = environment().activateRecipes(MIGRATE_ALIAS);
        assertFalse(upgrade.getRecipeList().isEmpty());
        assertFalse(audit.getRecipeList().isEmpty());
        assertFalse(migrateAlias.getRecipeList().isEmpty());
        assertTrue(upgrade.validateAll().stream().allMatch(validation -> validation.isValid()));
        assertTrue(audit.validateAll().stream().allMatch(validation -> validation.isValid()));
        assertTrue(migrateAlias.validateAll().stream().allMatch(validation -> validation.isValid()));
    }

    @Test
    void recommendedAuditDoesNotWriteTheUnpublishedTarget() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                pomXml(pom("2.9.2"), source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "<version>2.9.2</version>");
                    assertContains(after.printAll(), "downgrade from a published Springfox");
                })));
    }

    @Test
    void compatibilityAliasIsAlsoNonMutating() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE_ALIAS)),
                buildGradle("dependencies { implementation 'io.springfox:springfox-swagger2:2.10.5' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "springfox-swagger2:2.10.5");
                            assertContains(after.printAll(), "downgrade from a published Springfox");
                        })));
    }

    @Test
    void marksPreExistingLiteralWorkbookTargetAsUnresolvable() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2BuildRisks()),
                xml(pom("1.1.2"), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "<version>1.1.2</version>");
                    assertContains(after.printAll(), "not published in Maven Central");
                })));
    }

    @Test
    void marksReverseDirectionBeforeVersionRewriteWhenRunStandalone() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2BuildRisks()),
                buildGradle(
                        "dependencies { implementation 'io.springfox:springfox-swagger2:3.0.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "downgrade from a published Springfox"))));
    }

    @Test
    void marksManagementVariantExternalFamilyAndJava7Risks() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2BuildRisks()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>risks</artifactId><version>1</version>
                          <properties><maven.compiler.release>7</maven.compiler.release><springfox.version>2.9.2</springfox.version></properties>
                          <dependencyManagement><dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>2.9.2</version></dependency></dependencies></dependencyManagement>
                          <dependencies>
                            <dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>3.0.0</version><classifier>sources</classifier></dependency>
                            <dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version></dependency>
                            <dependency><groupId>io.springfox</groupId><artifactId>springfox-core</artifactId><version>2.9.2</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "no verifiable Java baseline");
                            assertContains(printed, "under dependencyManagement/BOM");
                            assertContains(printed, "classified or non-JAR");
                            assertContains(printed, "externally owned");
                            assertContains(printed, "modules must be version-aligned");
                        })));
    }

    @Test
    void doesNotInferSpringfoxJavaBaselineWithoutAThreeZeroOwner() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2BuildRisks()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>java7</artifactId><version>1</version>
                          <properties><maven.compiler.release>7</maven.compiler.release></properties>
                          <dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>2.9.2</version></dependency></dependencies>
                        </project>
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertFalse(printed.contains("no verifiable Java baseline"), printed);
                            assertContains(printed, "downgrade from a published Springfox");
                        })));
    }

    @Test
    void profileVersionPropertyCannotResolveRootDependencyForJavaBaseline() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2BuildRisks()),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unresolved-root</artifactId><version>1</version>
                          <properties><maven.compiler.release>7</maven.compiler.release></properties>
                          <dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>${springfox.version}</version></dependency></dependencies>
                          <profiles><profile><id>docs</id><properties><springfox.version>3.0.0</springfox.version></properties></profile></profiles>
                        </project>
                        """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertFalse(after.printAll().contains("no verifiable Java baseline"), after.printAll()))));
    }

    @Test
    void rootJavaBaselineAppliesToSpringfoxThreeProfile() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2BuildRisks()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-java</artifactId><version>1</version>
                          <properties><maven.compiler.release>7</maven.compiler.release></properties>
                          <profiles><profile><id>docs</id><dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>3.0.0</version></dependency></dependencies></profile></profiles>
                        </project>
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "no verifiable Java baseline"))));
    }

    @Test
    void siblingProfileJavaBaselineIsNotClaimed() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2BuildRisks()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>sibling-profile</artifactId><version>1</version><profiles>
                          <profile><id>docs</id><dependencies><dependency><groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>3.0.0</version></dependency></dependencies></profile>
                          <profile><id>legacy-tool</id><properties><maven.compiler.release>7</maven.compiler.release></properties></profile>
                        </profiles></project>
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "downgrade from a published Springfox");
                            assertFalse(printed.contains("no verifiable Java baseline"), printed);
                        })));
    }

    @Test
    void variantMarkerIsAttachedToTheVariantOwner() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2BuildRisks()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>variant</artifactId><version>1</version><dependencies><dependency>
                          <groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>2.9.2</version><classifier>sources</classifier>
                        </dependency></dependencies></project>
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            int message = printed.indexOf("classified or non-JAR");
                            int classifier = printed.indexOf("<classifier>sources</classifier>");
                            assertTrue(message >= 0 && message < classifier && classifier - message < 500, printed);
                        })));
    }

    @Test
    void marksGradleDynamicVariantAndCompanionRisks() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2BuildRisks()),
                buildGradle(
                        """
                        dependencies {
                            implementation 'io.springfox:springfox-swagger2:2.+'
                            implementation 'io.springfox:springfox-swagger2:2.9.2:sources'
                            implementation 'io.springfox:springfox-swagger-ui:2.9.2'
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "externally owned");
                            assertContains(printed, "classified or non-JAR");
                            assertContains(printed, "modules must be version-aligned");
                        })));
    }

    @Test
    void marksAttributedDocketAndEnableSwagger2Apis() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2JavaRisks()).parser(springfoxParser()),
                java(
                        """
                        import springfox.documentation.spring.web.plugins.Docket;
                        import springfox.documentation.swagger2.annotations.EnableSwagger2;
                        @EnableSwagger2
                        class SwaggerConfig { Docket docket; }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "This Springfox API cannot be migrated"))));
    }

    @Test
    void marksAttributedSpringfoxMethodInvocation() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2JavaRisks()).parser(JavaParser.fromJavaVersion().dependsOn(
                        "package springfox.documentation.spring.web.plugins; public class Docket { public Docket select(){ return this; } }")),
                java(
                        """
                        import springfox.documentation.spring.web.plugins.Docket;
                        class SwaggerConfig { Object configure(Docket docket) { return docket.select(); } }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "selectors/plugins/models/security"))));
    }

    @Test
    void marksAttributedSwagger2Annotation() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2JavaRisks()).parser(JavaParser.fromJavaVersion().dependsOn(
                        "package io.swagger.annotations; public @interface Api { String value() default \"\"; }")),
                java(
                        "import io.swagger.annotations.Api; @Api(\"pets\") class PetController {}",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "annotation behavior cannot be validated"))));
    }

    @ParameterizedTest(name = "marks endpoint {0}")
    @ValueSource(strings = {"/v2/api-docs", "/v3/api-docs", "/swagger-resources", "/swagger-ui.html", "/swagger-ui/", "/webjars/**", "/swagger-resources/**"})
    void marksExactJavaEndpointLiterals(String endpoint) {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2JavaRisks()),
                java("class SecurityConfig { String endpoint = \"" + endpoint + "\"; }",
                        source -> source.path("src/main/java/example/SpringfoxSecurityConfig.java")
                                .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "deployed security/routing contract"))));
    }

    @Test
    void leavesLocalLookalikesAndNearMissEndpointAlone() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2JavaRisks()),
                java("class Docket { Docket select(){return this;} } class Local { Docket d = new Docket().select(); String p = \"/v2/api-docs-extra\"; }"));
    }

    @Test
    void doesNotClaimSpringdocEndpointIsOwnedBySpringfox() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2JavaRisks()),
                java("class SecurityConfig { String endpoint = \"/v3/api-docs\"; }",
                        source -> source.path("src/main/java/example/SecurityConfig.java")));
    }

    @Test
    void javaRiskFinderSkipsGeneratedSources() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2JavaRisks()),
                java("class Generated { String endpoint = \"/swagger-ui.html\"; }",
                        source -> source.path("target/generated-sources/Generated.java")));
    }

    @Test
    void installNamedJavaLeafRemainsInProjectScope() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2JavaRisks()).parser(springfoxParser()),
                java("import springfox.documentation.spring.web.plugins.Docket; class Install { Docket docket; }",
                        source -> source.path("src/main/java/example/Install.java").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(),
                                        "This Springfox API cannot be migrated"))));
    }

    @Test
    void marksSpringfoxPropertiesEndpointAndPathMatchWorkaround() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2ConfigurationRisks()),
                properties(
                        """
                        springfox.documentation.swagger.v2.path=/v2/api-docs
                        spring.mvc.pathmatch.matching-strategy=ant_path_matcher
                        management.endpoint.health.show-details=always
                        """,
                        source -> source.path("src/main/resources/application.properties").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertContains(printed, "Springfox configuration contract");
                                    assertContains(printed, "compatibility workaround");
                                })));
    }

    @Test
    void marksYamlEndpointAndPathMatchWorkaround() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2ConfigurationRisks()),
                yaml(
                        """
                        spring:
                          mvc:
                            pathmatch:
                              matching-strategy: ant_path_matcher
                        springfox:
                          documentation:
                            swagger:
                              v2:
                                path: /v2/api-docs
                        springfox-routes:
                          ui-url: /swagger-ui.html
                        """,
                        source -> source.path("src/main/resources/application.yml").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertContains(printed, "compatibility workaround");
                                    assertContains(printed, "Springfox configuration contract");
                                    assertContains(printed, "externally visible routing/security contract");
                                })));
    }

    @Test
    void marksNonPomXmlEndpointAndSpringfoxKey() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2ConfigurationRisks()),
                xml(
                        "<configuration><springfox.documentation.swagger.v2.path>/v2/api-docs</springfox.documentation.swagger.v2.path><springfox-ui>/swagger-ui/</springfox-ui></configuration>",
                        source -> source.path("src/main/resources/docs.xml").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertContains(printed, "Springfox configuration contract");
                                    assertContains(printed, "externally visible routing/security contract");
                                })));
    }

    @Test
    void configurationFinderLeavesUnrelatedValuesAlone() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2ConfigurationRisks()),
                properties("spring.mvc.pathmatch.matching-strategy=path_pattern_parser\ndocs.url=/docs\n"),
                properties("docs.url=/v3/api-docs\n"),
                properties("springdoc.swagger-ui.path=/swagger-ui/\n"),
                yaml("docs:\n  url: /swagger-ui.html\n  matching-strategy: ant_path_matcher\n"),
                xml("<configuration><docs>/swagger-ui/</docs></configuration>"));
    }

    @Test
    void configurationFinderSkipsGeneratedTrees() {
        rewriteRun(spec -> spec.recipe(new FindSpringfoxSwagger2ConfigurationRisks()),
                properties("springfox.documentation.swagger.v2.path=/v2/api-docs\n",
                        source -> source.path("build/resources/main/application.properties")),
                yaml("docs:\n  url: /swagger-ui.html\n",
                        source -> source.path("target/classes/application.yml")));
    }

    @Test
    void recommendedRecipeIsStableAcrossTwoCycles() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)).parser(springfoxParser())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradle(
                        "dependencies { implementation 'io.springfox:springfox-swagger2:2.9.2' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "springfox-swagger2:2.9.2");
                            assertContains(after.printAll(), "downgrade from a published Springfox");
                        })),
                java(
                        "import springfox.documentation.spring.web.plugins.Docket; class Config { Docket docket; }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "This Springfox API cannot be migrated"))),
                properties(
                        "spring.mvc.pathmatch.matching-strategy=ant_path_matcher\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "compatibility workaround"))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static JavaParser.Builder<?, ?> springfoxParser() {
        return JavaParser.fromJavaVersion().classpath("springfox-swagger2", "springfox-spring-web");
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency>
                 <groupId>io.springfox</groupId><artifactId>springfox-swagger2</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected output to contain: " + expected + "\nActual:\n" + actual);
    }
}
