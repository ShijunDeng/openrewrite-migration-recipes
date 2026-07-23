package com.huawei.clouds.openrewrite.fastglob;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class FastGlobManifestRiskTest implements RewriteTest {
    @ParameterizedTest
    @ValueSource(strings = {
            ">=3.2.2", "3.2.2 || 3.3.3", "3.x", "workspace:^3.2.2",
            "npm:@company/fast-glob@3.2.2", "github:mrmlnc/fast-glob#3.2.2",
            "file:../fast-glob", "latest", "$fastGlobVersion", "3.2.12"
    })
    void marksSkippedDirectDeclarations(String declaration) {
        rewriteRun(spec -> spec.recipe(new FindFastGlobManifestRisks()),
                json("{\"dependencies\":{\"fast-glob\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("Strict migration skipped"));
                                    assertTrue(document.printAll().contains(declaration));
                                })));
    }

    @ParameterizedTest
    @ValueSource(strings = {">=6", ">=8", "8", "8.0.0", "^8.0.0", "~8.5.0"})
    void marksNodeRuntimeBelowTargetFloor(String engine) {
        rewriteRun(spec -> spec.recipe(new FindFastGlobManifestRisks()),
                json("{\"engines\":{\"node\":\"" + engine + "\"},\"dependencies\":{\"fast-glob\":\"3.3.3\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("requires Node >=8.6.0")))));
    }

    @Test
    void marksCentralOwnersWhenManifestUsesFastGlob() {
        rewriteRun(spec -> spec.recipe(new FindFastGlobManifestRisks()),
                json("{\"dependencies\":{\"fast-glob\":\"3.3.3\"},\"overrides\":{\"fast-glob\":\"3.2.2\"},\"resolutions\":{\"fast-glob\":\"3.2.12\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("central override")))),
                json("{\"devDependencies\":{\"fast-glob\":\"3.3.3\"},\"pnpm\":{\"overrides\":{\"tool\":{\"fast-glob\":\"3.2.2\"}}},\"catalogs\":{\"build\":{\"fast-glob\":\"3.2.2\"}}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("central override"));
                                    assertTrue(document.printAll().contains("3.2.2"));
                                })));
    }

    @Test
    void ignoresSupportedNodeAndTargetDeclarations() {
        rewriteRun(spec -> spec.recipe(new FindFastGlobManifestRisks()),
                json("{\"engines\":{\"node\":\">=8.6.0\"},\"dependencies\":{\"fast-glob\":\"^3.3.3\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~")))),
                json("{\"engines\":{\"node\":\">=18\"},\"devDependencies\":{\"fast-glob\":\"~3.3.3\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~")))),
                json("{\"engines\":{\"node\":\">=8.10.0\"},\"dependencies\":{\"fast-glob\":\"3.3.3\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~")))));
    }

    @Test
    void ignoresOrdinaryManifestLockfileAndExcludedTrees() {
        rewriteRun(spec -> spec.recipe(new FindFastGlobManifestRisks()),
                json("{\"engines\":{\"node\":\">=6\"},\"overrides\":{\"fast-glob\":\"3.2.2\"}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"fast-glob\":\"3.x\"}}",
                        source -> source.path("package-lock.json")),
                json("{\"engines\":{\"node\":\">=6\"},\"dependencies\":{\"fast-glob\":\"3.x\"}}",
                        source -> source.path("generated/package.json")));
    }
}
