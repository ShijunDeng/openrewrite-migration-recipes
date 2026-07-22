package com.huawei.clouds.openrewrite.singlespaangular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class SingleSpaAngularWorkspaceTest implements RewriteTest {
    @ParameterizedTest(name = "migrates workspace shape {0}")
    @MethodSource("workspaceShapes")
    void migratesRelevantConflictFreeBuildTargets(String label, String before, String after, String path) {
        rewriteRun(spec -> spec.recipe(new MigrateSingleSpaAngularWorkspace()),
                json(before, after, source -> source.path(path)));
    }

    static Stream<Arguments> workspaceShapes() {
        return Stream.of(
                Arguments.of("architect", "{\"projects\":{\"app\":{\"architect\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"browser\":\"src/main.single-spa.ts\",\"customWebpackConfig\":{\"path\":\"extra-webpack.config.js\"}}}}}}}",
                        "{\"projects\":{\"app\":{\"architect\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:browser\",\"options\":{\"main\":\"src/main.single-spa.ts\",\"customWebpackConfig\":{\"path\":\"extra-webpack.config.js\"}}}}}}}", "angular.json"),
                Arguments.of("targets", "{\"projects\":{\"app\":{\"targets\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"browser\":\"src/main.ts\",\"extraWebpackConfig\":\"webpack.js\"}}}}}}",
                        "{\"projects\":{\"app\":{\"targets\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:browser\",\"options\":{\"main\":\"src/main.ts\",\"extraWebpackConfig\":\"webpack.js\"}}}}}}", "workspace.json"),
                Arguments.of("project-json", "{\"targets\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"browser\":\"src/main.ts\",\"customWebpackConfig\":{\"path\":\"webpack.js\"}}}}}",
                        "{\"targets\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:browser\",\"options\":{\"main\":\"src/main.ts\",\"customWebpackConfig\":{\"path\":\"webpack.js\"}}}}}", "project.json"));
    }

    @Test
    void preservesMainBrowserCollisionAndUnrelatedApplicationBuilder() {
        rewriteRun(spec -> spec.recipe(new MigrateSingleSpaAngularWorkspace()),
                json("{\"projects\":{\"app\":{\"architect\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"main\":\"src/legacy.ts\",\"browser\":\"src/main.ts\",\"customWebpackConfig\":{\"path\":\"webpack.js\"}}}}}}}", source -> source.path("angular.json")),
                json("{\"projects\":{\"admin\":{\"architect\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"browser\":\"src/main.ts\"}}}}}}", source -> source.path("angular.json")));
    }

    @Test
    void doesNotRenameBrowserOptionOwnedByAnAlreadyBrowserBuilder() {
        rewriteRun(spec -> spec.recipe(new MigrateSingleSpaAngularWorkspace()),
                json("{\"projects\":{\"app\":{\"targets\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:browser\",\"options\":{\"browser\":\"src/unrecognized.ts\",\"customWebpackConfig\":{\"path\":\"webpack.js\"}}}}}}}",
                        source -> source.path("angular.json")));
    }

    @Test
    void changesOnlyBuildOptionsNotNestedOrServeLookalikes() {
        rewriteRun(spec -> spec.recipe(new MigrateSingleSpaAngularWorkspace()),
                json("{\"projects\":{\"app\":{\"architect\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"browser\":\"src/main.ts\",\"customWebpackConfig\":{\"path\":\"webpack.js\",\"browser\":\"nested.js\"}}},\"serve\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"browser\":\"serve.ts\"}}}}}}",
                        "{\"projects\":{\"app\":{\"architect\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:browser\",\"options\":{\"main\":\"src/main.ts\",\"customWebpackConfig\":{\"path\":\"webpack.js\",\"browser\":\"nested.js\"}}},\"serve\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"browser\":\"serve.ts\"}}}}}}",
                        source -> source.path("angular.json")));
    }

    @ParameterizedTest(name = "marks workspace risk {0}")
    @MethodSource("workspaceRisks")
    void marksPreciseWorkspaceRisks(String keyValue, String message) {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularWorkspaceRisks()),
                json("{\"singleSpa\":true,\"projects\":{\"app\":{\"targets\":{\"build\":{" + keyValue + "}}}}}", source -> source.path("angular.json")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> workspaceRisks() {
        return Stream.of(
                Arguments.of("\"builder\":\"single-spa-angular:build\"", "pre-Angular-8"),
                Arguments.of("\"builder\":\"single-spa-angular:dev-server\"", "pre-Angular-8"),
                Arguments.of("\"builder\":\"@angular-devkit/build-angular:application\"", "application builder is not supported"),
                Arguments.of("\"customWebpackConfig\":{\"path\":\"webpack.js\"}", "build/deployment owner"),
                Arguments.of("\"extraWebpackConfig\":\"webpack.js\"", "build/deployment owner"),
                Arguments.of("\"libraryTarget\":\"system\"", "build/deployment owner"),
                Arguments.of("\"excludeAngularDependencies\":true", "build/deployment owner"),
                Arguments.of("\"outputPath\":\"dist/app\"", "build/deployment owner"),
                Arguments.of("\"deployUrl\":\"/app/\"", "build/deployment owner"),
                Arguments.of("\"browserTarget\":\"app:build\"", "build/deployment owner"),
                Arguments.of("\"buildTarget\":\"app:build\"", "build/deployment owner"));
    }

    @Test
    void markersIgnoreUnrelatedAndExcludedWorkspaceFiles() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularWorkspaceRisks()),
                json("{\"builder\":\"@angular-devkit/build-angular:application\"}", source -> source.path("angular.json")),
                json("{\"singleSpa\":true,\"metadata\":{\"outputPath\":\"not-a-build-owner\"}}",
                        source -> source.path("workspace.json")),
                json("{\"singleSpa\":true,\"builder\":\"single-spa-angular:build\"}", source -> source.path("dist/angular.json")),
                json("{\"singleSpa\":true,\"builder\":\"single-spa-angular:build\"}", source -> source.path("angular.fixture.json")));
    }

    @Test
    void workspaceAutoAndMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateSingleSpaAngularWorkspace()).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"projects\":{\"a\":{\"targets\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"browser\":\"src/main.ts\",\"customWebpackConfig\":{\"path\":\"webpack.js\"}}}}}}}",
                        "{\"projects\":{\"a\":{\"targets\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:browser\",\"options\":{\"main\":\"src/main.ts\",\"customWebpackConfig\":{\"path\":\"webpack.js\"}}}}}}}", source -> source.path("angular.json")));
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularWorkspaceRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"singleSpa\":true,\"projects\":{\"a\":{\"targets\":{\"build\":{\"builder\":\"single-spa-angular:build\"}}}}}", source -> source.path("angular.json")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            String message = "pre-Angular-8";
                            assertTrue(printed.indexOf(message) == printed.lastIndexOf(message), printed);
                        })));
    }

    @Test
    void applicationCollisionReceivesReviewMarker() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularWorkspaceRisks()),
                json("{\"projects\":{\"app\":{\"architect\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"main\":\"a.ts\",\"browser\":\"b.ts\",\"customWebpackConfig\":{\"path\":\"webpack.js\"}}}}}}}",
                        source -> source.path("angular.json").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "application builder is not supported");
                            assertFalse(after.printAll().contains("@angular-devkit/build-angular:browser"), after.printAll());
                        })));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
