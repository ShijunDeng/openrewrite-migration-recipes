package com.huawei.clouds.openrewrite.d3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

import java.util.stream.Stream;

class UpgradeD3Test implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.d3.UpgradeD3To7_9_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesBritechartsProductionDependency() {
        // Reduced from britecharts/britecharts at 8b1b2acb:
        // https://github.com/britecharts/britecharts/blob/8b1b2acb4b496ca70c12469850af304dae67bdaa/package.json
        rewriteRun(json(
                """
                {
                  "name": "britecharts",
                  "dependencies": {
                    "base-64": "^0.1.0",
                    "d3": "^5.16.0",
                    "lodash.assign": "^4.2.0"
                  }
                }
                """,
                """
                {
                  "name": "britecharts",
                  "dependencies": {
                    "base-64": "^0.1.0",
                    "d3": "7.9.0",
                    "lodash.assign": "^4.2.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesIpfsWebUiDevelopmentDependency() {
        // Reduced from ipfs/ipfs-webui at 818f09c3:
        // https://github.com/ipfs/ipfs-webui/blob/818f09c371b7a4bf30acecd0fbb94e59da978069/package.json
        rewriteRun(json(
                """
                {
                  "name": "ipfs-webui",
                  "devDependencies": {
                    "countly-sdk-web": "^19.8.0",
                    "d3": "^5.16.0",
                    "datatransfer-files-promise": "^1.3.1"
                  }
                }
                """,
                """
                {
                  "name": "ipfs-webui",
                  "devDependencies": {
                    "countly-sdk-web": "^19.8.0",
                    "d3": "7.9.0",
                    "datatransfer-files-promise": "^1.3.1"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesD3fcMonorepoDevelopmentDependency() {
        // Reduced from d3fc/d3fc at 55ee5942; this is a real npm-workspaces root manifest:
        // https://github.com/d3fc/d3fc/blob/55ee5942a0885ea073838afaaa89eba24cefab15/package.json
        rewriteRun(json(
                """
                {
                  "name": "@d3fc/d3fc-monorepo",
                  "private": true,
                  "workspaces": ["./packages/*"],
                  "devDependencies": {
                    "@types/d3": "^6.7.5",
                    "d3": "^6.7.0",
                    "rollup": "^2.79.1"
                  }
                }
                """,
                """
                {
                  "name": "@d3fc/d3fc-monorepo",
                  "private": true,
                  "workspaces": ["./packages/*"],
                  "devDependencies": {
                    "@types/d3": "^6.7.5",
                    "d3": "7.9.0",
                    "rollup": "^2.79.1"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesTeammatesAngularApplicationDependency() {
        // Reduced from TEAMMATES/teammates at e8270607; @types/d3 intentionally remains unchanged:
        // https://github.com/TEAMMATES/teammates/blob/e82706072141196191640375727edb302e54a55f/package.json
        rewriteRun(json(
                """
                {
                  "private": true,
                  "dependencies": {
                    "@angular/core": "^21.2.13",
                    "d3": "^7.8.5",
                    "handsontable": "^17.0.0"
                  },
                  "devDependencies": {
                    "@types/d3": "^7.4.3",
                    "typescript": "~5.9.3"
                  }
                }
                """,
                """
                {
                  "private": true,
                  "dependencies": {
                    "@angular/core": "^21.2.13",
                    "d3": "7.9.0",
                    "handsontable": "^17.0.0"
                  },
                  "devDependencies": {
                    "@types/d3": "^7.4.3",
                    "typescript": "~5.9.3"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"d3": "5.16.0"},
                  "devDependencies": {"d3": "6.6.2"},
                  "peerDependencies": {"d3": "7.1.1"},
                  "optionalDependencies": {"d3": "7.8.2"}
                }
                """,
                """
                {
                  "dependencies": {"d3": "7.9.0"},
                  "devDependencies": {"d3": "7.9.0"},
                  "peerDependencies": {"d3": "7.9.0"},
                  "optionalDependencies": {"d3": "7.9.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesEverySpreadsheetVersion() {
        rewriteRun(
                versionedPackage("apps/v5/package.json", "5.16.0"),
                versionedPackage("apps/v6-6/package.json", "6.6.2"),
                versionedPackage("apps/v6-7/package.json", "6.7.0"),
                versionedPackage("apps/v7-1/package.json", "7.1.1"),
                versionedPackage("apps/v7-8-2/package.json", "7.8.2"),
                versionedPackage("apps/v7-8-4/package.json", "7.8.4"),
                versionedPackage("apps/v7-8-5/package.json", "7.8.5")
        );
    }

    @ParameterizedTest(name = "upgrades {0} d3 declaration {1}")
    @MethodSource("selectedDeclarations")
    void upgradesEverySafeDeclarationInEveryDirectSection(String section, String declaration) {
        rewriteRun(json(
                "{\"" + section + "\":{\"d3\":\"" + declaration + "\"}}",
                "{\"" + section + "\":{\"d3\":\"7.9.0\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesSingleTildeButLeavesComplexRangesAndPrereleaseFormsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"d3": "~5.16.0"},
                  "devDependencies": {"d3": ">= 6.7.0 < 7"},
                  "peerDependencies": {"d3": "7.8.4 || ^7.8.5"},
                  "optionalDependencies": {"d3": "7.8.5-rc.1"}
                }
                """,
                """
                {
                  "dependencies": {"d3": "7.9.0"},
                  "devDependencies": {"d3": ">= 6.7.0 < 7"},
                  "peerDependencies": {"d3": "7.8.4 || ^7.8.5"},
                  "optionalDependencies": {"d3": "7.8.5-rc.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesNestedWorkspacePackageJson() {
        rewriteRun(json(
                """
                {
                  "name": "@example/chart",
                  "peerDependencies": {
                    "d3": "^6.6.2",
                    "react": ">=17"
                  }
                }
                """,
                """
                {
                  "name": "@example/chart",
                  "peerDependencies": {
                    "d3": "7.9.0",
                    "react": ">=17"
                  }
                }
                """,
                spec -> spec.path("packages/chart/package.json")
        ));
    }

    @Test
    void preservesAdjacentD3MicrolibrariesAndTypes() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "d3": "7.8.4",
                    "d3-array": "^3.2.4",
                    "d3-selection": "^3.0.0"
                  },
                  "devDependencies": {"@types/d3": "^7.4.3"}
                }
                """,
                """
                {
                  "dependencies": {
                    "d3": "7.9.0",
                    "d3-array": "^3.2.4",
                    "d3-selection": "^3.0.0"
                  },
                  "devDependencies": {"@types/d3": "^7.4.3"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTargetVersionAndTargetRangeUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"d3": "7.9.0"},
                  "devDependencies": {"d3": "^7.9.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotDowngradeNewerVersions() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"d3": "7.9.1"},
                  "devDependencies": {"d3": "^7.10.0"},
                  "peerDependencies": {"d3": ">=8.0.0"},
                  "optionalDependencies": {"d3": "9.0.0-beta.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesUnlistedOldVersionsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"d3": "5.15.1"},
                  "devDependencies": {"d3": "6.6.1"},
                  "peerDependencies": {"d3": "7.8.3"},
                  "optionalDependencies": {"d3": "7.8.6"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesWorkspaceAliasGitFileAndUrlReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"d3": "workspace:^7.8.5"},
                  "devDependencies": {"d3": "npm:@example/d3@7.8.5"},
                  "peerDependencies": {"d3": "github:d3/d3#v7.8.5"},
                  "optionalDependencies": {"d3": "file:../d3"}
                }
                """,
                spec -> spec.path("package.json")
        ));
        rewriteRun(json(
                """
                {
                  "dependencies": {"d3": "git+https://github.com/d3/d3.git#v7.8.5"},
                  "devDependencies": {"d3": "https://registry.example/d3-7.8.5.tgz"}
                }
                """,
                spec -> spec.path("packages/external/package.json")
        ));
    }

    @Test
    void leavesMalformedVersionLookalikesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"d3": "7.8.50"},
                  "devDependencies": {"d3": "6.7.00"},
                  "peerDependencies": {"d3": "7.8.5local"},
                  "optionalDependencies": {"d3": "=7.8.5"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyPackageLock() {
        rewriteRun(json(
                """
                {
                  "name": "locked-app",
                  "packages": {
                    "": {"dependencies": {"d3": "7.8.5"}},
                    "node_modules/d3": {"version": "7.8.5"}
                  },
                  "dependencies": {"d3": {"version": "7.8.5"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                """
                {"dependencies": {"d3": "7.8.5"}}
                """,
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNames() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@types/d3": "7.8.5",
                    "d3-array": "7.8.5",
                    "d3-node": "7.8.5",
                    "D3": "7.8.5"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNonScalarAndNestedMetadataUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"d3": {"version": "7.8.5"}},
                  "devDependencies": {"d3": ["7.8.5"]},
                  "overrides": {"d3": "7.8.5"},
                  "resolutions": {"d3": "7.8.5"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static org.openrewrite.test.SourceSpecs versionedPackage(String path, String version) {
        return json(
                "{\"dependencies\": {\"d3\": \"" + version + "\"}}",
                "{\"dependencies\": {\"d3\": \"7.9.0\"}}",
                spec -> spec.path(path)
        );
    }

    private static Stream<Arguments> selectedDeclarations() {
        return Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                .flatMap(section -> Stream.of("5.16.0", "6.6.2", "6.7.0", "7.1.1", "7.8.2", "7.8.4", "7.8.5")
                        .flatMap(version -> Stream.of("", "^", "~")
                                .map(prefix -> Arguments.of(section, prefix + version))));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.d3")
                .scanYamlResources()
                .build();
    }
}
