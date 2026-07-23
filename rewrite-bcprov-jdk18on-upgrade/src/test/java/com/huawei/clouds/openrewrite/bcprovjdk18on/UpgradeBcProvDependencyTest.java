package com.huawei.clouds.openrewrite.bcprovjdk18on;

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
import static org.openrewrite.xml.Assertions.xml;

class UpgradeBcProvDependencyTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.bcprovjdk18on.UpgradeBcProvJdk18onTo1_84";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateBcProvJdk18onTo1_84";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(UPGRADE));
    }

    @ParameterizedTest(name = "Maven upgrades selected source {0}")
    @ValueSource(strings = {"1.75", "1.78", "1.78.1", "1.79", "1.80", "1.81"})
    void upgradesEveryVisibleWorkbookVersion(String version) {
        rewriteRun(xml(pom(version), pom("1.84"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesDirectDependencyAndPreservesMetadata() {
        rewriteRun(pomXml(
                dependency("1.79", "<scope>runtime</scope><optional>true</optional><exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>"),
                dependency("1.84", "<scope>runtime</scope><optional>true</optional><exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>")));
    }

    @Test
    void upgradesDependencyManagementAndExplicitJar() {
        rewriteRun(pomXml(
                project("<dependencyManagement><dependencies>" + target("1.78", "<type>jar</type>") +
                        "</dependencies></dependencyManagement><dependencies>" + target(null, "") + "</dependencies>"),
                project("<dependencyManagement><dependencies>" + target("1.84", "<type>jar</type>") +
                        "</dependencies></dependencyManagement><dependencies>" + target(null, "") + "</dependencies>")));
    }

    @Test
    void upgradesRootAndProfileLiteralOwners() {
        rewriteRun(pomXml(
                project("<dependencies>" + target("1.79", "") + "</dependencies><profiles><profile><id>p</id><dependencies>" + target("1.78", "") + "</dependencies></profile></profiles>"),
                project("<dependencies>" + target("1.84", "") + "</dependencies><profiles><profile><id>p</id><dependencies>" + target("1.84", "") + "</dependencies></profile></profiles>")));
    }

    @Test
    void upgradesExclusiveRootPropertyAcrossRootAndProfile() {
        rewriteRun(pomXml(
                propertyProject("1.81", "bcprov.version"),
                propertyProject("1.84", "bcprov.version")));
    }

    @Test
    void upgradesExclusiveProfilePropertyWithoutChangingRoot() {
        rewriteRun(pomXml(
                project("<properties><bc.version>1.80</bc.version></properties><profiles><profile><id>p</id><properties><provider.version>1.78.1</provider.version></properties><dependencies>" + target("${provider.version}", "") + "</dependencies></profile></profiles>"),
                project("<properties><bc.version>1.80</bc.version></properties><profiles><profile><id>p</id><properties><provider.version>1.84</provider.version></properties><dependencies>" + target("${provider.version}", "") + "</dependencies></profile></profiles>")));
    }

    @Test
    void preservesSharedAttributeDuplicateAndShadowedProperties() {
        rewriteRun(
                xml("<project marker=\"${bc.version}\"><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId><version>1</version><properties><bc.version>1.79</bc.version></properties><dependencies>" + target("${bc.version}", "") + "</dependencies></project>", source -> source.path("pom.xml")),
                xml(project("<properties><bc.version>1.79</bc.version><bc.version>1.79</bc.version></properties><dependencies>" + target("${bc.version}", "") + "</dependencies>"), source -> source.path("duplicate/pom.xml")),
                xml(project("<properties><bc.version>1.79</bc.version></properties><dependencies>" + target("${bc.version}", "") + "<dependency><groupId>org.bouncycastle</groupId><artifactId>bcutil-jdk18on</artifactId><version>${bc.version}</version></dependency></dependencies>"), source -> source.path("shared/pom.xml")),
                xml(project("<properties><bc.version>1.79</bc.version></properties><dependencies>" + target("${bc.version}", "") + "</dependencies><profiles><profile><id>p</id><properties><bc.version>1.78.1</bc.version></properties></profile></profiles>"), source -> source.path("shadow/pom.xml"))
        );
    }

    @Test
    void preservesExternalOwnersRangesOutsideTargetAndFuture() {
        rewriteRun(xml(project("<dependencies>" +
                target(null, "") + target("${missing}", "") + target("[1.75,1.84)", "") +
                target("1.76", "") + target("1.84", "") + target("2.0.0", "") +
                "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesBcprovButNeverChangesIndependentBcpkix1811Target() {
        String before = project("<dependencies>" + target("1.81", "") +
                "<dependency><groupId>org.bouncycastle</groupId><artifactId>bcpkix-jdk18on</artifactId>" +
                "<version>1.81.1</version></dependency></dependencies>");
        String after = project("<dependencies>" + target("1.84", "") +
                "<dependency><groupId>org.bouncycastle</groupId><artifactId>bcpkix-jdk18on</artifactId>" +
                "<version>1.81.1</version></dependency></dependencies>");
        rewriteRun(pomXml(before, after));
    }

    @Test
    void preservesVariantsPluginDependenciesAndNestedLookalikes() {
        rewriteRun(xml(project("<dependencies>" + target("1.79", "<classifier>sources</classifier>") +
                        target("1.79", "<type>test-jar</type>") +
                        "<dependency><groupId>example</groupId><artifactId>bcprov-jdk18on</artifactId><version>1.79</version></dependency>" +
                        "<dependency><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on-extra</artifactId><version>1.79</version></dependency>" +
                        "</dependencies><build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><version>1</version><dependencies>" +
                        target("1.79", "") + "</dependencies></plugin></plugins></build>"), source -> source.path("pom.xml")),
                xml("<root>" + project("<dependencies>" + target("1.79", "") + "</dependencies>") + "</root>",
                        source -> source.path("nested/pom.xml")));
    }

    @Test
    void upgradesGroovyStringsMapsAndKotlinStrings() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.79' }",
                        "dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.84' }", s -> s.path("string.gradle")),
                buildGradle("dependencies { runtimeOnly group: 'org.bouncycastle', name: 'bcprov-jdk18on', version: '1.80' }",
                        "dependencies { runtimeOnly group: 'org.bouncycastle', name: 'bcprov-jdk18on', version: '1.84' }", s -> s.path("map.gradle")),
                buildGradle("dependencies { testImplementation([group: 'org.bouncycastle', name: 'bcprov-jdk18on', version: '1.80']) }",
                        "dependencies { testImplementation([group: 'org.bouncycastle', name: 'bcprov-jdk18on', version: '1.84']) }", s -> s.path("map-literal.gradle")),
                buildGradleKts("dependencies { implementation(\"org.bouncycastle:bcprov-jdk18on:1.75\") }",
                        "dependencies { implementation(\"org.bouncycastle:bcprov-jdk18on:1.84\") }")
        );
    }

    @Test
    void preservesInterpolationCatalogPlatformsFourPartAndVariants() {
        rewriteRun(
                buildGradle("""
                        def v = '1.79'
                        dependencies {
                          implementation "org.bouncycastle:bcprov-jdk18on:${v}"
                          implementation libs.bouncycastle.bcprov.jdk18on
                          implementation 'org.bouncycastle:bcprov-jdk18on:1.79:sources'
                          implementation group: 'org.bouncycastle', name: 'bcprov-jdk18on', version: '1.79', classifier: 'sources'
                          implementation([group: 'org.bouncycastle', name: 'bcprov-jdk18on', version: '1.79', ext: 'zip'])
                          implementation platform('org.bouncycastle:bc-bom:1.79')
                        }
                        """),
                buildGradleKts("""
                        val v = "1.79"
                        dependencies {
                            implementation("org.bouncycastle:bcprov-jdk18on:$v")
                            implementation(libs.bouncycastle.bcprov.jdk18on)
                        }
                        """));
    }

    @Test
    void ignoresBuildscriptCustomAndNestedProjectDependencies() {
        rewriteRun(buildGradle("""
                buildscript { dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.79' } }
                dependencies { generated { implementation 'org.bouncycastle:bcprov-jdk18on:1.79' } }
                project(':child') { dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.79' } }
                fake { dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.79' } }
                """));
    }

    @Test
    void skipsGeneratedBuildFilesAndIsIdempotent() {
        rewriteRun(
                xml(pom("1.79"), source -> source.path("target/generated/pom.xml")),
                buildGradle("dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.79' }", s -> s.path("install/build.gradle")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("1.79"), pom("1.84")));
    }

    @Test
    void discoversAllPublicRecipesAndAggregateParity() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        String[] names = {
                UPGRADE,
                "com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateDeterministicBcProv1_84Java",
                "com.huawei.clouds.openrewrite.bcprovjdk18on.FindBcProv1_84BuildRisks",
                "com.huawei.clouds.openrewrite.bcprovjdk18on.FindBcProv1_84SourceAndConfigurationRisks",
                MIGRATE
        };
        for (String name : names) assertEquals(name, environment.activateRecipes(name).getName());
        Recipe aggregate = environment.activateRecipes(MIGRATE);
        assertTrue(aggregate.getRecipeList().size() >= 4);
        assertEquals(UPGRADE, aggregate.getRecipeList().get(0).getName());
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static String pom(String version) {
        return project("<dependencies>" + target(version, "") + "</dependencies>");
    }

    private static String dependency(String version, String metadata) {
        return project("<dependencies>" + target(version, metadata) + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String target(String version, String metadata) {
        return "<dependency><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + metadata + "</dependency>";
    }

    private static String propertyProject(String version, String property) {
        return project("<properties><" + property + ">" + version + "</" + property + "></properties>" +
                "<dependencies>" + target("${" + property + "}", "") + "</dependencies>" +
                "<profiles><profile><id>p</id><dependencies>" + target("${" + property + "}", "") +
                "</dependencies></profile></profiles>");
    }
}
