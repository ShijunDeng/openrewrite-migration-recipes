package com.huawei.clouds.openrewrite.reactresizable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class ReactResizableProjectRiskTest implements RewriteTest {
    @ParameterizedTest(name = "marks project risk {0}")
    @MethodSource("projectRisks")
    void marksExactPackageGraphRisks(String label, String source, String message) {
        rewriteRun(spec -> spec.recipe(new FindReactResizableProjectRisks()),
                json(source, input -> input.path("package.json").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> projectRisks() {
        return Stream.of(
                Arguments.of("react-16.2", pkgWith("\"react\":\"16.2.0\""), "requires both React and ReactDOM >=16.3"),
                Arguments.of("react-dom-15", pkgWith("\"react-dom\":\"^15.6.2\""), "requires both React and ReactDOM >=16.3"),
                Arguments.of("react-dynamic", pkgWith("\"react\":\"workspace:*\""), "requires both React and ReactDOM >=16.3"),
                Arguments.of("react-prerelease", pkgWith("\"react\":\"^16.3.0-beta.1\""), "requires both React and ReactDOM >=16.3"),
                Arguments.of("draggable", pkgWith("\"react-draggable\":\"^4.0.3\""), "depends on react-draggable ^4.5.0"),
                Arguments.of("types", pkgWith("\"@types/react-resizable\":\"^1.7.4\""), "ships Flow source rather than TypeScript declarations"),
                Arguments.of("unselected", "{\"dependencies\":{\"react-resizable\":\"^3.0.5\"}}", "outside the workbook's exact 1.11.1 source"),
                Arguments.of("range", "{\"dependencies\":{\"react-resizable\":\">=1.11.1\"}}", "outside the workbook's exact 1.11.1 source"),
                Arguments.of("alias", "{\"dependencies\":{\"react-resizable\":\"npm:fork@1.11.1\"}}", "outside the workbook's exact 1.11.1 source"),
                Arguments.of("override", "{\"dependencies\":{\"react-resizable\":\"3.1.3\"},\"overrides\":{\"react-resizable\":\"1.11.1\"}}", "override/resolution owns react-resizable"),
                Arguments.of("resolution", "{\"dependencies\":{\"react-resizable\":\"3.1.3\"},\"resolutions\":{\"react-resizable\":\"1.11.1\"}}", "override/resolution owns react-resizable"),
                Arguments.of("pnpm-override", "{\"dependencies\":{\"react-resizable\":\"3.1.3\"},\"pnpm\":{\"overrides\":{\"react-resizable\":\"1.11.1\"}}}", "override/resolution owns react-resizable")
        );
    }

    @Test
    void leavesCompatiblePeersAndTargetGraphAlone() {
        rewriteRun(spec -> spec.recipe(new FindReactResizableProjectRisks()),
                json("{\"dependencies\":{\"react-resizable\":\"3.1.3\",\"react\":\"^18.2.0\",\"react-dom\":\"^18.2.0\",\"react-draggable\":\"^4.5.0\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void projectFinderSkipsIrrelevantAndGeneratedManifests() {
        rewriteRun(spec -> spec.recipe(new FindReactResizableProjectRisks()),
                json("{\"dependencies\":{\"react\":\"15.0.0\"}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"react-resizable\":\"3.0.5\",\"react\":\"15.0.0\"}}",
                        source -> source.path("dist/package.json")));
    }

    @Test
    void marksNpmLockGraph() {
        rewriteRun(spec -> spec.recipe(new FindReactResizableResourceRisks()),
                json("{\"lockfileVersion\":3,\"packages\":{\"node_modules/react-resizable\":{\"version\":\"1.11.1\"}}}",
                        source -> source.path("package-lock.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Regenerate this lockfile"))));
    }

    @Test
    void marksYarnAndPnpmLockGraphs() {
        rewriteRun(spec -> spec.recipe(new FindReactResizableResourceRisks()),
                text("react-resizable@^1.11.1:\n  version \"1.11.1\"\n",
                        source -> source.path("yarn.lock").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "absence of unintended 1.x duplicates"))),
                yaml("lockfileVersion: 9\npackages:\n  react-resizable@1.11.1: {}\n",
                        source -> source.path("pnpm-lock.yaml").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "react-draggable ^4.5.0"))));
    }

    @ParameterizedTest(name = "marks application style {0}")
    @MethodSource("styles")
    void marksHandleCssContracts(String path, String source) {
        rewriteRun(spec -> spec.recipe(new FindReactResizableResourceRisks()),
                text(source, input -> input.path(path).after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "Application CSS overrides react-resizable geometry"))));
    }

    static Stream<Arguments> styles() {
        return Stream.of(
                Arguments.of("src/layout.css", ".react-resizable-handle { width: 8px; height: 8px; }"),
                Arguments.of("src/layout.scss", ".panel { &.react-resizable { position: relative; } }"),
                Arguments.of("src/layout.less", ".react-resizable-handle-nw { cursor: nw-resize; }")
        );
    }

    @Test
    void resourceFinderLeavesUnrelatedAndGeneratedAssetsAlone() {
        rewriteRun(spec -> spec.recipe(new FindReactResizableResourceRisks()),
                text("left-pad@1.0.0:\n  version \"1.0.0\"", source -> source.path("yarn.lock")),
                text(".resizable-handle { width: 8px; }", source -> source.path("src/layout.css")),
                text(".react-resizable-handle { width: 8px; }", source -> source.path("dist/layout.css")));
    }

    @Test
    void recommendedRecipePerformsAutoAndMarksRemainingRiskAcrossTwoCycles() {
        rewriteRun(spec -> spec.recipe(ReactResizableDependencyTest.environment()
                                .activateRecipes(ReactResizableDependencyTest.MIGRATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json(ReactResizableDependencyTest.pkg("dependencies", "^1.11.1"),
                        ReactResizableDependencyTest.pkg("dependencies", "3.1.3"), source -> source.path("package.json")),
                javascript(
                        "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={(axis)=><span/>} onResizeStop={save}/>;",
                        source -> source.path("src/App.jsx").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "(axis, resizeHandleRef)=><span ref={resizeHandleRef}/>");
                            assertContains(printed, "last onResize size");
                        })),
                text(".react-resizable-handle { right: 0; }",
                        source -> source.path("src/app.css").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Application CSS overrides"))));
    }

    private static String pkgWith(String entry) {
        return "{\"dependencies\":{\"react-resizable\":\"3.1.3\"," + entry + "}}";
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
