package com.huawei.clouds.openrewrite.ws;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class WsManifestRiskTest implements RewriteTest {
    @ParameterizedTest
    @ValueSource(strings = {
            ">=8.16.0", "8.x", "workspace:^8.16.0", "github:websockets/ws#8.20.0", "latest",
            "8.17.0", "8.19.0", "8.20.1", "9.0.0"
    })
    void marksSkippedDirectDeclarations(String declaration) {
        rewriteRun(spec -> spec.recipe(new FindWsManifestRisks()), json(
                "{\"dependencies\":{\"ws\":\"" + declaration + "\"}}",
                source -> source.path("package.json").after(actual -> actual).afterRecipe(document ->
                        assertTrue(document.printAll().contains("Strict migration skipped")))));
    }

    @Test
    void leavesWorkbookAndTargetDeclarationsUnmarked() {
        rewriteRun(spec -> spec.recipe(new FindWsManifestRisks()), json(
                "{\"dependencies\":{\"ws\":\"8.5.0\"},\"devDependencies\":{\"ws\":\"^8.16.0\"},\"peerDependencies\":{\"ws\":\"~8.18.3\"},\"optionalDependencies\":{\"ws\":\"8.21.0\"}}",
                source -> source.path("package.json").afterRecipe(document ->
                        assertFalse(document.printAll().contains("Strict migration skipped")))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "8.6", ">=8", "^9.0.0", "~9.4.0", "<10", "8 || 20"})
    void marksNodeRuntimeFloorsThatMayViolateTarget(String engine) {
        rewriteRun(spec -> spec.recipe(new FindWsManifestRisks()), json(
                "{\"engines\":{\"node\":\"" + engine + "\"},\"dependencies\":{\"ws\":\"8.16.0\"}}",
                source -> source.path("package.json").after(actual -> actual).afterRecipe(document ->
                        assertTrue(document.printAll().contains("requires Node >=10")))));
    }

    @ParameterizedTest
    @ValueSource(strings = {">=10", "10", "^12.22.0", ">=18.14.0", "20.x", "lts/*"})
    void acceptsClearlyCompatibleOrUnparseableNodeFloors(String engine) {
        rewriteRun(spec -> spec.recipe(new FindWsManifestRisks()), json(
                "{\"engines\":{\"node\":\"" + engine + "\"},\"dependencies\":{\"ws\":\"8.16.0\"}}",
                source -> source.path("package.json").afterRecipe(document ->
                        assertFalse(document.printAll().contains("requires Node >=10")))));
    }

    @Test
    void marksAllCentralOwnershipShapes() {
        rewriteRun(spec -> spec.recipe(new FindWsManifestRisks()), json(
                """
                {
                  "dependencies": {"ws": "8.20.0"},
                  "overrides": {"ws": "8.20.0"},
                  "resolutions": {"ws": "8.18.3"},
                  "pnpm": {"overrides": {"ws": "8.16.0"}},
                  "catalog": {"ws": "8.5.0"}
                }
                """, source -> source.path("package.json").after(actual -> actual).afterRecipe(document -> {
                    String printed = document.printAll();
                    assertTrue(printed.contains("owns ws independently"));
                    assertTrue(printed.contains("\"dependencies\": {\"ws\": \"8.20.0\"}"));
                })));
    }

    @Test
    void marksNativeAddonsTypesAndBrowserBoundary() {
        rewriteRun(spec -> spec.recipe(new FindWsManifestRisks()), json(
                """
                {
                  "browser": "src/browser.js",
                  "dependencies": {
                    "ws": "8.18.3",
                    "bufferutil": "^4.0.9",
                    "utf-8-validate": "^6.0.5",
                    "@types/ws": "^8.18.1"
                  }
                }
                """, source -> source.path("package.json").after(actual -> actual).afterRecipe(document -> {
                    String printed = document.printAll();
                    assertTrue(printed.contains("optional ws native addon"));
                    assertTrue(printed.contains("relies on @types/ws"));
                    assertTrue(printed.contains("Node WebSocket implementation"));
                })));
    }

    @Test
    void doesNotMarkAddonsTypesOrBrowserWithoutWsOwnership() {
        rewriteRun(spec -> spec.recipe(new FindWsManifestRisks()), json(
                "{\"browser\":\"src/browser.js\",\"dependencies\":{\"bufferutil\":\"4.0.9\",\"@types/ws\":\"8.18.1\"}}",
                source -> source.path("package.json").afterRecipe(document ->
                        assertFalse(document.printAll().contains("~~")))));
    }

    @Test
    void ignoresLockfilesOrdinaryJsonAndExcludedManifests() {
        rewriteRun(spec -> spec.recipe(new FindWsManifestRisks()),
                json("{\"dependencies\":{\"ws\":\">=8.16.0\"}}", source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"ws\":\">=8.16.0\"}}", source -> source.path("fixtures/config.json")),
                json("{\"dependencies\":{\"ws\":\">=8.16.0\"}}", source -> source.path("vendor/tool/package.json")),
                json("{\"dependencies\":{\"ws\":\">=8.16.0\"}}", source -> source.path("generated-client/package.json")));
    }
}
