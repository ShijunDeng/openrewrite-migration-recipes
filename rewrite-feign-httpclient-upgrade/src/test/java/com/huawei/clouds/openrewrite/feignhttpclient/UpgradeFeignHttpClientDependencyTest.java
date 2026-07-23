package com.huawei.clouds.openrewrite.feignhttpclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeFeignHttpClientDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedFeignHttpClientDependency());
    }

    @ParameterizedTest(name = "workbook source {0}")
    @ValueSource(strings = {"10.4.0", "12", "12.1", "12.2", "12.4"})
    void upgradesEveryWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("13.6"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesRows409Through413() {
        assertEquals(Set.of("10.4.0", "12", "12.1", "12.2", "12.4"),
                UpgradeSelectedFeignHttpClientDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesExclusiveRootProperty() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>codec</artifactId><version>1</version>
                  <properties><feign.httpclient.version>12.1</feign.httpclient.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>${feign.httpclient.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>codec</artifactId><version>1</version>
                  <properties><feign.httpclient.version>13.6</feign.httpclient.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>${feign.httpclient.version}</version></dependency></dependencies>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void rootPropertyIsVisibleInDirectProfile() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <properties><feign.version>12.4</feign.version></properties>
                  <profiles><profile><id>integration</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>${feign.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <properties><feign.version>13.6</feign.version></properties>
                  <profiles><profile><id>integration</id><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>${feign.version}</version></dependency></dependencies></profile></profiles>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWinsWithoutLeakingToRootOrSibling() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scopes</artifactId><version>1</version>
                  <properties><feign.version>10.4.0</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles>
                    <profile><id>owned</id><properties><feign.version>12.4</feign.version></properties><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>${feign.version}</version></dependency></dependencies></profile>
                    <profile><id>sibling</id><properties><label>${feign.version}</label></properties></profile>
                  </profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scopes</artifactId><version>1</version>
                  <properties><feign.version>10.4.0</feign.version></properties>
                  <dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>${feign.version}</version></dependency></dependencies>
                  <profiles>
                    <profile><id>owned</id><properties><feign.version>13.6</feign.version></properties><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>${feign.version}</version></dependency></dependencies></profile>
                    <profile><id>sibling</id><properties><label>${feign.version}</label></properties></profile>
                  </profiles>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>12.1</version></dependency></dependencies></dependencyManagement>
                  <profiles><profile><id>legacy</id><dependencyManagement><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>12</version></dependency></dependencies></dependencyManagement></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>13.6</version></dependency></dependencies></dependencyManagement>
                  <profiles><profile><id>legacy</id><dependencyManagement><dependencies><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>13.6</version></dependency></dependencies></dependencyManagement></profile></profiles>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property ownership: {0}")
    @MethodSource("ambiguousProperties")
    void leavesAmbiguousPropertiesUntouched(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("unused", project("<properties><f>12.4</f></properties>")),
                Arguments.of("shared plugin", project("<properties><f>12.4</f></properties><dependencies>" + dep("${f}") + "</dependencies><build><finalName>${f}</finalName></build>")),
                Arguments.of("shared dependency", project("<properties><f>12.4</f></properties><dependencies>" + dep("${f}") + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${f}</version></dependency></dependencies>")),
                Arguments.of("duplicate", project("<properties><f>12.4</f><f>12.4</f></properties><dependencies>" + dep("${f}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><f>12.4</f></properties><dependencies>" + dep("${f}") + "</dependencies><x value=\"${f}\"/>")));
    }

    @Test
    void upgradesRealGroovyPatternFromEpasDependencyManifest() {
        // Build form reduced from consiglionazionaledellericerche/epas at 7bac6d72ae3af2a3b0dadc848a83e2af58d630ee.
        rewriteRun(buildGradle(
                "dependencies { implementation 'io.github.openfeign:feign-httpclient:12.4' }",
                "dependencies { implementation 'io.github.openfeign:feign-httpclient:13.6' }"));
    }

    @Test
    void upgradesRealKotlinStyleWhileKeepingAdjacentFeignModule() {
        // Family-alignment shape reduced from CNR/epas at
        // 7bac6d72ae3af2a3b0dadc848a83e2af58d630ee.
        rewriteRun(buildGradleKts(
                """
                dependencies {
                    implementation("io.github.openfeign:feign-core:12.4")
                    implementation("io.github.openfeign:feign-httpclient:12.4")
                }
                """,
                """
                dependencies {
                    implementation("io.github.openfeign:feign-core:12.4")
                    implementation("io.github.openfeign:feign-httpclient:13.6")
                }
                """));
    }

    @Test
    void upgradesGroovyMapNotationAndPreservesMetadata() {
        rewriteRun(buildGradle(
                "dependencies { runtimeOnly group: 'io.github.openfeign', name: 'feign-httpclient', version: '12.2', transitive: false }",
                "dependencies { runtimeOnly group: 'io.github.openfeign', name: 'feign-httpclient', version: '13.6', transitive: false }"));
    }

    @ParameterizedTest(name = "nested Gradle scope NOOP: {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleScopes(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'io.github.openfeign:feign-httpclient:12.4'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':client') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom", "company { dependencies { implementation " + d + " } }"),
                Arguments.of("selected invocation", "dependencies { helper.implementation " + d + " }"));
    }

    @ParameterizedTest(name = "out-of-workbook {0}")
    @ValueSource(strings = {"10.3", "10.5", "11.1", "11.9", "12.3", "13.5", "13.7"})
    void doesNotGuessOtherFixedVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "unowned or nonfixed {0}")
    @ValueSource(strings = {"${feign.version}", "[10,13)", "[12.4,)", "12.+", "+", "latest.release"})
    void doesNotGuessDynamicOrExternalOwners(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void versionlessBomOwnedAndVariantsAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + "</dependencies>"), source -> source.path("bom/pom.xml")),
                xml(project("<dependencies>" + dep("12.4", "<classifier>tests</classifier>") + "</dependencies>"), source -> source.path("classified/pom.xml")),
                xml(project("<dependencies>" + dep("12.4", "<type>zip</type>") + "</dependencies>"), source -> source.path("zip/pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-httpclient:12.4:tests' }"),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-httpclient:12.4@zip' }"));
    }

    @ParameterizedTest(name = "generated/cache parent {0}")
    @ValueSource(strings = {"target", "generatedSources", "GENERATED-code", "installation", "INSTALL-cache", ".gradle", ".m2", "reports", ".output", "test-results", "storybook-static", "tmp", "TEMP", ".vite", ".nuxt"})
    void generatedAndCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("12.4"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void leafFilenamesBeginningInstallAreStillProcessed() {
        rewriteRun(
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-httpclient:12.4' }",
                        "dependencies { implementation 'io.github.openfeign:feign-httpclient:13.6' }",
                        source -> source.path("install.gradle")),
                xml(pom("12.1"), pom("13.6"), source -> source.path("pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("10.4.0"), pom("13.6"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-httpclient:12.4' }",
                        "dependencies { implementation 'io.github.openfeign:feign-httpclient:13.6' }"));
    }

    @Test
    void publicLowLevelRecipeContainsOnlyStrictUpgrade() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feignhttpclient").build();
        var migrate = environment.activateRecipes(
                "com.huawei.clouds.openrewrite.feignhttpclient.MigrateFeignHttpClientTo13_6");
        assertEquals("com.huawei.clouds.openrewrite.feignhttpclient.UpgradeFeignHttpClientTo13_6",
                migrate.getRecipeList().get(0).getName());
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.feignhttpclient.UpgradeFeignHttpClientTo13_6")),
                xml(pom("12.4"), pom("13.6"), source -> source.path("pom.xml")));
    }

    private static String pom(String version) {
        return project("<dependencies>" + dep(version) + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>client</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dep(String version) {
        return dep(version, "");
    }

    private static String dep(String version, String extra) {
        return "<dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>" + version + "</version>" + extra + "</dependency>";
    }

    private static String depWithoutVersion() {
        return "<dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId></dependency>";
    }
}
