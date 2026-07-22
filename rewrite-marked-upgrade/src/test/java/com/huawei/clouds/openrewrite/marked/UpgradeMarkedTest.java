package com.huawei.clouds.openrewrite.marked;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeMarkedTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.marked.UpgradeMarkedTo17_0_6";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesPkgsitePinnedDependency() {
        // Reduced from golang/pkgsite at f5afe024; adjacent @types/marked intentionally remains unchanged:
        // https://github.com/golang/pkgsite/blob/f5afe0245fffc029e6a9ec3010b9ff04107b171a/package.json
        rewriteRun(json(
                """
                {
                  "name": "pkgsite",
                  "private": true,
                  "dependencies": {
                    "@types/marked": "4.0.1",
                    "jest": "27.3.1",
                    "marked": "4.0.10",
                    "typescript": "4.0.3"
                  }
                }
                """,
                """
                {
                  "name": "pkgsite",
                  "private": true,
                  "dependencies": {
                    "@types/marked": "4.0.1",
                    "jest": "27.3.1",
                    "marked": "17.0.6",
                    "typescript": "4.0.3"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesMdToPdfRuntimeDependency() {
        // Reduced from simonhaenisch/md-to-pdf v5.2.4; its Node >=12 baseline must be raised manually:
        // https://github.com/simonhaenisch/md-to-pdf/blob/v5.2.4/package.json
        rewriteRun(json(
                """
                {
                  "name": "md-to-pdf",
                  "engines": {"node": ">=12.0"},
                  "dependencies": {
                    "gray-matter": "^4.0.3",
                    "marked": "^4.2.12",
                    "puppeteer": ">=8.0.0"
                  },
                  "devDependencies": {"@types/marked": "4.0.8"}
                }
                """,
                """
                {
                  "name": "md-to-pdf",
                  "engines": {"node": ">=12.0"},
                  "dependencies": {
                    "gray-matter": "^4.0.3",
                    "marked": "17.0.6",
                    "puppeteer": ">=8.0.0"
                  },
                  "devDependencies": {"@types/marked": "4.0.8"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesMongooseDocumentationDependency() {
        // Reduced from Automattic/mongoose 6.10.2; marked is used by its documentation toolchain:
        // https://github.com/Automattic/mongoose/blob/6.10.2/package.json
        rewriteRun(json(
                """
                {
                  "name": "mongoose",
                  "engines": {"node": ">=12.0.0"},
                  "devDependencies": {
                    "highlight.js": "11.7.0",
                    "marked": "4.2.12",
                    "typescript": "4.9.5"
                  }
                }
                """,
                """
                {
                  "name": "mongoose",
                  "engines": {"node": ">=12.0.0"},
                  "devDependencies": {
                    "highlight.js": "11.7.0",
                    "marked": "17.0.6",
                    "typescript": "4.9.5"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "upgrades spreadsheet version {0}")
    @ValueSource(strings = {
            "4.0.10", "4.0.12", "4.0.17", "4.1.0", "4.2.3",
            "4.2.12", "4.3.0", "5.1.0", "5.1.1"
    })
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(json(
                "{\"dependencies\":{\"marked\":\"" + oldVersion + "\"}}",
                "{\"dependencies\":{\"marked\":\"17.0.6\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"marked": "4.0.12"},
                  "devDependencies": {"marked": "^4.1.0"},
                  "peerDependencies": {"marked": "~4.3.0"},
                  "optionalDependencies": {"marked": ">=5.1.1 <6"}
                }
                """,
                """
                {
                  "dependencies": {"marked": "17.0.6"},
                  "devDependencies": {"marked": "17.0.6"},
                  "peerDependencies": {"marked": "17.0.6"},
                  "optionalDependencies": {"marked": "17.0.6"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesComparatorOrHyphenAndVPrefixRanges() {
        rewriteRun(
                packageVersion("apps/comparator/package.json", ">= 4.2.12 < 5"),
                packageVersion("apps/or/package.json", "4.2.3 || ^5.1.1"),
                packageVersion("apps/hyphen/package.json", "4.0.17 - 4.3.0"),
                packageVersion("apps/v-prefix/package.json", "v5.1.0")
        );
    }

    @Test
    void upgradesPrereleaseAndBuildMetadataOfSelectedVersions() {
        rewriteRun(
                packageVersion("apps/prerelease/package.json", "5.1.1-rc.1"),
                packageVersion("apps/build/package.json", "4.2.12+company.7")
        );
    }

    @Test
    void upgradesNestedMonorepoManifest() {
        rewriteRun(json(
                """
                {
                  "name": "@example/markdown-renderer",
                  "peerDependencies": {
                    "dompurify": "^3.2.6",
                    "marked": "^5.1.0"
                  }
                }
                """,
                """
                {
                  "name": "@example/markdown-renderer",
                  "peerDependencies": {
                    "dompurify": "^3.2.6",
                    "marked": "17.0.6"
                  }
                }
                """,
                spec -> spec.path("packages/markdown-renderer/package.json")
        ));
    }

    @Test
    void preservesAdjacentMarkedExtensionsTypesAndSanitizer() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "marked": "4.3.0",
                    "marked-base-url": "^1.1.7",
                    "marked-gfm-heading-id": "^4.1.2",
                    "marked-highlight": "^2.2.3",
                    "marked-mangle": "^1.1.10",
                    "dompurify": "^3.3.0"
                  },
                  "devDependencies": {"@types/marked": "^5.0.2"}
                }
                """,
                """
                {
                  "dependencies": {
                    "marked": "17.0.6",
                    "marked-base-url": "^1.1.7",
                    "marked-gfm-heading-id": "^4.1.2",
                    "marked-highlight": "^2.2.3",
                    "marked-mangle": "^1.1.10",
                    "dompurify": "^3.3.0"
                  },
                  "devDependencies": {"@types/marked": "^5.0.2"}
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
                  "dependencies": {"marked": "17.0.6"},
                  "devDependencies": {"marked": "^17.0.6"}
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
                  "dependencies": {"marked": "17.0.7"},
                  "devDependencies": {"marked": "^17.1.0"},
                  "peerDependencies": {"marked": ">=18.0.0"},
                  "optionalDependencies": {"marked": "20.0.0-beta.1"}
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
                  "dependencies": {"marked": "3.0.8"},
                  "devDependencies": {"marked": "4.0.11"},
                  "peerDependencies": {"marked": "4.2.13"},
                  "optionalDependencies": {"marked": "5.1.2"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesWorkspaceAndNpmAliasReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"marked": "workspace:^4.2.12"},
                  "devDependencies": {"marked": "npm:@example/marked@4.2.12"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesGitFileAndUrlReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"marked": "file:../marked"},
                  "devDependencies": {"marked": "git+ssh://git@github.com/markedjs/marked.git#v4.2.12"},
                  "peerDependencies": {"marked": "github:markedjs/marked#v5.1.1"},
                  "optionalDependencies": {"marked": "https://example.test/marked-4.3.0.tgz"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTagsWildcardsAndEmptyDeclarationsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"marked": "latest"},
                  "devDependencies": {"marked": "next"},
                  "peerDependencies": {"marked": "*"},
                  "optionalDependencies": {"marked": ""}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesMalformedVersionLookalikesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"marked": "4.2.120"},
                  "devDependencies": {"marked": "4.0.010"},
                  "peerDependencies": {"marked": "5.1.10"},
                  "optionalDependencies": {"marked": "4.3.0local"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyOverridesOrResolutions() {
        rewriteRun(json(
                """
                {
                  "overrides": {"marked": "4.2.12"},
                  "resolutions": {"marked": "4.0.10"},
                  "pnpm": {"overrides": {"marked": "5.1.1"}}
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
                    "": {"dependencies": {"marked": "4.2.12"}},
                    "node_modules/marked": {"version": "4.2.12"}
                  },
                  "dependencies": {"marked": {"version": "4.2.12"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                "{\"dependencies\":{\"marked\":\"4.2.12\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNamesOrCaseVariants() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@types/marked": "4.2.12",
                    "marked-highlight": "4.2.12",
                    "marked-terminal": "4.2.12",
                    "markedjs": "4.2.12",
                    "Marked": "4.2.12"
                  }
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

    private static org.openrewrite.test.SourceSpecs packageVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"marked\":\"" + version + "\"}}",
                "{\"dependencies\":{\"marked\":\"17.0.6\"}}",
                spec -> spec.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.marked")
                .scanYamlResources()
                .build();
    }
}
