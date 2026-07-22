package com.huawei.clouds.openrewrite.jcloverslf4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class JclOverSlf4jDependencyTest implements RewriteTest {
    static final String UPGRADE =
            "com.huawei.clouds.openrewrite.jcloverslf4j.UpgradeJclOverSlf4jDependencyTo2_0_17";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesEveryXlsxMavenVersion(String version) {
        rewriteRun(pomXml(pom(dependency(version)), pom(dependency("2.0.17"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesRootOwnedMavenProperty(String version) {
        rewriteRun(pomXml(
                pom("<properties><jcl.bridge.version>" + version + "</jcl.bridge.version></properties>" +
                    dependency("${jcl.bridge.version}")),
                pom("<properties><jcl.bridge.version>2.0.17</jcl.bridge.version></properties>" +
                    dependency("${jcl.bridge.version}"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesDependencyManagementAtRoot(String version) {
        rewriteRun(pomXml(
                pom("<dependencyManagement><dependencies>" + dependencyTag("jcl-over-slf4j", version) +
                    "</dependencies></dependencyManagement>"),
                pom("<dependencyManagement><dependencies>" + dependencyTag("jcl-over-slf4j", "2.0.17") +
                    "</dependencies></dependencyManagement>")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesProfileDependencyAndProfileProperty(String version) {
        String before = "<profiles><profile><id>logging</id><properties><jcl.version>" + version +
                        "</jcl.version></properties><dependencies>" +
                        dependencyTag("jcl-over-slf4j", "${jcl.version}") +
                        "</dependencies></profile></profiles>";
        String after = before.replace("<jcl.version>" + version + "</jcl.version>",
                "<jcl.version>2.0.17</jcl.version>");
        rewriteRun(pomXml(pom(before), pom(after)));
    }

    @Test
    void upgradesProfileDependencyManagement() {
        rewriteRun(pomXml(
                pom("<profiles><profile><id>managed</id><dependencyManagement><dependencies>" +
                    dependencyTag("jcl-over-slf4j", "1.7.36") +
                    "</dependencies></dependencyManagement></profile></profiles>"),
                pom("<profiles><profile><id>managed</id><dependencyManagement><dependencies>" +
                    dependencyTag("jcl-over-slf4j", "2.0.17") +
                    "</dependencies></dependencyManagement></profile></profiles>")));
    }

    @Test
    void doesNotRepurposeSharedMavenProperty() {
        rewriteRun(pomXml(pom(
                "<properties><logging.version>1.7.36</logging.version></properties><dependencies>" +
                dependencyTag("jcl-over-slf4j", "${logging.version}") +
                dependencyTag("slf4j-api", "${logging.version}") + "</dependencies>")));
    }

    @Test
    void resolvesSameNamedPropertiesWithinTheirMavenScope() {
        String before =
                "<properties><jcl.version>9.9.9</jcl.version></properties>" +
                "<profiles><profile><id>logging</id><properties><jcl.version>1.7.36</jcl.version></properties>" +
                "<dependencies>" + dependencyTag("jcl-over-slf4j", "${jcl.version}") +
                "</dependencies></profile></profiles>";
        rewriteRun(pomXml(pom(before), pom(before.replace(
                "<profile><id>logging</id><properties><jcl.version>1.7.36</jcl.version>",
                "<profile><id>logging</id><properties><jcl.version>2.0.17</jcl.version>"))));
    }

    @Test
    void profileBridgeDoesNotRetargetRootCompanions() {
        String before = "<dependencies>" + dependencyTag("slf4j-api", "1.7.36") +
                "</dependencies><profiles><profile><id>logging</id><dependencies>" +
                dependencyTag("jcl-over-slf4j", "1.7.36") + "</dependencies></profile></profiles>";
        String after = before.replace(
                dependencyTag("jcl-over-slf4j", "1.7.36"),
                dependencyTag("jcl-over-slf4j", "2.0.17"));
        rewriteRun(spec -> spec.recipe(new MigrateSelectedJclSlf4jFamilyDependencies()),
                pomXml(pom(before), pom(after)));
    }

    @Test
    void rootBridgeCanAlignProfileCompanions() {
        String before = "<dependencies>" + dependencyTag("jcl-over-slf4j", "1.7.36") +
                "</dependencies><profiles><profile><id>logging</id><dependencies>" +
                dependencyTag("slf4j-simple", "1.7.36") + "</dependencies></profile></profiles>";
        rewriteRun(spec -> spec.recipe(new MigrateSelectedJclSlf4jFamilyDependencies()),
                pomXml(pom(before), pom(before.replace("1.7.36", "2.0.17"))));
    }

    @Test
    void leavesManagedVersionAndBomOwnershipExternal() {
        rewriteRun(pomXml(pom(
                "<dependencyManagement><dependencies>" +
                "<dependency><groupId>org.slf4j</groupId><artifactId>slf4j-bom</artifactId><version>2.0.16</version><type>pom</type><scope>import</scope></dependency>" +
                "</dependencies></dependencyManagement><dependencies>" +
                "<dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId></dependency>" +
                "</dependencies>")));
    }

    @Test
    void leavesMavenClassifierAndNonJarVariantsAlone() {
        rewriteRun(pomXml(pom("<dependencies>" +
                "<dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId><version>1.7.36</version><classifier>tests</classifier></dependency>" +
                "<dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId><version>1.7.32</version><type>test-jar</type></dependency>" +
                "</dependencies>")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesEveryXlsxGradleGroovyVersion(String version) {
        rewriteRun(buildGradle(gradle(version, "runtimeOnly"), gradle("2.0.17", "runtimeOnly")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesEveryXlsxGradleKotlinVersion(String version) {
        rewriteRun(spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradleKts(kotlinGradle(version), kotlinGradle("2.0.17")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "implementation", "runtimeOnly", "testImplementation"})
    void preservesTopLevelGradleConfiguration(String configuration) {
        rewriteRun(buildGradle(gradle("1.7.30", configuration), gradle("2.0.17", configuration)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesGradleMapNotation(String version) {
        rewriteRun(buildGradle(
                "plugins { id 'java' }\ndependencies {\n    runtimeOnly group: 'org.slf4j', name: 'jcl-over-slf4j', version: '" + version + "'\n}\n",
                "plugins { id 'java' }\ndependencies {\n    runtimeOnly group: 'org.slf4j', name: 'jcl-over-slf4j', version: '2.0.17'\n}\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.7.29", "1.7.31", "1.7.33", "1.7.35", "2.0.0", "2.0.9", "2.0.16",
            "2.0.17", "1.6.6", "[1.7,2.0)"})
    void leavesUnlistedDynamicAndManagedMavenVersions(String version) {
        rewriteRun(pomXml(pom(dependency(version))));
    }

    @Test
    void leavesUnlistedPropertyVersion() {
        rewriteRun(pomXml(pom("<properties><slf4j.version>1.7.35</slf4j.version></properties>" +
                dependency("${slf4j.version}"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.7.29", "1.7.31", "1.7.+", "2.0.0", "2.0.17"})
    void leavesUnlistedGradleVersions(String version) {
        rewriteRun(buildGradle(gradle(version, "implementation")));
    }

    @Test
    void leavesGradleVariablesPlatformsAndVariantsAlone() {
        rewriteRun(buildGradle(
                "plugins { id 'java' }\ndef v = '1.7.36'\ndependencies {\n" +
                "    implementation \"org.slf4j:jcl-over-slf4j:$v\"\n" +
                "    implementation platform('org.slf4j:slf4j-bom:1.7.36')\n" +
                "    runtimeOnly 'org.slf4j:jcl-over-slf4j:1.7.36:tests'\n" +
                "    runtimeOnly group: 'org.slf4j', name: 'jcl-over-slf4j', version: '1.7.36', classifier: 'tests'\n}\n"));
    }

    @Test
    void excludesNestedBuildscriptConstraintsAndSubprojects() {
        rewriteRun(buildGradle(
                "plugins { id 'java' }\nbuildscript { dependencies { classpath 'org.slf4j:jcl-over-slf4j:1.7.36' } }\n" +
                "dependencies { constraints { implementation 'org.slf4j:jcl-over-slf4j:1.7.36' } }\n" +
                "subprojects { dependencies { implementation 'org.slf4j:jcl-over-slf4j:1.7.36' } }\n"));
    }

    @Test
    void excludesEveryOuterGradleMethodOwner() {
        rewriteRun(buildGradle(
                "plugins { id 'java' }\nconfigure(rootProject) { dependencies { " +
                "implementation 'org.slf4j:jcl-over-slf4j:1.7.36' } }\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target/pom.xml", "build/pom.xml", "generated/pom.xml", "Generated-Sources/pom.xml",
            "install/pom.xml", "installation/pom.xml", "vendor/pom.xml", ".gradle/pom.xml", ".m2/pom.xml",
            ".yarn/pom.xml", ".cache/pom.xml", "node_modules/pkg/pom.xml"})
    void excludesInstalledGeneratedAndBuiltMavenPaths(String path) {
        rewriteRun(pomXml(pom(dependency("1.7.36")), source -> source.path(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"build/generated/build.gradle", "generated-client/build.gradle", "install/build.gradle",
            "installation/build.gradle", "vendor/build.gradle", ".gradle/cache/build.gradle", ".yarn/build.gradle"})
    void excludesInstalledGeneratedAndBuiltGradlePaths(String path) {
        rewriteRun(buildGradle(gradle("1.7.36", "implementation"), source -> source.path(path)));
    }

    @Test
    void exactWhitelistAndStrictRecipeAreDiscoverableValidAndIdempotent() {
        assertEquals(Set.of("1.7.30", "1.7.32", "1.7.36"),
                AbstractSelectedJclDependencyRecipe.SOURCE_VERSIONS);
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom(dependency("1.7.36")), pom(dependency("2.0.17"))));
        Recipe recipe = environment().activateRecipes(UPGRADE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> UPGRADE.equals(candidate.getName())));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jcloverslf4j")
                .scanYamlResources()
                .build();
    }

    private static String pom(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dependency(String version) {
        return "<dependencies>" + dependencyTag("jcl-over-slf4j", version) + "</dependencies>";
    }

    private static String dependencyTag(String artifact, String version) {
        return "<dependency><groupId>org.slf4j</groupId><artifactId>" + artifact + "</artifactId><version>" + version + "</version></dependency>";
    }

    private static String gradle(String version, String configuration) {
        return "plugins { id 'java' }\ndependencies {\n    " + configuration + " 'org.slf4j:jcl-over-slf4j:" + version + "'\n}\n";
    }

    private static String kotlinGradle(String version) {
        return "plugins { java }\ndependencies {\n    runtimeOnly(\"org.slf4j:jcl-over-slf4j:" + version + "\")\n}\n";
    }
}
