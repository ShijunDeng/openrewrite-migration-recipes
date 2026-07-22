package com.huawei.clouds.openrewrite.graalvmjs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeGraalVmJsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedGraalVmJsDependency());
    }

    @ParameterizedTest
    @ValueSource(strings = {"22.3.0", "22.3.1"})
    void upgradesEveryWorkbookSource(String source) {
        rewriteRun(pomXml(pom(source), pom("24.2.1")));
    }

    @Test
    void sourceWhitelistExactlyMatchesWorkbook() {
        assertEquals(Set.of("22.3.0", "22.3.1"), UpgradeSelectedGraalVmJsDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesOwnedRootProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>root</artifactId><version>1</version>
                  <properties><graaljs.version>22.3.0</graaljs.version></properties>
                  <dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>${graaljs.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>root</artifactId><version>1</version>
                  <properties><graaljs.version>24.2.1</graaljs.version></properties>
                  <dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>${graaljs.version}</version></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void rootPropertyIsVisibleInsideProfile() {
        rewriteRun(pomXml(
                project("<properties><g>22.3.1</g></properties><profiles><profile><id>ci</id><dependencies>" +
                        dep("${g}") + "</dependencies></profile></profiles>"),
                project("<properties><g>24.2.1</g></properties><profiles><profile><id>ci</id><dependencies>" +
                        dep("${g}") + "</dependencies></profile></profiles>")));
    }

    @Test
    void profilePropertyDoesNotLeakToRoot() {
        String before = project("<dependencies>" + dep("${g}") + "</dependencies><profiles><profile><id>ci</id>" +
                                "<properties><g>22.3.0</g></properties></profile></profiles>");
        rewriteRun(xml(before, source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWins() {
        rewriteRun(pomXml(
                project("<properties><g>22.3.0</g></properties><dependencies>" + dep("${g}") +
                        "</dependencies><profiles><profile><id>ci</id><properties><g>22.3.1</g></properties>" +
                        "<dependencies>" + dep("${g}") + "</dependencies></profile></profiles>"),
                project("<properties><g>24.2.1</g></properties><dependencies>" + dep("${g}") +
                        "</dependencies><profiles><profile><id>ci</id><properties><g>24.2.1</g></properties>" +
                        "<dependencies>" + dep("${g}") + "</dependencies></profile></profiles>")));
    }

    @Test
    void sharedPropertyIsProtected() {
        String before = project("<properties><g>22.3.0</g></properties><dependencies>" + dep("${g}") +
                                "<dependency><groupId>org.graalvm.sdk</groupId><artifactId>graal-sdk</artifactId>" +
                                "<version>${g}</version></dependency></dependencies>");
        rewriteRun(pomXml(before));
    }

    @Test
    void duplicatePropertyDefinitionIsProtected() {
        String before = project("<properties><g>22.3.0</g><g>22.3.0</g></properties><dependencies>" + dep("${g}") +
                                "</dependencies>");
        rewriteRun(xml(before, source -> source.path("pom.xml")));
    }

    @Test
    void propertyUsedInAttributeIsProtected() {
        String before = project("<properties><g>22.3.1</g></properties><name data-version=\"${g}\">app</name>" +
                                "<dependencies>" + dep("${g}") + "</dependencies>");
        rewriteRun(xml(before, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesDependencyManagementAndProfileDependency() {
        rewriteRun(pomXml(
                project("<dependencyManagement><dependencies>" + dep("22.3.0") +
                        "</dependencies></dependencyManagement><profiles><profile><id>prod</id><dependencies>" +
                        dep("22.3.1") + "</dependencies></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("24.2.1") +
                        "</dependencies></dependencyManagement><profiles><profile><id>prod</id><dependencies>" +
                        dep("24.2.1") + "</dependencies></profile></profiles>")));
    }

    @Test
    void upgradesLocalManagementWithoutWritingVersionOnConsumer() {
        rewriteRun(pomXml(
                project("<dependencyManagement><dependencies>" + dep("22.3.0") +
                        "</dependencies></dependencyManagement><dependencies><dependency><groupId>org.graalvm.js</groupId>" +
                        "<artifactId>js</artifactId></dependency></dependencies>"),
                project("<dependencyManagement><dependencies>" + dep("24.2.1") +
                        "</dependencies></dependencyManagement><dependencies><dependency><groupId>org.graalvm.js</groupId>" +
                        "<artifactId>js</artifactId></dependency></dependencies>")));
    }

    @Test
    void ignoresPluginDependencyAndLookalikeXml() {
        String before = project("<build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><dependencies>" +
                                dep("22.3.0") + "</dependencies></plugin></plugins></build><metadata>" +
                                dep("22.3.1") + "</metadata>");
        rewriteRun(xml(before, source -> source.path("pom.xml")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"22.2.0", "23.0.0", "24.2.0", "24.2.1", "25.0.0", "[22,25)", "LATEST"})
    void protectsVersionsOutsideExactWorkbookSelection(String version) {
        rewriteRun(pomXml(pom(version)));
    }

    @Test
    void protectsVersionlessClassifierAndPomVariants() {
        String before = project("<dependencies>" +
                                "<dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId></dependency>" +
                                "<dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>22.3.0</version><classifier>linux</classifier></dependency>" +
                                "<dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>22.3.1</version><type>pom</type></dependency>" +
                                "</dependencies>");
        rewriteRun(pomXml(before));
    }

    @Test
    void upgradesRealMobileUpGroovyFixture() {
        rewriteRun(buildGradle(
                """
                dependencies {
                    implementation("org.graalvm.js:js:22.3.0")
                }
                """,
                """
                dependencies {
                    implementation("org.graalvm.js:js:24.2.1")
                }
                """));
    }

    @Test
    void upgradesRealCssxshKotlinFixture() {
        rewriteRun(buildGradleKts(
                """
                dependencies {
                    compileOnly("org.graalvm.js:js:22.3.1")
                }
                """,
                """
                dependencies {
                    compileOnly("org.graalvm.js:js:24.2.1")
                }
                """));
    }

    @Test
    void upgradesGroovyMapAndMapLiteral() {
        rewriteRun(buildGradle(
                """
                dependencies {
                    implementation group: 'org.graalvm.js', name: 'js', version: '22.3.0'
                    runtimeOnly([group: 'org.graalvm.js', name: 'js', version: '22.3.1'])
                }
                """,
                """
                dependencies {
                    implementation group: 'org.graalvm.js', name: 'js', version: '24.2.1'
                    runtimeOnly([group: 'org.graalvm.js', name: 'js', version: '24.2.1'])
                }
                """));
    }

    @Test
    void ignoresNestedAndSelectedGradleDsl() {
        String before = """
                buildscript { dependencies { classpath('org.graalvm.js:js:22.3.0') } }
                subprojects { dependencies { implementation('org.graalvm.js:js:22.3.0') } }
                project(':child') { dependencies { implementation('org.graalvm.js:js:22.3.1') } }
                dependencies {
                    constraints { implementation('org.graalvm.js:js:22.3.0') }
                    custom.implementation('org.graalvm.js:js:22.3.1')
                }
                """;
        rewriteRun(buildGradle(before));
    }

    @Test
    void ignoresGradleDynamicInterpolationAndVariant() {
        String before = """
                dependencies {
                    implementation("org.graalvm.js:js:${graalVersion}")
                    implementation("org.graalvm.js:js:22.+")
                    implementation group: 'org.graalvm.js', name: 'js', version: '22.3.0', ext: 'jar'
                }
                """;
        rewriteRun(buildGradle(before));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target/pom.xml", ".cache/build.gradle", "GENERATED-sources/pom.xml",
            "installation/pom.xml", ".m2/pom.xml", ".gradle/build.gradle", "reports/pom.xml"})
    void excludesGeneratedInstallAndCacheParentDirectories(String path) {
        if (path.endsWith(".gradle")) rewriteRun(buildGradle("dependencies { implementation('org.graalvm.js:js:22.3.0') }",
                source -> source.path(path)));
        else rewriteRun(xml(pom("22.3.0"), source -> source.path(path)));
    }

    @Test
    void leafInstallNamesRemainEligible() {
        rewriteRun(buildGradle("dependencies { implementation('org.graalvm.js:js:22.3.1') }",
                        "dependencies { implementation('org.graalvm.js:js:24.2.1') }",
                        source -> source.path("install.gradle")));
    }

    @Test
    void remainsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("22.3.0"), pom("24.2.1")));
    }

    private static String pom(String version) {
        return project("<dependencies>" + dep(version) + "</dependencies>");
    }

    private static String dep(String version) {
        return "<dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId><version>" + version +
               "</version></dependency>";
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>app</artifactId>" +
               "<version>1</version>" + body + "</project>";
    }
}
