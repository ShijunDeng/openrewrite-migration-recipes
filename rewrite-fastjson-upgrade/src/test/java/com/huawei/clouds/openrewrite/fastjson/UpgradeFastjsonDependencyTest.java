package com.huawei.clouds.openrewrite.fastjson;

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
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeFastjsonDependencyTest implements RewriteTest {
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.fastjson.MigrateFastjsonTo2_0_62";
    private static final String NATIVE_RECIPE =
            "com.huawei.clouds.openrewrite.fastjson.MigrateFastjson1ToFastjson2Api";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedFastjsonDependency());
    }

    @ParameterizedTest(name = "upgrades exact spreadsheet Maven version {0}")
    @ValueSource(strings = {
            "1.2.40", "1.2.70", "1.2.71", "1.2.75", "1.2.83", "1.2.83_noneautotype",
            "2.0.12", "2.0.13", "2.0.14", "2.0.16"
    })
    void upgradesEveryVisibleSpreadsheetVersion(String version) {
        rewriteRun(pomXml(pomWithVersion(version), pomWithVersion("2.0.62")));
    }

    @ParameterizedTest(name = "rejects unlisted or non-literal Maven version {0}")
    @ValueSource(strings = {
            "1.2.39", "1.2.69", "1.2.80", "2.0.15", "2.0.17", "2.0.60", "2.0.61", "2.0.62",
            "[1.2,2.0)", "LATEST"
    })
    void rejectsUnlistedTargetNewerRangesAndDynamicVersions(String version) {
        rewriteRun(pomXml(pomWithVersion(version)));
    }

    @Test
    void upgradesExclusiveMavenProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                  <properties><fastjson.version>1.2.83</fastjson.version></properties>
                  <dependencies><dependency><groupId>com.alibaba</groupId><artifactId>fastjson</artifactId><version>${fastjson.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                  <properties><fastjson.version>2.0.62</fastjson.version></properties>
                  <dependencies><dependency><groupId>com.alibaba</groupId><artifactId>fastjson</artifactId><version>${fastjson.version}</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesSharedMavenPropertyUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>platform</artifactId><version>${shared.version}</version>
                  <properties><shared.version>1.2.83</shared.version></properties><dependencies>
                    <dependency><groupId>com.alibaba</groupId><artifactId>fastjson</artifactId><version>${shared.version}</version></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void upgradesDependencyManagementDeclaration() {
        rewriteRun(pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>parent</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>com.alibaba</groupId><artifactId>fastjson</artifactId><version>1.2.75</version></dependency></dependencies></dependencyManagement>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>parent</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>com.alibaba</groupId><artifactId>fastjson</artifactId><version>2.0.62</version></dependency></dependencies></dependencyManagement>
                        </project>
                        """
        ));
    }

    @Test
    void upgradesLiteralGradleAndKotlinDslCoordinates() {
        rewriteRun(
                buildGradle(
                        "plugins { id 'java' }\ndependencies { implementation 'com.alibaba:fastjson:1.2.70' }",
                        "plugins { id 'java' }\ndependencies { implementation 'com.alibaba:fastjson:2.0.62' }"
                ),
                buildGradleKts(
                        "plugins { java }\ndependencies { implementation(\"com.alibaba:fastjson:2.0.14\") }",
                        "plugins { java }\ndependencies { implementation(\"com.alibaba:fastjson:2.0.62\") }",
                        spec -> spec.path("kotlin/build.gradle.kts")
                )
        );
    }

    @Test
    void upgradesGradleMapNotationAndPreservesConfiguration() {
        rewriteRun(buildGradle(
                "plugins { id 'java-library' }\ndependencies { compileOnly group: 'com.alibaba', name: 'fastjson', version: '1.2.71' }",
                "plugins { id 'java-library' }\ndependencies { compileOnly group: 'com.alibaba', name: 'fastjson', version: '2.0.62' }"
        ));
    }

    @Test
    void leavesGradleVariablesRangesAndOtherArtifactsUntouched() {
        rewriteRun(buildGradle("""
                plugins { id 'java' }
                def fastjsonVersion = '1.2.83'
                dependencies {
                    implementation "com.alibaba:fastjson:$fastjsonVersion"
                    implementation 'com.alibaba:fastjson:[1.2,2.0)'
                    implementation 'com.alibaba:fastjson2:1.2.83'
                    implementation 'com.alibaba.fastjson2:fastjson2:2.0.14'
                }
                """));
    }

    @Test
    void leavesGeneratedBuildDescriptorsUntouched() {
        rewriteRun(
                pomXml(pomWithVersion("1.2.83"), source -> source.path("target/pom.xml")),
                buildGradle("plugins { id 'java' }\ndependencies { implementation 'com.alibaba:fastjson:1.2.83' }",
                        source -> source.path("build/generated/build.gradle"))
        );
    }

    @Test
    void nativeModeStrictlyChangesCoordinatesAndVersion() {
        rewriteRun(
                spec -> spec.recipe(new MigrateSelectedFastjsonDependencyToNative()),
                pomXml(
                        pomWithVersion("1.2.83"),
                        pomWithCoordinates("com.alibaba.fastjson2", "fastjson2", "2.0.62")
                ),
                buildGradle(
                        "plugins { id 'java' }\ndependencies { implementation 'com.alibaba:fastjson:2.0.12' }",
                        "plugins { id 'java' }\ndependencies { implementation 'com.alibaba.fastjson2:fastjson2:2.0.62' }",
                        source -> source.path("native/build.gradle")
                )
        );
    }

    @Test
    void nativeModeDoesNotChangeUnlistedCoordinates() {
        rewriteRun(
                spec -> spec.recipe(new MigrateSelectedFastjsonDependencyToNative()),
                pomXml(pomWithVersion("1.2.80")),
                buildGradle("plugins { id 'java' }\ndependencies { implementation 'com.alibaba:fastjson:2.0.61' }")
        );
    }

    @Test
    void crossoverJieCimPomShapeUsesStrictUpgrade() {
        // Reduced from crossoverJie/cim at 8863d9f6d76d0ad55a27bd0d6f05d6476937f0e8:
        // https://github.com/crossoverJie/cim/blob/8863d9f6d76d0ad55a27bd0d6f05d6476937f0e8/pom.xml#L165-L169
        rewriteRun(pomXml(pomWithVersion("1.2.83"), pomWithVersion("2.0.62")));
    }

    @Test
    void discoversAndValidatesPublicRecipes() {
        Environment environment = environment();
        assertEquals(10, UpgradeSelectedFastjsonDependency.SOURCE_VERSIONS.size());
        for (String name : new String[]{
                MIGRATION_RECIPE,
                "com.huawei.clouds.openrewrite.fastjson.UpgradeFastjsonTo2_0_62",
                NATIVE_RECIPE
        }) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        }
    }

    private static String pomWithVersion(String version) {
        return pomWithCoordinates("com.alibaba", "fastjson", version);
    }

    private static String pomWithCoordinates(String group, String artifact, String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>fastjson-app</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version>
                  </dependency></dependencies>
                </project>
                """.formatted(group, artifact, version);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.fastjson")
                .scanYamlResources()
                .build();
    }
}
