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

class SingleSpaAngularManifestRiskTest implements RewriteTest {
    @ParameterizedTest(name = "marks companion {0}")
    @MethodSource("companions")
    void marksExplicitCompanionOwners(String dependency, String message) {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularManifestRisks()),
                json("{\"dependencies\":{\"single-spa-angular\":\"9.2.0\"," + dependency + "}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> companions() {
        return Stream.of(
                Arguments.of("\"@angular/core\":\"^18.0.0\"", "publishes an @angular/core >=16 peer"),
                Arguments.of("\"@angular/router\":\"^18.0.0\"", "one aligned framework set"),
                Arguments.of("\"@angular/cli\":\"^18.0.0\"", "one aligned framework set"),
                Arguments.of("\"@angular-devkit/build-angular\":\"^18.0.0\"", "one aligned framework set"),
                Arguments.of("\"zone.js\":\"~0.14.0\"", "Exactly one compatible zone.js"),
                Arguments.of("\"@angular-builders/custom-webpack\":\"18.0.0\"", "Align the custom-webpack builder"),
                Arguments.of("\"single-spa\":\"^6.0.0\"", "accepts single-spa >=4"),
                Arguments.of("\"style-loader\":\"^3.3.1\"", "target peer/build companion"),
                Arguments.of("\"json5\":\"^2.2.3\"", "target peer/build companion"),
                Arguments.of("\"tslib\":\"^2.3.0\"", "target peer/build companion"),
                Arguments.of("\"rxjs\":\"~7.8.0\"", "Validate RxJS"));
    }

    @ParameterizedTest(name = "marks unresolved declaration {0}")
    @MethodSource("unresolvedDeclarations")
    void marksNonTargetDirectOwners(String declaration) {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularManifestRisks()),
                json("{\"dependencies\":{\"single-spa-angular\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "not the exact workbook target"))));
    }

    static Stream<Arguments> unresolvedDeclarations() {
        return Stream.of(
                Arguments.of("4.3.1"), Arguments.of("^8.1.0"), Arguments.of(">=8"),
                Arguments.of("workspace:^8.1.0"), Arguments.of("npm:@fork/single-spa-angular@8.1.0"),
                Arguments.of("latest"));
    }

    @Test
    void marksNestedOverrideAndResolutionOwnership() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularManifestRisks()),
                json("{\"dependencies\":{\"single-spa-angular\":\"9.2.0\"},\"overrides\":{\"single-spa-angular\":\"8.1.0\"},\"pnpm\":{\"overrides\":{\"single-spa-angular\":\"7.1.0\"}},\"resolutions\":{\"single-spa-angular\":\"6.3.1\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "override/resolution independently owns");
                        })));
    }

    @Test
    void targetOperatorsAreAcceptedAndSiblingPackageDoesNotActivateModule() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularManifestRisks()),
                json("{\"dependencies\":{\"single-spa-angular\":\"^9.2.0\"}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"single-spa-angular\":\"~9.2.0\"}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"single-spa-angular-extra\":\"8.1.0\",\"@angular/core\":\"12.0.0\"}}",
                        source -> source.path("package.json").afterRecipe(after ->
                                assertFalse(after.printAll().contains("aligned framework"), after.printAll()))));
    }

    @Test
    void ignoresGeneratedParentAndOtherJson() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularManifestRisks()),
                json("{\"dependencies\":{\"single-spa-angular\":\"8.1.0\"}}", source -> source.path("generated-fixtures/package.json")),
                json("{\"dependencies\":{\"single-spa-angular\":\"8.1.0\"}}", source -> source.path("package-lock.json")));
    }

    @Test
    void markerIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularManifestRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"single-spa-angular\":\">=8\"}}", source -> source.path("package.json")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            String message = "not the exact workbook target";
                            assertTrue(printed.indexOf(message) == printed.lastIndexOf(message), printed);
                        })));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
