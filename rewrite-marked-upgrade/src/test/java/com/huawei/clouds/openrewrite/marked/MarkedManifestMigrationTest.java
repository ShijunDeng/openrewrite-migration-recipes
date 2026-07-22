package com.huawei.clouds.openrewrite.marked;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class MarkedManifestMigrationTest implements RewriteTest {
    @Test
    void removesUniqueOrdinaryTypesDevDependencyForTarget() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantMarkedTypes()),
                json(
                        """
                        {"dependencies":{"marked":"17.0.6"},"devDependencies":{"@types/lodash":"4.17.0","@types/marked":"4.0.8","@types/node":"20.10.0"}}
                        """,
                        """
                        {"dependencies":{"marked":"17.0.6"},"devDependencies":{"@types/lodash":"4.17.0","@types/node":"20.10.0"}}
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void keepsEmptyDevDependenciesObject() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantMarkedTypes()),
                json(
                        "{\"dependencies\":{\"marked\":\"17.0.6\"},\"devDependencies\":{\"@types/marked\":\"^5.0.2\"}}",
                        "{\"dependencies\":{\"marked\":\"17.0.6\"},\"devDependencies\":{}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void doesNotRemoveTypesForComplexMainRange() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantMarkedTypes()),
                json(
                        "{\"dependencies\":{\"marked\":\">=4.2.12 <18\"},\"devDependencies\":{\"@types/marked\":\"4.0.8\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void duplicateAndNestedTypesOwnersBlockRemoval() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantMarkedTypes()),
                json(
                        """
                        {"dependencies":{"marked":"17.0.6"},"devDependencies":{"@types/marked":"4.0.8"},"pnpm":{"overrides":{"@types/marked":"5.0.2"}}}
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void aliasTypesOwnerBlocksRemoval() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantMarkedTypes()),
                json(
                        "{\"dependencies\":{\"marked\":\"17.0.6\"},\"devDependencies\":{\"@types/marked\":\"npm:@company/marked-types@5.0.2\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void marksComplexRangeAtItsValue() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17ManifestRisks()),
                json(
                        "{\"dependencies\":{\"marked\":\">=4.2.12 <18\"}}",
                        "{\"dependencies\":{\"marked\":/*~~(Strict migration skipped this complex range, protocol, dynamic tag, alias, variable, decorated, or unlisted Marked version; select a tested 17.x constraint and regenerate the lockfile)~~>*/\">=4.2.12 <18\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void marksNode12EngineAndNode16Types() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17ManifestRisks()),
                json(
                        "{\"engines\":{\"node\":\">=12.0\"},\"dependencies\":{\"marked\":\"17.0.6\"},\"devDependencies\":{\"@types/node\":\"^16.4.13\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("requires Node >=20"));
                                    assertTrue(printed.indexOf("requires Node >=20") != printed.lastIndexOf("requires Node >=20"));
                                })
                )
        );
    }

    @Test
    void marksCommonJsPackageMode() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17ManifestRisks()),
                json(
                        "{\"type\":\"commonjs\",\"dependencies\":{\"marked\":\"17.0.6\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("ESM-only")))
                )
        );
    }

    @Test
    void node20AndEsmTargetNeedNoManifestMarker() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17ManifestRisks()),
                json(
                        "{\"type\":\"module\",\"engines\":{\"node\":\">=20\"},\"dependencies\":{\"marked\":\"17.0.6\"},\"devDependencies\":{\"@types/node\":\"20.10.0\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void unrelatedManifestAndLockfileAreNotMarked() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17ManifestRisks()),
                json(
                        "{\"engines\":{\"node\":\"12\"},\"dependencies\":{\"other\":\"1.0.0\"}}",
                        source -> source.path("package.json")
                ),
                json(
                        "{\"dependencies\":{\"marked\":\"4.2.12\"}}",
                        source -> source.path("package-lock.json")
                )
        );
    }
}
