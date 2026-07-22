package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.json.Assertions.json;

class AngularCoreDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build()
                .activateRecipes("com.huawei.clouds.openrewrite.angular.UpgradeAngularCoreTo20_3_26"));
    }

    @ParameterizedTest(name = "spreadsheet source {0}")
    @ValueSource(strings = {
            "10.0.14", "10.2.5", "11.2.14", "12.2.10", "12.2.13", "12.2.14",
            "12.2.16", "12.2.17", "13.1.3", "13.2.6"
    })
    void upgradesEveryVisibleSpreadsheetSource(String source) {
        rewriteRun(json(
                "{\"dependencies\":{\"@angular/core\":\"" + source + "\"}}",
                "{\"dependencies\":{\"@angular/core\":\"20.3.26\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void npmDependencyIsUpdatedWithoutInventingLockfileChanges() {
        rewriteRun(json(
                """
                {"packageManager":"npm@10.9.0","dependencies":{"@angular/core":"10.0.14"}}
                """,
                """
                {"packageManager":"npm@10.9.0","dependencies":{"@angular/core":"20.3.26"}}
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void yarnDevDependencyIsUpdated() {
        rewriteRun(json(
                """
                {"packageManager":"yarn@4.9.2","devDependencies":{"@angular/core":"11.2.14"}}
                """,
                """
                {"packageManager":"yarn@4.9.2","devDependencies":{"@angular/core":"20.3.26"}}
                """,
                spec -> spec.path("packages/demo/package.json")
        ));
    }

    @Test
    void pnpmDependencyIsUpdated() {
        rewriteRun(json(
                """
                {"packageManager":"pnpm@10.13.1","dependencies":{"@angular/core":"12.2.17"}}
                """,
                """
                {"packageManager":"pnpm@10.13.1","dependencies":{"@angular/core":"20.3.26"}}
                """,
                spec -> spec.path("apps/web/package.json")
        ));
    }

    @Test
    void upgradesSafeScalarRangesFromRealWorkersListPokedexAndElasticFixtures() {
        rewriteRun(
                json("{\"dependencies\":{\"@angular/core\":\"^10.0.14\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"20.3.26\"}}",
                        spec -> spec.path("workers-list-client/package.json")),
                json("{\"dependencies\":{\"@angular/core\":\"~10.2.5\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"20.3.26\"}}",
                        spec -> spec.path("pokedex/package.json")),
                json("{\"devDependencies\":{\"@angular/core\":\"^12.2.17\"}}",
                        "{\"devDependencies\":{\"@angular/core\":\"20.3.26\"}}",
                        spec -> spec.path("apm-agent-rum-js/package.json"))
        );
    }

    @Test
    void preservesCompoundAndUnlistedRanges() {
        rewriteRun(json(
                """
                {
                  "dependencies":{"@angular/core":">=10.0.14 <14"},
                  "devDependencies":{"@angular/core":"10.2.5 || 12.2.17"},
                  "peerDependencies":{"@angular/core":"^12.2.6"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void preservesDynamicAndProtocolDeclarations() {
        rewriteRun(json(
                """
                {
                  "dependencies":{"@angular/core":"workspace:*"},
                  "devDependencies":{"@angular/core":"npm:@scope/angular-core@10.2.5"},
                  "peerDependencies":{"@angular/core":"${ANGULAR_VERSION}"},
                  "optionalDependencies":{"@angular/core":"latest"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void preservesUnlistedNewerAndTargetVersions() {
        rewriteRun(json(
                """
                {
                  "dependencies":{"@angular/core":"12.2.6"},
                  "devDependencies":{"@angular/core":"19.2.25"},
                  "peerDependencies":{"@angular/core":"20.3.26"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotRewriteCentralOwnersOrNestedMetadata() {
        rewriteRun(json(
                """
                {
                  "resolutions":{"@angular/core":"10.2.5"},
                  "overrides":{"@angular/core":"11.2.14"},
                  "pnpm":{"overrides":{"@angular/core":"12.2.17"}},
                  "catalog":{"@angular/core":"13.1.3"},
                  "metadata":{"dependencies":{"@angular/core":"10.0.14"}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void sharedDirectDeclarationsMoveTogetherAndAreIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json(
                        """
                        {
                          "dependencies":{"@angular/core":"11.2.14"},
                          "devDependencies":{"@angular/core":"12.2.10"},
                          "peerDependencies":{"@angular/core":"13.2.6"}
                        }
                        """,
                        """
                        {
                          "dependencies":{"@angular/core":"20.3.26"},
                          "devDependencies":{"@angular/core":"20.3.26"},
                          "peerDependencies":{"@angular/core":"20.3.26"}
                        }
                        """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void apacheNifiExactFixtureMigratesButSiblingAngularPackagesDoNot() {
        rewriteRun(json(
                """
                {"dependencies":{"@angular/common":"11.2.14","@angular/compiler":"11.2.14","@angular/core":"11.2.14","@angular/forms":"11.2.14"}}
                """,
                """
                {"dependencies":{"@angular/common":"11.2.14","@angular/compiler":"11.2.14","@angular/core":"20.3.26","@angular/forms":"11.2.14"}}
                """,
                spec -> spec.path("nifi-registry-web-ui/package.json")
        ));
    }
}
