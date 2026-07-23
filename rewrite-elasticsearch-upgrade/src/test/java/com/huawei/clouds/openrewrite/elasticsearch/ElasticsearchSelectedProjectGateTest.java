package com.huawei.clouds.openrewrite.elasticsearch;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class ElasticsearchSelectedProjectGateTest implements RewriteTest {
    private static final String PREFIX =
            "com.huawei.clouds.openrewrite.elasticsearch.";
    private static final String IMAGE =
            "docker.elastic.co/elasticsearch/elasticsearch:7.9.2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(gatedSourceRecipes())
                .parser(ElasticsearchTestSupport.parser());
    }

    @Test
    void exactMavenLiteralEnablesOfficialAutoAndSourceRisks() {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom("1.17.6"),
                        source -> source.path("pom.xml")),
                selectedSource("Selected", "src/test/java/Selected.java"));
    }

    @Test
    void exactExclusiveMavenPropertyEnablesTheSameGate() {
        rewriteRun(
                xml(ElasticsearchTestSupport.project(
                                "<properties><tc.elasticsearch>1.17.6</tc.elasticsearch></properties>" +
                                "<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency(
                                        "${tc.elasticsearch}", "") +
                                "</dependencies>"),
                        source -> source.path("pom.xml")),
                selectedSource("PropertySelected",
                        "src/test/java/PropertySelected.java"));
    }

    @Test
    void sharedOrShadowedMavenPropertyCannotAuthorizeSourceWork() {
        rewriteRun(
                xml(ElasticsearchTestSupport.project(
                                "<properties><v>1.17.6</v></properties>" +
                                "<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("${v}", "") +
                                ElasticsearchTestSupport.dependency(
                                        "example", "shared-owner", "${v}", "") +
                                "</dependencies>"),
                        source -> source.path("shared/pom.xml")),
                untouchedSource("SharedProperty",
                        "shared/src/test/java/SharedProperty.java"),
                xml(ElasticsearchTestSupport.project(
                                "<properties><v>1.17.6</v></properties>" +
                                "<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("${v}", "") +
                                "</dependencies><profiles><profile><id>it</id>" +
                                "<properties><v>1.17.6</v></properties></profile></profiles>"),
                        source -> source.path("shadowed/pom.xml")),
                untouchedSource("ShadowedProperty",
                        "shadowed/src/test/java/ShadowedProperty.java"));
    }

    @Test
    void groovyStringAndBothMapFormsEnableTheSameGate() {
        rewriteRun(
                buildGradle(
                        "dependencies { testImplementation 'org.testcontainers:elasticsearch:1.17.6' }",
                        source -> source.path("string/build.gradle")),
                selectedSource("StringSelected",
                        "string/src/test/java/StringSelected.java"),
                buildGradle(
                        "dependencies { testImplementation group: 'org.testcontainers', name: 'elasticsearch', version: '1.17.6' }",
                        source -> source.path("map/build.gradle")),
                selectedSource("MapSelected",
                        "map/src/test/java/MapSelected.java"),
                buildGradle(
                        "dependencies { testImplementation([group: 'org.testcontainers', name: 'elasticsearch', version: '1.17.6']) }",
                        source -> source.path("map-literal/build.gradle")),
                selectedSource("MapLiteralSelected",
                        "map-literal/src/test/java/MapLiteralSelected.java"));
    }

    @Test
    void kotlinStringEnablesTheSameGate() {
        rewriteRun(
                buildGradleKts(
                        "dependencies { testImplementation(\"org.testcontainers:elasticsearch:1.17.6\") }",
                        source -> source.path("kotlin/build.gradle.kts")),
                selectedSource("KotlinSelected",
                        "kotlin/src/test/java/KotlinSelected.java"));
    }

    @Test
    void targetFutureOffListServerUnrelatedAndMissingRootsBlockAllSourceWork() {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom("1.21.4"),
                        source -> source.path("target/pom.xml")),
                untouchedSource("Target", "target/src/test/java/Target.java"),
                xml(ElasticsearchTestSupport.pom("1.21.5"),
                        source -> source.path("future/pom.xml")),
                untouchedSource("Future", "future/src/test/java/Future.java"),
                xml(ElasticsearchTestSupport.pom("1.20.6"),
                        source -> source.path("off-list/pom.xml")),
                untouchedSource("OffList", "off-list/src/test/java/OffList.java"),
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.serverDependency("7.10.2") +
                                "</dependencies>"),
                        source -> source.path("server/pom.xml")),
                untouchedSource("Server", "server/src/test/java/Server.java"),
                xml(ElasticsearchTestSupport.project(
                                "<dependencies>" +
                                ElasticsearchTestSupport.dependency(
                                        "example", "unrelated", "1.17.6", "") +
                                "</dependencies>"),
                        source -> source.path("unrelated/pom.xml")),
                untouchedSource("Unrelated",
                        "unrelated/src/test/java/Unrelated.java"),
                untouchedSource("NoBuildRoot",
                        "missing/src/test/java/NoBuildRoot.java"));
    }

    @Test
    void everyMixedIdentityOrVersionRootBlocksAllSourceWork() {
        rewriteRun(
                xml(mixedPom(ElasticsearchTestSupport.testcontainersDependency(
                                "1.21.4", "")),
                        source -> source.path("target/pom.xml")),
                untouchedSource("TargetMixed",
                        "target/src/test/java/TargetMixed.java"),
                xml(mixedPom(ElasticsearchTestSupport.testcontainersDependency(
                                "1.21.5", "")),
                        source -> source.path("future/pom.xml")),
                untouchedSource("FutureMixed",
                        "future/src/test/java/FutureMixed.java"),
                xml(mixedPom(ElasticsearchTestSupport.testcontainersDependency(
                                "1.20.6", "")),
                        source -> source.path("off-list/pom.xml")),
                untouchedSource("OffListMixed",
                        "off-list/src/test/java/OffListMixed.java"),
                xml(mixedPom(ElasticsearchTestSupport.serverDependency("7.10.2")),
                        source -> source.path("server/pom.xml")),
                untouchedSource("ServerMixed",
                        "server/src/test/java/ServerMixed.java"));
    }

    @Test
    void outOfScopeDependencyIdentitiesAlsoMakeTheRootAmbiguous() {
        rewriteRun(
                xml(ElasticsearchTestSupport.project(
                                "<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency(
                                        "1.17.6", "") +
                                "</dependencies><build><plugins><plugin>" +
                                "<groupId>example</groupId><artifactId>tool</artifactId>" +
                                "<dependencies>" +
                                ElasticsearchTestSupport.serverDependency("7.10.2") +
                                "</dependencies></plugin></plugins></build>"),
                        source -> source.path("maven/pom.xml")),
                untouchedSource("MavenPluginConflict",
                        "maven/src/test/java/MavenPluginConflict.java"),
                buildGradle("""
                        buildscript {
                          dependencies {
                            classpath 'org.testcontainers:elasticsearch:1.21.4'
                          }
                        }
                        dependencies {
                          testImplementation 'org.testcontainers:elasticsearch:1.17.6'
                        }
                        """, source -> source.path("gradle/build.gradle")),
                untouchedSource("GradleBuildscriptConflict",
                        "gradle/src/test/java/GradleBuildscriptConflict.java"),
                buildGradle("""
                        dependencies {
                          testImplementation 'org.testcontainers:elasticsearch:1.17.6'
                          testImplementation libs.elasticsearch
                        }
                        """, source -> source.path("catalog/build.gradle")),
                untouchedSource("GradleCatalogConflict",
                        "catalog/src/test/java/GradleCatalogConflict.java"));
    }

    @Test
    void nearestNestedBuildBoundaryStopsOuterEligibility() {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom("1.17.6"),
                        source -> source.path("pom.xml")),
                selectedSource("RootSelected",
                        "src/test/java/RootSelected.java"),
                xml(ElasticsearchTestSupport.pom("1.21.4"),
                        source -> source.path("nested/pom.xml")),
                untouchedSource("NestedTarget",
                        "nested/src/test/java/NestedTarget.java"));
    }

    private static org.openrewrite.test.SourceSpecs selectedSource(
            String className, String path) {
        return java(source(className), spec -> spec.path(path)
                .after(actual -> actual)
                .afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(
                            "new ElasticsearchContainer(\"" + IMAGE + "\")"), printed);
                    assertTrue(printed.contains(
                            FindElasticsearchContainerSourceRisks.OPERATIONAL_DEFAULTS), printed);
                }));
    }

    private static org.openrewrite.test.SourceSpecs untouchedSource(
            String className, String path) {
        return java(source(className), spec -> spec.path(path)
                .afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("new ElasticsearchContainer()"), printed);
                    assertFalse(printed.contains(IMAGE), printed);
                    assertFalse(printed.contains(
                            FindElasticsearchContainerSourceRisks.OPERATIONAL_DEFAULTS), printed);
                }));
    }

    private static String source(String className) {
        return """
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class %s {
                    ElasticsearchContainer container = new ElasticsearchContainer();
                }
                """.formatted(className);
    }

    private static String mixedPom(String secondDependency) {
        return ElasticsearchTestSupport.project("<dependencies>" +
               ElasticsearchTestSupport.testcontainersDependency("1.17.6", "") +
               secondDependency + "</dependencies>");
    }

    private static Recipe gatedSourceRecipes() {
        return ElasticsearchTestSupport.environment().activateRecipes(
                PREFIX + "MarkSelectedElasticsearchProjects",
                PREFIX + "MakeSelectedElasticsearchContainerImageExplicit",
                PREFIX + "FindSelectedElasticsearchContainer1_21_4SourceRisks");
    }
}
