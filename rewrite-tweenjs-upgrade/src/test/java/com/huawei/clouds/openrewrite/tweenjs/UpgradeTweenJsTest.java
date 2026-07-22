package com.huawei.clouds.openrewrite.tweenjs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeTweenJsTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.tweenjs.UpgradeTweenJsTo23_1_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesAwsIotAppKitSceneComposer() {
        // Reduced from awslabs/iot-app-kit at f3825152. The real package is ESM, uses Three.js,
        // and must be regression-tested for the target package export map:
        // https://github.com/awslabs/iot-app-kit/blob/f38251529912f65e4994b6a19fd035a29dd9d8c4/packages/scene-composer/package.json
        rewriteRun(json(
                """
                {
                  "name": "@iot-app-kit/scene-composer",
                  "type": "module",
                  "dependencies": {
                    "@iot-app-kit/core": "12.5.1",
                    "@tweenjs/tween.js": "^20.0.3",
                    "three": "0.151.3"
                  },
                  "devDependencies": {"typescript": "^5.5.4"}
                }
                """,
                """
                {
                  "name": "@iot-app-kit/scene-composer",
                  "type": "module",
                  "dependencies": {
                    "@iot-app-kit/core": "12.5.1",
                    "@tweenjs/tween.js": "23.1.1",
                    "three": "0.151.3"
                  },
                  "devDependencies": {"typescript": "^5.5.4"}
                }
                """,
                spec -> spec.path("packages/scene-composer/package.json")
        ));
    }

    @Test
    void upgradesGlobeStream3dLibrary() {
        // Reduced from hululuuuuu/GlobeStream3D at ba75e68c. Its code uses both namespace imports
        // and the named update export in a requestAnimationFrame loop:
        // https://github.com/hululuuuuu/GlobeStream3D/blob/ba75e68c1575673cbbb1d2edc89ff7c28586d4db/package.json
        // https://github.com/hululuuuuu/GlobeStream3D/blob/ba75e68c1575673cbbb1d2edc89ff7c28586d4db/src/lib/chartScene.ts#L24
        rewriteRun(json(
                """
                {
                  "name": "earth-flyline",
                  "type": "module",
                  "dependencies": {
                    "@tweenjs/tween.js": "^20.0.3",
                    "@types/three": "^0.152.0",
                    "lodash-es": "^4.17.21",
                    "three": "^0.152.2"
                  },
                  "devDependencies": {"typescript": "^4.6.4", "vite": "^3.2.3"}
                }
                """,
                """
                {
                  "name": "earth-flyline",
                  "type": "module",
                  "dependencies": {
                    "@tweenjs/tween.js": "23.1.1",
                    "@types/three": "^0.152.0",
                    "lodash-es": "^4.17.21",
                    "three": "^0.152.2"
                  },
                  "devDependencies": {"typescript": "^4.6.4", "vite": "^3.2.3"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesThreeViewcubePeerDependency() {
        // Reduced from mikemklee/three-viewcube at 036db7b9. Its TypeScript source uses the
        // default TWEEN import, Tween/Easing, and the global update function:
        // https://github.com/mikemklee/three-viewcube/blob/036db7b9e74c23fcb2dc6c7cb72d7220504ca558/package.json
        // https://github.com/mikemklee/three-viewcube/blob/036db7b9e74c23fcb2dc6c7cb72d7220504ca558/index.ts
        rewriteRun(json(
                """
                {
                  "name": "three-viewcube",
                  "devDependencies": {
                    "@types/three": "^0.150.2",
                    "three": "^0.174.0",
                    "typescript": "^5.0.3"
                  },
                  "peerDependencies": {
                    "@tweenjs/tween.js": "^19.0.0",
                    "three": "^0.174.0"
                  }
                }
                """,
                """
                {
                  "name": "three-viewcube",
                  "devDependencies": {
                    "@types/three": "^0.150.2",
                    "three": "^0.174.0",
                    "typescript": "^5.0.3"
                  },
                  "peerDependencies": {
                    "@tweenjs/tween.js": "23.1.1",
                    "three": "^0.174.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesStichHubReactViteApplication() {
        // Reduced from UBA-GCOEN/StichHub at 1eb512f9; adjacent React, Three.js, Vite and
        // @types/three versions are intentionally preserved:
        // https://github.com/UBA-GCOEN/StichHub/blob/1eb512f98f1f76cab581ceda39b9f89fbfb4547b/client/StichHub/package.json
        rewriteRun(json(
                """
                {
                  "name": "stichhub",
                  "type": "module",
                  "dependencies": {
                    "@react-three/fiber": "^8.12.0",
                    "@tweenjs/tween.js": "^19.0.0",
                    "@types/three": "^0.150.1",
                    "react": "^18.2.0",
                    "three": "^0.151.2"
                  },
                  "devDependencies": {"vite": "^4.3.8"}
                }
                """,
                """
                {
                  "name": "stichhub",
                  "type": "module",
                  "dependencies": {
                    "@react-three/fiber": "^8.12.0",
                    "@tweenjs/tween.js": "23.1.1",
                    "@types/three": "^0.150.1",
                    "react": "^18.2.0",
                    "three": "^0.151.2"
                  },
                  "devDependencies": {"vite": "^4.3.8"}
                }
                """,
                spec -> spec.path("client/StichHub/package.json")
        ));
    }

    @ParameterizedTest(name = "upgrades spreadsheet version {0}")
    @ValueSource(strings = {"19.0.0", "20.0.3"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(json(
                "{\"dependencies\":{\"@tweenjs/tween.js\":\"" + oldVersion + "\"}}",
                "{\"dependencies\":{\"@tweenjs/tween.js\":\"23.1.1\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@tweenjs/tween.js": "19.0.0"},
                  "devDependencies": {"@tweenjs/tween.js": "^20.0.3"},
                  "peerDependencies": {"@tweenjs/tween.js": "~19.0.0"},
                  "optionalDependencies": {"@tweenjs/tween.js": ">=20.0.3 <21"}
                }
                """,
                """
                {
                  "dependencies": {"@tweenjs/tween.js": "23.1.1"},
                  "devDependencies": {"@tweenjs/tween.js": "23.1.1"},
                  "peerDependencies": {"@tweenjs/tween.js": "23.1.1"},
                  "optionalDependencies": {"@tweenjs/tween.js": "23.1.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "upgrades selected semver declaration {0}")
    @ValueSource(strings = {
            "^19.0.0", "~20.0.3", ">=19.0.0 <21", "  >= 20.0.3 < 23",
            "19.0.0 || ^20.0.3", "20.0.3 - 22.0.0", "v19.0.0", "^v20.0.3",
            "19.0.0-beta.1", "20.0.3+company.5"
    })
    void upgradesSelectedSemverDeclarations(String oldDeclaration) {
        rewriteRun(packageVersion("fixtures/" + oldDeclaration.hashCode() + "/package.json", oldDeclaration));
    }

    @Test
    void upgradesNestedWorkspaceManifestWithoutChangingWorkspaceList() {
        rewriteRun(
                json(
                        """
                        {"name":"root","private":true,"workspaces":["packages/*"]}
                        """,
                        spec -> spec.path("package.json")
                ),
                json(
                        """
                        {"name":"@example/animation","dependencies":{"@tweenjs/tween.js":"^20.0.3"}}
                        """,
                        """
                        {"name":"@example/animation","dependencies":{"@tweenjs/tween.js":"23.1.1"}}
                        """,
                        spec -> spec.path("packages/animation/package.json")
                )
        );
    }

    @Test
    void preservesJsonFormattingAndAdjacentAnimationPackages() {
        rewriteRun(json(
                """
                {
                    "dependencies" : {
                        "@react-spring/web" : "^9.7.1",
                        "@tweenjs/tween.js" : "20.0.3",
                        "gsap" : "^3.12.5",
                        "three" : "^0.152.2"
                    }
                }
                """,
                """
                {
                    "dependencies" : {
                        "@react-spring/web" : "^9.7.1",
                        "@tweenjs/tween.js" : "23.1.1",
                        "gsap" : "^3.12.5",
                        "three" : "^0.152.2"
                    }
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
                  "dependencies": {"@tweenjs/tween.js": "23.1.1"},
                  "devDependencies": {"@tweenjs/tween.js": "^23.1.1"},
                  "peerDependencies": {"@tweenjs/tween.js": ">=23.1.1 <24"}
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
                  "dependencies": {"@tweenjs/tween.js": "23.1.2"},
                  "devDependencies": {"@tweenjs/tween.js": "^23.1.3"},
                  "peerDependencies": {"@tweenjs/tween.js": ">=24.0.0"},
                  "optionalDependencies": {"@tweenjs/tween.js": "25.0.0-beta.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves unselected registry version {0} untouched")
    @ValueSource(strings = {
            "18.6.4", "19.0.1", "19.1.0", "20.0.0", "20.0.1", "20.0.2", "20.0.4", "21.0.0", "22.0.0"
    })
    void leavesUnselectedRegistryVersionsUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"@tweenjs/tween.js\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesWorkspaceAndNpmAliasReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@tweenjs/tween.js": "workspace:^20.0.3"},
                  "devDependencies": {"@tweenjs/tween.js": "npm:@example/tween.js@19.0.0"},
                  "peerDependencies": {"@tweenjs/tween.js": "workspace:*"}
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
                  "dependencies": {"@tweenjs/tween.js": "file:../tween.js"},
                  "devDependencies": {"@tweenjs/tween.js": "git+ssh://git@github.com/tweenjs/tween.js.git#v20.0.3"},
                  "peerDependencies": {"@tweenjs/tween.js": "github:tweenjs/tween.js#v19.0.0"},
                  "optionalDependencies": {"@tweenjs/tween.js": "https://example.test/tween.js-20.0.3.tgz"}
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
                  "dependencies": {"@tweenjs/tween.js": "latest"},
                  "devDependencies": {"@tweenjs/tween.js": "next"},
                  "peerDependencies": {"@tweenjs/tween.js": "*"},
                  "optionalDependencies": {"@tweenjs/tween.js": ""}
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
                  "dependencies": {"@tweenjs/tween.js": "119.0.0"},
                  "devDependencies": {"@tweenjs/tween.js": "20.0.30"},
                  "peerDependencies": {"@tweenjs/tween.js": "19.0.00"},
                  "optionalDependencies": {"@tweenjs/tween.js": "20.0.3local"}
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
                  "overrides": {"@tweenjs/tween.js": "20.0.3"},
                  "resolutions": {"@tweenjs/tween.js": "19.0.0"},
                  "pnpm": {"overrides": {"@tweenjs/tween.js": "^20.0.3"}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyBundledOrNestedDependencyMetadata() {
        rewriteRun(json(
                """
                {
                  "bundledDependencies": ["@tweenjs/tween.js"],
                  "metadata": {"dependencies": {"@tweenjs/tween.js": "20.0.3"}},
                  "publishConfig": {"@tweenjs/tween.js": "19.0.0"}
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
                    "": {"dependencies": {"@tweenjs/tween.js": "^20.0.3"}},
                    "node_modules/@tweenjs/tween.js": {"version": "20.0.3"}
                  },
                  "dependencies": {"@tweenjs/tween.js": {"version": "20.0.3"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                "{\"dependencies\":{\"@tweenjs/tween.js\":\"20.0.3\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifyPackageJsonBackup() {
        rewriteRun(json(
                "{\"dependencies\":{\"@tweenjs/tween.js\":\"19.0.0\"}}",
                spec -> spec.path("package.json.backup")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNamesOrCaseVariants() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@types/tween.js": "20.0.3",
                    "@tweenjs/tween": "19.0.0",
                    "@tweenjs/tween.js-extra": "20.0.3",
                    "tween.js": "19.0.0",
                    "@TweenJS/tween.js": "20.0.3"
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
                "{\"dependencies\":{\"@tweenjs/tween.js\":\"" + version + "\"}}",
                "{\"dependencies\":{\"@tweenjs/tween.js\":\"23.1.1\"}}",
                spec -> spec.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.tweenjs")
                .scanYamlResources()
                .build();
    }
}
