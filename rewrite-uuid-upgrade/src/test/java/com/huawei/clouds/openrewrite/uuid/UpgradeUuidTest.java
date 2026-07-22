package com.huawei.clouds.openrewrite.uuid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeUuidTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.uuid.UpgradeUuidTo13_0_2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesShineoutRuntimeDependency() {
        // Reduced from sheinsight/shineout at f7516856. Its webpack 4, TypeScript 4.5,
        // @types/uuid, and old browser build must be checked separately for uuid 13 ESM compatibility:
        // https://github.com/sheinsight/shineout/blob/f75168569cbf87e269f0d37fee2e91b71e9b6ea1/package.json
        rewriteRun(json(
                """
                {
                  "name": "shineout",
                  "main": "./lib/index.js",
                  "module": "./es/index.js",
                  "dependencies": {
                    "date-fns": "2.28.0",
                    "uuid": "8.3.2"
                  },
                  "devDependencies": {
                    "@types/uuid": "^9.0.0",
                    "typescript": "4.5.2",
                    "webpack": "^4.28.3"
                  }
                }
                """,
                """
                {
                  "name": "shineout",
                  "main": "./lib/index.js",
                  "module": "./es/index.js",
                  "dependencies": {
                    "date-fns": "2.28.0",
                    "uuid": "13.0.2"
                  },
                  "devDependencies": {
                    "@types/uuid": "^9.0.0",
                    "typescript": "4.5.2",
                    "webpack": "^4.28.3"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesOpenWaNode12DependencyButLeavesOverride() {
        // Reduced from open-wa/wa-automate-nodejs at 043bb31e. The real backup manifest
        // has uuid ^9.0.0, an override of the same name, and a Node >=12.18.3 baseline:
        // https://github.com/open-wa/wa-automate-nodejs/blob/043bb31e542944213375179532fbaef89a1e42af/package.json.v4-backup
        rewriteRun(json(
                """
                {
                  "name": "@open-wa/wa-automate",
                  "main": "dist/index.js",
                  "types": "dist/index.d.ts",
                  "engines": {"node": ">=12.18.3", "npm": ">=7.9.0"},
                  "dependencies": {
                    "uuid": "^9.0.0",
                    "uuid-apikey": "^1.5.3"
                  },
                  "overrides": {"uuid": "^9.0.0"}
                }
                """,
                """
                {
                  "name": "@open-wa/wa-automate",
                  "main": "dist/index.js",
                  "types": "dist/index.d.ts",
                  "engines": {"node": ">=12.18.3", "npm": ">=7.9.0"},
                  "dependencies": {
                    "uuid": "13.0.2",
                    "uuid-apikey": "^1.5.3"
                  },
                  "overrides": {"uuid": "^9.0.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesVueDiagramsDependency() {
        // Reduced from gwenaelp/vue-diagrams at b417a289. The manifest is a Vite/TypeScript
        // library but still advertises Node >=8.9, below uuid 13's tested Node 18-24 matrix:
        // https://github.com/gwenaelp/vue-diagrams/blob/b417a289f08a0f0dccfa6156397d35142066334b/package.json
        rewriteRun(json(
                """
                {
                  "name": "@three-workspace/vue-diagrams",
                  "main": "dist/vue-diagrams.mjs",
                  "engines": {"node": ">=8.9.0"},
                  "dependencies": {
                    "typescript": "^5.2.2",
                    "uuid": "^10.0.0",
                    "vite": "^6.2.2",
                    "vue": "^3.5.13"
                  }
                }
                """,
                """
                {
                  "name": "@three-workspace/vue-diagrams",
                  "main": "dist/vue-diagrams.mjs",
                  "engines": {"node": ">=8.9.0"},
                  "dependencies": {
                    "typescript": "^5.2.2",
                    "uuid": "13.0.2",
                    "vite": "^6.2.2",
                    "vue": "^3.5.13"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesBoombEsmDependencyAndPreservesTypesStub() {
        // Reduced from liuzi6612/boomb at 320c472f. It demonstrates an ESM application
        // with uuid ^11.1.0 and a separate @types/uuid declaration:
        // https://github.com/liuzi6612/boomb/blob/320c472f264d61fed254c2ce35b9fa6b2e4a12d9/package.json
        rewriteRun(json(
                """
                {
                  "name": "boomb",
                  "type": "module",
                  "dependencies": {
                    "uuid": "^11.1.0",
                    "vue": "^3.5.13"
                  },
                  "devDependencies": {
                    "@types/uuid": "^10.0.0",
                    "typescript": "^5.8.3",
                    "vite": "^6.3.4"
                  }
                }
                """,
                """
                {
                  "name": "boomb",
                  "type": "module",
                  "dependencies": {
                    "uuid": "13.0.2",
                    "vue": "^3.5.13"
                  },
                  "devDependencies": {
                    "@types/uuid": "^10.0.0",
                    "typescript": "^5.8.3",
                    "vite": "^6.3.4"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "upgrades spreadsheet uuid version {0}")
    @ValueSource(strings = {
            "8.3.2", "9.0.0", "9.0.1", "10.0.0", "11.0.3", "11.1.0"
    })
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(packageVersion("package.json", oldVersion));
    }

    @ParameterizedTest(name = "upgrades supported scalar npm declaration {0}")
    @ValueSource(strings = {
            "^8.3.2",
            "~9.0.0",
            "^9.0.1",
            "~10.0.0",
            "^11.0.3",
            "~11.1.0"
    })
    void upgradesSafeScalarDeclarations(String oldVersion) {
        rewriteRun(packageVersion("package.json", oldVersion));
    }

    @ParameterizedTest(name = "leaves ambiguous npm declaration {0} unchanged")
    @ValueSource(strings = {
            ">=9.0.1 <10",
            "10.0.0 - 11.1.0",
            "9.0.0 || ^11.0.3",
            "v11.1.0",
            "11.0.3-beta.2",
            "8.3.2+vendor.5",
            "  >= 10.0.0 < 13",
            "=11.1.0"
    })
    void leavesCompoundOrNonCanonicalDeclarationsUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"uuid\":\"" + declaration + "\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"uuid": "8.3.2"},
                  "devDependencies": {"uuid": "^9.0.1"},
                  "peerDependencies": {"uuid": "^10.0.0"},
                  "optionalDependencies": {"uuid": "~11.0.3"}
                }
                """,
                """
                {
                  "dependencies": {"uuid": "13.0.2"},
                  "devDependencies": {"uuid": "13.0.2"},
                  "peerDependencies": {"uuid": "13.0.2"},
                  "optionalDependencies": {"uuid": "13.0.2"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesNestedWorkspaceManifests() {
        rewriteRun(
                packageVersion("apps/api/package.json", "^9.0.1"),
                packageVersion("packages/id-generator/package.json", "11.1.0"),
                packageVersion("examples/browser/package.json", "~8.3.2")
        );
    }

    @Test
    void preservesAdjacentTypesPolyfillAndUuidPackages() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "react-native-get-random-values": "^1.11.0",
                    "uuid": "10.0.0",
                    "uuid-apikey": "^1.5.3",
                    "uuid-validate": "^0.0.3"
                  },
                  "devDependencies": {"@types/uuid": "^10.0.0"}
                }
                """,
                """
                {
                  "dependencies": {
                    "react-native-get-random-values": "^1.11.0",
                    "uuid": "13.0.2",
                    "uuid-apikey": "^1.5.3",
                    "uuid-validate": "^0.0.3"
                  },
                  "devDependencies": {"@types/uuid": "^10.0.0"}
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
                  "dependencies": {"uuid": "13.0.2"},
                  "devDependencies": {"uuid": "^13.0.2"}
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
                  "dependencies": {"uuid": "13.0.3"},
                  "devDependencies": {"uuid": "^14.0.0"},
                  "peerDependencies": {"uuid": ">=15.0.0"},
                  "optionalDependencies": {"uuid": "20.0.0-beta.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesUnlistedVersionsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"uuid": "8.3.1"},
                  "devDependencies": {"uuid": "9.0.2"},
                  "peerDependencies": {"uuid": "10.0.1"},
                  "optionalDependencies": {"uuid": "11.1.1"},
                  "bundledDependencies": {"uuid": "12.0.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesWorkspaceAndNpmAliasesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"uuid": "workspace:^11.1.0"},
                  "devDependencies": {"uuid": "npm:@example/uuid@10.0.0"},
                  "peerDependencies": {"uuid": "link:../uuid"}
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
                  "dependencies": {"uuid": "file:../uuid-8.3.2"},
                  "devDependencies": {"uuid": "git+ssh://git@github.com/uuidjs/uuid.git#v9.0.1"},
                  "peerDependencies": {"uuid": "github:uuidjs/uuid#v10.0.0"},
                  "optionalDependencies": {"uuid": "https://example.test/uuid-11.1.0.tgz"}
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
                  "dependencies": {"uuid": "latest"},
                  "devDependencies": {"uuid": "next"},
                  "peerDependencies": {"uuid": "*"},
                  "optionalDependencies": {"uuid": ""}
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
                  "dependencies": {"uuid": "8.3.20"},
                  "devDependencies": {"uuid": "9.0.01"},
                  "peerDependencies": {"uuid": "10.0.00"},
                  "optionalDependencies": {"uuid": "11.1.0local"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyOverridesResolutionsOrPnpmOverrides() {
        rewriteRun(json(
                """
                {
                  "overrides": {"uuid": "9.0.1"},
                  "resolutions": {"uuid": "10.0.0"},
                  "pnpm": {"overrides": {"uuid": "11.1.0"}}
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
                    "": {"dependencies": {"uuid": "9.0.1"}},
                    "node_modules/uuid": {"version": "9.0.1"}
                  },
                  "dependencies": {"uuid": {"version": "9.0.1"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                "{\"dependencies\":{\"uuid\":\"10.0.0\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNamesOrCaseVariants() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@types/uuid": "9.0.1",
                    "uuid-apikey": "10.0.0",
                    "uuid-validate": "11.1.0",
                    "uuidv4": "8.3.2",
                    "UUID": "10.0.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void discoversRecipeWithExpectedMetadataAndValidConfiguration() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertEquals("Upgrade uuid to 13.0.2", recipe.getDisplayName());
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static org.openrewrite.test.SourceSpecs packageVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"uuid\":\"" + version + "\"}}",
                "{\"dependencies\":{\"uuid\":\"13.0.2\"}}",
                spec -> spec.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.uuid")
                .scanYamlResources()
                .build();
    }
}
