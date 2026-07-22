package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.json.Assertions.json;

class AngularCommonDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build()
                .activateRecipes("com.huawei.clouds.openrewrite.angular.UpgradeAngularCommonTo20_3_26"));
    }

    @ParameterizedTest(name = "spreadsheet source {0}")
    @ValueSource(strings = {
            "10.0.14", "10.2.5", "11.2.14", "12.2.10", "12.2.13", "12.2.14",
            "12.2.16", "12.2.17", "13.1.3", "13.2.6"
    })
    void upgradesEveryVisibleSpreadsheetSource(String source) {
        rewriteRun(json(
                "{\"dependencies\":{\"@angular/common\":\"" + source + "\"}}",
                "{\"dependencies\":{\"@angular/common\":\"20.3.26\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void supportsNpmYarnAndPnpmWorkspacePackageLocations() {
        rewriteRun(
                json("{\"packageManager\":\"npm@10.9.0\",\"dependencies\":{\"@angular/common\":\"10.0.14\"}}",
                        "{\"packageManager\":\"npm@10.9.0\",\"dependencies\":{\"@angular/common\":\"20.3.26\"}}",
                        spec -> spec.path("package.json")),
                json("{\"packageManager\":\"yarn@4.9.2\",\"devDependencies\":{\"@angular/common\":\"11.2.14\"}}",
                        "{\"packageManager\":\"yarn@4.9.2\",\"devDependencies\":{\"@angular/common\":\"20.3.26\"}}",
                        spec -> spec.path("packages/demo/package.json")),
                json("{\"packageManager\":\"pnpm@10.13.1\",\"optionalDependencies\":{\"@angular/common\":\"12.2.17\"}}",
                        "{\"packageManager\":\"pnpm@10.13.1\",\"optionalDependencies\":{\"@angular/common\":\"20.3.26\"}}",
                        spec -> spec.path("apps/web/package.json"))
        );
    }

    @Test
    void upgradesSafeRangeFromRealMvsLightwalletFixture() {
        rewriteRun(json(
                "{\"dependencies\":{\"@angular/common\":\"^10.0.14\"}}",
                "{\"dependencies\":{\"@angular/common\":\"20.3.26\"}}",
                spec -> spec.path("mvs-org/lightwallet/package.json")
        ));
    }

    @Test
    void preservesAllConstraintAndDynamicForms() {
        rewriteRun(json(
                """
                {
                  "dependencies":{"@angular/common":"~10.2.5"},
                  "devDependencies":{"@angular/common":">=13 <20"},
                  "peerDependencies":{"@angular/common":"workspace:*"},
                  "optionalDependencies":{"@angular/common":"${ANGULAR_VERSION}"}
                }
                """,
                """
                {
                  "dependencies":{"@angular/common":"20.3.26"},
                  "devDependencies":{"@angular/common":">=13 <20"},
                  "peerDependencies":{"@angular/common":"workspace:*"},
                  "optionalDependencies":{"@angular/common":"${ANGULAR_VERSION}"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void preservesAliasesTagsUnlistedNewerAndTarget() {
        rewriteRun(json(
                """
                {
                  "dependencies":{"@angular/common":"npm:@scope/common@10.2.5"},
                  "devDependencies":{"@angular/common":"latest"},
                  "peerDependencies":{"@angular/common":"12.2.6"},
                  "optionalDependencies":{"@angular/common":"20.3.26"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesCentralOwnersAndNestedMetadataUntouched() {
        rewriteRun(json(
                """
                {
                  "resolutions":{"@angular/common":"10.2.5"},
                  "overrides":{"@angular/common":"11.2.14"},
                  "pnpm":{"overrides":{"@angular/common":"12.2.17"}},
                  "catalog":{"@angular/common":"13.1.3"},
                  "metadata":{"dependencies":{"@angular/common":"10.0.14"}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void directSectionsMoveTogetherAndRecipeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json(
                        """
                        {
                          "dependencies":{"@angular/common":"11.2.14"},
                          "devDependencies":{"@angular/common":"12.2.10"},
                          "peerDependencies":{"@angular/common":"13.2.6"}
                        }
                        """,
                        """
                        {
                          "dependencies":{"@angular/common":"20.3.26"},
                          "devDependencies":{"@angular/common":"20.3.26"},
                          "peerDependencies":{"@angular/common":"20.3.26"}
                        }
                        """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void apacheNifiPinnedFixtureMigratesOnlyAngularCommon() {
        rewriteRun(json(
                "{\"dependencies\":{\"@angular/common\":\"11.2.14\",\"@angular/compiler\":\"11.2.14\",\"@angular/core\":\"11.2.14\",\"@angular/forms\":\"11.2.14\"}}",
                "{\"dependencies\":{\"@angular/common\":\"20.3.26\",\"@angular/compiler\":\"11.2.14\",\"@angular/core\":\"11.2.14\",\"@angular/forms\":\"11.2.14\"}}",
                spec -> spec.path("nifi-registry/nifi-registry-core/nifi-registry-web-ui/src/main/package.json")
        ));
    }
}
