package com.huawei.clouds.openrewrite.losslessjson;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class LosslessJsonManifestMigrationTest implements RewriteTest {
    @Test
    void removesSingleOrdinaryTypesDevDependencyForTarget() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantLosslessJsonTypes()),
                json(
                        """
                        {
                          "dependencies": {"lossless-json": "4.0.1"},
                          "devDependencies": {
                            "@types/lodash": "4.17.0",
                            "@types/lossless-json": "1.0.4",
                            "@types/node": "20.11.17"
                          }
                        }
                        """,
                        """
                        {
                          "dependencies": {"lossless-json": "4.0.1"},
                          "devDependencies": {
                            "@types/lodash": "4.17.0",
                            "@types/node": "20.11.17"
                          }
                        }
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void removesOnlyMemberAndKeepsEmptyDevDependenciesObject() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantLosslessJsonTypes()),
                json(
                        "{\"dependencies\":{\"lossless-json\":\"4.0.1\"},\"devDependencies\":{\"@types/lossless-json\":\"^1.0.1\"}}",
                        "{\"dependencies\":{\"lossless-json\":\"4.0.1\"},\"devDependencies\":{}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void doesNotRemoveTypesUntilMainDependencyIsExactTarget() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantLosslessJsonTypes()),
                json(
                        "{\"dependencies\":{\"lossless-json\":\"^2.0.8\"},\"devDependencies\":{\"@types/lossless-json\":\"1.0.4\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void duplicateTypesOwnershipIsNeverRemoved() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantLosslessJsonTypes()),
                json(
                        """
                        {
                          "dependencies": {"lossless-json": "4.0.1", "@types/lossless-json": "1.0.4"},
                          "devDependencies": {"@types/lossless-json": "1.0.4"}
                        }
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void nestedPnpmOverrideCountsAsSecondTypesOwner() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantLosslessJsonTypes()),
                json(
                        """
                        {
                          "dependencies": {"lossless-json": "4.0.1"},
                          "devDependencies": {"@types/lossless-json": "1.0.4"},
                          "pnpm": {"overrides": {"@types/lossless-json": "1.0.4"}}
                        }
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void protocolOrAliasTypesOwnershipIsNeverRemoved() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantLosslessJsonTypes()),
                json(
                        """
                        {"dependencies":{"lossless-json":"4.0.1"},"devDependencies":{"@types/lossless-json":"npm:@company/lossless-types@1.0.0"}}
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void typesOutsideDevDependenciesIsNeverRemoved() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantLosslessJsonTypes()),
                json(
                        "{\"dependencies\":{\"lossless-json\":\"4.0.1\",\"@types/lossless-json\":\"1.0.4\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void marksSkippedRangeAtExactValue() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonManifestRisks()),
                json(
                        "{\"dependencies\":{\"lossless-json\":\">=2.0.8 <3\"}}",
                        "{\"dependencies\":{\"lossless-json\":/*~~(Strict migration skipped this lossless-json range, dynamic tag, protocol, alias, variable, or unlisted version; select and lock an intended 4.x constraint explicitly)~~>*/\">=2.0.8 <3\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void marksNode16EngineAndTypesAtExactValues() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonManifestRisks()),
                json(
                        """
                        {"engines":{"node":"16.x"},"dependencies":{"lossless-json":"4.0.1"},"devDependencies":{"@types/node":"^16.4.13"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("dropped official Node.js 16 support"));
                                    assertTrue(printed.indexOf("dropped official Node.js 16 support") !=
                                               printed.lastIndexOf("dropped official Node.js 16 support"));
                                })
                )
        );
    }

    @Test
    void marksTargetProjectModuleModeAtRootValue() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonManifestRisks()),
                json(
                        "{\"type\":\"module\",\"dependencies\":{\"lossless-json\":\"4.0.1\"}}",
                        "{\"type\":/*~~(4.0.1 resolves its public root through conditional import/require exports; verify this project module mode, test runner, bundler, SSR, and mocks against the public root entry)~~>*/\"module\",\"dependencies\":{\"lossless-json\":\"4.0.1\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void supportedNodeAndPlainTargetNeedNoManifestMarker() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonManifestRisks()),
                json(
                        "{\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"lossless-json\":\"4.0.1\"},\"devDependencies\":{\"@types/node\":\"20.11.17\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void doesNotMarkNode16InUnrelatedManifest() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonManifestRisks()),
                json(
                        "{\"engines\":{\"node\":\"16.x\"},\"dependencies\":{\"other\":\"1.0.0\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void packageLockAndOrdinaryJsonAreNeverMigratedOrMarked() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonManifestRisks()),
                json(
                        "{\"dependencies\":{\"lossless-json\":\"2.0.11\"}}",
                        source -> source.path("package-lock.json")
                ),
                json(
                        "{\"dependencies\":{\"lossless-json\":\"2.0.11\"}}",
                        source -> source.path("fixture.json")
                )
        );
    }
}
