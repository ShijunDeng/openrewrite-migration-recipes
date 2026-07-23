package com.huawei.clouds.openrewrite.junrar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeJunrarDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                "com.huawei.clouds.openrewrite.junrar.UpgradeJunrarTo7_5_10"));
    }

    @ParameterizedTest(name = "Maven upgrades exact source {0}")
    @ValueSource(strings = {"7.5.5", "7.5.8"})
    void upgradesEveryMavenSource(String version) {
        rewriteRun(xml(pom(version), pom("7.5.10"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "Groovy upgrades exact source {0}")
    @ValueSource(strings = {"7.5.5", "7.5.8"})
    void upgradesEveryGroovySource(String version) {
        rewriteRun(buildGradle(
                "dependencies { implementation 'com.github.junrar:junrar:" + version + "' }",
                "dependencies { implementation 'com.github.junrar:junrar:7.5.10' }"));
    }

    @ParameterizedTest(name = "Kotlin upgrades exact source {0}")
    @ValueSource(strings = {"7.5.5", "7.5.8"})
    void upgradesEveryKotlinSource(String version) {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"com.github.junrar:junrar:" + version + "\") }",
                "dependencies { implementation(\"com.github.junrar:junrar:7.5.10\") }"));
    }

    @Test
    void preservesMavenMetadataAndUnrelatedDependencies() {
        String metadata = "<scope>runtime</scope><optional>true</optional>" +
                "<exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>";
        rewriteRun(xml(
                project("<dependencies>" + target("7.5.5", metadata) +
                        "<dependency><groupId>example</groupId><artifactId>junrar</artifactId>" +
                        "<version>7.5.5</version></dependency></dependencies>"),
                project("<dependencies>" + target("7.5.10", metadata) +
                        "<dependency><groupId>example</groupId><artifactId>junrar</artifactId>" +
                        "<version>7.5.5</version></dependency></dependencies>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesDependencyManagementAndProfiles() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + target("7.5.5", "") +
                        "</dependencies></dependencyManagement><profiles><profile><id>rar</id><dependencies>" +
                        target("7.5.5", "<type>jar</type>") + "</dependencies></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + target("7.5.10", "") +
                        "</dependencies></dependencyManagement><profiles><profile><id>rar</id><dependencies>" +
                        target("7.5.10", "<type>jar</type>") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveRootPropertyAcrossRootAndProfileReferences() {
        rewriteRun(xml(
                project("<properties><junrar.version>7.5.5</junrar.version></properties><dependencies>" +
                        target("${junrar.version}", "") +
                        "</dependencies><profiles><profile><id>rar</id><dependencies>" +
                        target("${junrar.version}", "") + "</dependencies></profile></profiles>"),
                project("<properties><junrar.version>7.5.10</junrar.version></properties><dependencies>" +
                        target("${junrar.version}", "") +
                        "</dependencies><profiles><profile><id>rar</id><dependencies>" +
                        target("${junrar.version}", "") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveProfilePropertyWithoutChangingRootProperty() {
        rewriteRun(xml(
                project("<properties><junrar.version>7.6.0</junrar.version></properties><profiles><profile>" +
                        "<id>rar</id><properties><junrar.patch>7.5.8</junrar.patch></properties><dependencies>" +
                        target("${junrar.patch}", "") + "</dependencies></profile></profiles>"),
                project("<properties><junrar.version>7.6.0</junrar.version></properties><profiles><profile>" +
                        "<id>rar</id><properties><junrar.patch>7.5.10</junrar.patch></properties><dependencies>" +
                        target("${junrar.patch}", "") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void preservesSharedDuplicateAttributeAndShadowedProperties() {
        rewriteRun(
                xml("<project marker=\"${junrar.version}\"><modelVersion>4.0.0</modelVersion>" +
                        "<groupId>x</groupId><artifactId>a</artifactId><version>1</version>" +
                        "<properties><junrar.version>7.5.5</junrar.version></properties><dependencies>" +
                        target("${junrar.version}", "") + "</dependencies></project>",
                        source -> source.path("attribute/pom.xml")),
                xml(project("<properties><junrar.version>7.5.5</junrar.version>" +
                                "<junrar.version>7.5.5</junrar.version></properties><dependencies>" +
                                target("${junrar.version}", "") + "</dependencies>"),
                        source -> source.path("duplicate/pom.xml")),
                xml(project("<properties><shared.version>7.5.8</shared.version></properties><dependencies>" +
                                target("${shared.version}", "") +
                                "<dependency><groupId>example</groupId><artifactId>other</artifactId>" +
                                "<version>${shared.version}</version></dependency></dependencies>"),
                        source -> source.path("shared/pom.xml")),
                xml(project("<properties><junrar.version>7.5.5</junrar.version></properties><dependencies>" +
                                target("${junrar.version}", "") +
                                "</dependencies><profiles><profile><id>rar</id><properties>" +
                                "<junrar.version>7.5.8</junrar.version></properties></profile></profiles>"),
                        source -> source.path("shadowed/pom.xml")));
    }

    @ParameterizedTest(name = "never downgrades Maven/Gradle higher version {0}")
    @ValueSource(strings = {
            "7.5.11", "7.6.0", "7.6.1", "7.10.0", "8.0.0", "8.0.0-beta1",
            "9.1", "10.0.0", "20.3.26", "999999999999999999999.0"
    })
    void preservesHigherVersionsInEveryBuildDsl(String version) {
        rewriteRun(
                xml(pom(version), source -> source.path("maven-" + version + "/pom.xml")),
                buildGradle("dependencies { implementation 'com.github.junrar:junrar:" +
                        version + "' }", source -> source.path("groovy-" + version + "/build.gradle")),
                buildGradleKts("dependencies { implementation(\"com.github.junrar:junrar:" +
                        version + "\") }", source -> source.path("kotlin-" + version + "/build.gradle.kts")));
    }

    @ParameterizedTest(name = "preserves non-whitelisted fixed version {0}")
    @ValueSource(strings = {
            "1.0", "6.0.0", "7.0.0", "7.4.1", "7.5.0", "7.5.4", "7.5.6",
            "7.5.7", "7.5.9", "7.5.10-rc1", "7.5.10.Final", "7.5"
    })
    void preservesUnknownFixedVersions(String version) {
        rewriteRun(
                xml(pom(version), source -> source.path("maven-" + version + "/pom.xml")),
                buildGradle("dependencies { implementation 'com.github.junrar:junrar:" +
                        version + "' }", source -> source.path("groovy-" + version + "/build.gradle")));
    }

    @ParameterizedTest(name = "preserves Maven variant {0}")
    @ValueSource(strings = {
            "<classifier>sources</classifier>",
            "<classifier>tests</classifier>",
            "<type>test-jar</type>",
            "<type>zip</type>",
            "<type>pom</type>"
    })
    void preservesMavenVariants(String metadata) {
        rewriteRun(xml(project("<dependencies>" + target("7.5.5", metadata) + "</dependencies>"),
                source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "preserves Gradle variant {0}")
    @ValueSource(strings = {
            "com.github.junrar:junrar:7.5.5:sources",
            "com.github.junrar:junrar:7.5.8:tests",
            "com.github.junrar:junrar:7.5.5@zip",
            "com.github.junrar:junrar",
            "xcom.github.junrar:junrar:7.5.5",
            "com.github.junrar:junrar-extra:7.5.5"
    })
    void preservesGradleVariantOrLookalikeCoordinates(String coordinate) {
        rewriteRun(buildGradle("dependencies { implementation '" + coordinate + "' }"));
    }

    @Test
    void upgradesSupportedGroovyMapForms() {
        rewriteRun(
                buildGradle(
                        "dependencies { runtimeOnly group: 'com.github.junrar', name: 'junrar', version: '7.5.5' }",
                        "dependencies { runtimeOnly group: 'com.github.junrar', name: 'junrar', version: '7.5.10' }",
                        source -> source.path("named/build.gradle")),
                buildGradle(
                        "dependencies { testImplementation([group: 'com.github.junrar', name: 'junrar', version: '7.5.8']) }",
                        "dependencies { testImplementation([group: 'com.github.junrar', name: 'junrar', version: '7.5.10']) }",
                        source -> source.path("literal/build.gradle")));
    }

    @ParameterizedTest(name = "preserves Groovy map variant {0}")
    @ValueSource(strings = {"classifier: 'sources'", "classifier: 'tests'", "ext: 'zip'",
            "type: 'test-jar'", "variant: 'shadow'"})
    void preservesGroovyMapVariants(String variant) {
        rewriteRun(buildGradle("dependencies { implementation group: 'com.github.junrar', " +
                "name: 'junrar', version: '7.5.5', " + variant + " }"));
    }

    @Test
    void preservesDynamicCatalogPlatformNestedAndPluginOwners() {
        rewriteRun(
                buildGradle("""
                        def v = '7.5.5'
                        buildscript { dependencies { implementation 'com.github.junrar:junrar:7.5.5' } }
                        dependencies {
                          implementation "com.github.junrar:junrar:${v}"
                          implementation libs.junrar
                          implementation platform('com.github.junrar:junrar:7.5.5')
                        }
                        project(':child') {
                          dependencies { implementation 'com.github.junrar:junrar:7.5.5' }
                        }
                        fake { dependencies { implementation 'com.github.junrar:junrar:7.5.5' } }
                        """),
                buildGradleKts("""
                        val v = "7.5.8"
                        dependencies {
                            implementation("com.github.junrar:junrar:$v")
                            implementation(libs.junrar)
                            implementation(platform("com.github.junrar:junrar:7.5.8"))
                        }
                        """),
                xml(project("<dependencies>" + target(null, "") +
                        target("${missing.version}", "") +
                        target("[7.5.5,7.5.10]", "") + "</dependencies>"),
                        source -> source.path("pom.xml")));
    }

    @Test
    void conflictingVersionsInOneMavenOwnerBlockEveryAutomaticChange() {
        rewriteRun(xml(project("<dependencies>" + target("7.5.5", "") +
                target("7.5.10", "") + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void conflictingVersionsAcrossBuildFilesAtOneRootBlockEveryAutomaticChange() {
        rewriteRun(
                xml(pom("7.5.5"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'com.github.junrar:junrar:7.5.8' }",
                        source -> source.path("build.gradle")));
    }

    @Test
    void duplicateOwnersAcrossBuildFilesAtOneRootAreNotAssumedEquivalent() {
        rewriteRun(
                xml(pom("7.5.5"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'com.github.junrar:junrar:7.5.5' }",
                        source -> source.path("build.gradle")));
    }

    @Test
    void nestedUnselectedBuildBoundaryDoesNotInheritOuterEligibility() {
        rewriteRun(
                xml(pom("7.5.5"), pom("7.5.10"), source -> source.path("pom.xml")),
                xml(project(""), source -> source.path("nested/pom.xml")),
                buildGradle("dependencies { implementation 'com.github.junrar:junrar:7.5.8' }",
                        "dependencies { implementation 'com.github.junrar:junrar:7.5.10' }",
                        source -> source.path("nested-independent/build.gradle")));
    }

    @Test
    void skipsGeneratedCacheInstallAndReportBuildFiles() {
        rewriteRun(
                xml(pom("7.5.5"), source -> source.path("target/generated/pom.xml")),
                xml(pom("7.5.8"), source -> source.path("build/pom.xml")),
                xml(pom("7.5.5"), source -> source.path("reports/pom.xml")),
                buildGradle("dependencies { implementation 'com.github.junrar:junrar:7.5.5' }",
                        source -> source.path("install/build.gradle")),
                buildGradle("dependencies { implementation 'com.github.junrar:junrar:7.5.8' }",
                        source -> source.path(".gradle/build.gradle")),
                buildGradleKts("dependencies { implementation(\"com.github.junrar:junrar:7.5.5\") }",
                        source -> source.path("generated-sources/build.gradle.kts")));
    }

    @Test
    void exactTargetIsStableAndRecipeIsIdempotent() {
        rewriteRun(xml(pom("7.5.10"), source -> source.path("target-version/pom.xml")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("7.5.8"), pom("7.5.10"), source -> source.path("pom.xml")));
    }

    private static String pom(String version) {
        return project("<dependencies>" + target(version, "") + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String target(String version, String metadata) {
        return "<dependency><groupId>com.github.junrar</groupId><artifactId>junrar</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") +
               metadata + "</dependency>";
    }
}
