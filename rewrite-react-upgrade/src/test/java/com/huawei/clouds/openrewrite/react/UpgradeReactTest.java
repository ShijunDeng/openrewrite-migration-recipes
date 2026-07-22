package com.huawei.clouds.openrewrite.react;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeReactTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.react.UpgradeReactTo19_2_7";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesHitachiMeteorEditorWithoutChangingRendererOrLegacyStack() {
        // Reduced from Hitachi-Automotive-And-Industry-Lab/semantic-segmentation-editor at b159ccf4:
        // https://github.com/Hitachi-Automotive-And-Industry-Lab/semantic-segmentation-editor/blob/b159ccf46001420b6018e6d0faca3e64b0955cf9/package.json
        rewriteRun(json(
                """
                {
                  "name": "semantic-segmentation-editor",
                  "version": "1.6.0",
                  "scripts": {"start": "meteor run --settings settings.json"},
                  "dependencies": {
                    "@material-ui/core": "4.11.2",
                    "meteor-node-stubs": "1.2.5",
                    "react": "^16.14.0",
                    "react-dom": "^16.14.0",
                    "react-router": "5.2.0",
                    "react-router-dom": "5.2.0"
                  }
                }
                """,
                """
                {
                  "name": "semantic-segmentation-editor",
                  "version": "1.6.0",
                  "scripts": {"start": "meteor run --settings settings.json"},
                  "dependencies": {
                    "@material-ui/core": "4.11.2",
                    "meteor-node-stubs": "1.2.5",
                    "react": "19.2.7",
                    "react-dom": "^16.14.0",
                    "react-router": "5.2.0",
                    "react-router-dom": "5.2.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesRealNextTwelveApplicationAndLeavesFrameworkMatrixVisible() {
        // Reduced from VishwaGauravIn/github-profile-readme-maker at 089aa348:
        // https://github.com/VishwaGauravIn/github-profile-readme-maker/blob/089aa348c0deedef1f1363d00b8c7ca0e74774d0/package.json
        rewriteRun(json(
                """
                {
                  "name": "github-profile-readme-maker",
                  "private": true,
                  "scripts": {"dev": "next dev", "build": "next build"},
                  "dependencies": {
                    "mobx-react": "^7.5.0",
                    "next": "12.1.0",
                    "react": "17.0.2",
                    "react-dom": "17.0.2",
                    "react-text-loop": "^2.3.0"
                  }
                }
                """,
                """
                {
                  "name": "github-profile-readme-maker",
                  "private": true,
                  "scripts": {"dev": "next dev", "build": "next build"},
                  "dependencies": {
                    "mobx-react": "^7.5.0",
                    "next": "12.1.0",
                    "react": "19.2.7",
                    "react-dom": "17.0.2",
                    "react-text-loop": "^2.3.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesOfficialViteReactTemplateAndPreservesTypesAndReactDom() {
        // Reduced from vitejs/vite v4.5.0 at commit 055d2b86:
        // https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/package.json
        // https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/src/main.jsx
        rewriteRun(
                json(
                        """
                        {
                          "name": "vite-react-starter",
                          "private": true,
                          "version": "0.0.0",
                          "type": "module",
                          "dependencies": {
                            "react": "^18.2.0",
                            "react-dom": "^18.2.0"
                          },
                          "devDependencies": {
                            "@types/react": "^18.2.18",
                            "@types/react-dom": "^18.2.7",
                            "@vitejs/plugin-react": "^4.0.4",
                            "vite": "^4.4.8"
                          }
                        }
                        """,
                        """
                        {
                          "name": "vite-react-starter",
                          "private": true,
                          "version": "0.0.0",
                          "type": "module",
                          "dependencies": {
                            "react": "19.2.7",
                            "react-dom": "^18.2.0"
                          },
                          "devDependencies": {
                            "@types/react": "^18.2.18",
                            "@types/react-dom": "^18.2.7",
                            "@vitejs/plugin-react": "^4.0.4",
                            "vite": "^4.4.8"
                          }
                        }
                        """,
                        spec -> spec.path("packages/create-vite/template-react/package.json")
                ),
                text(
                        """
                        import React from 'react'
                        import ReactDOM from 'react-dom/client'
                        import App from './App.jsx'
                        ReactDOM.createRoot(document.getElementById('root')).render(
                          <React.StrictMode><App /></React.StrictMode>,
                        )
                        """,
                        spec -> spec.path("packages/create-vite/template-react/src/main.jsx")
                )
        );
    }

    @Test
    void upgradesRealIgniteDevelopmentDependencyAtNineteenZero() {
        // Reduced from infinitered/ignite at e829d2f9:
        // https://github.com/infinitered/ignite/blob/e829d2f922c5568a59a77bfb6232aeb500be3f13/package.json
        rewriteRun(json(
                """
                {
                  "name": "ignite-cli",
                  "version": "11.5.0",
                  "engines": {"node": ">=20.0.0"},
                  "devDependencies": {
                    "@types/react": "~19.0.10",
                    "eslint-plugin-react-native": "^4.1.0",
                    "react": "19.0.0",
                    "typescript": "~5.8.3"
                  },
                  "packageManager": "pnpm@10.9.0"
                }
                """,
                """
                {
                  "name": "ignite-cli",
                  "version": "11.5.0",
                  "engines": {"node": ">=20.0.0"},
                  "devDependencies": {
                    "@types/react": "~19.0.10",
                    "eslint-plugin-react-native": "^4.1.0",
                    "react": "19.2.7",
                    "typescript": "~5.8.3"
                  },
                  "packageManager": "pnpm@10.9.0"
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "upgrades spreadsheet React version {0}")
    @ValueSource(strings = {"16.6.1", "16.14.0", "17.0.2", "18.2.0", "19.0.0"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(packageVersion("package.json", oldVersion));
    }

    @ParameterizedTest(name = "upgrades safe npm declaration {0}")
    @ValueSource(strings = {
            "^16.6.1",
            "~16.6.1",
            "^16.14.0",
            "~16.14.0",
            "^17.0.2",
            "~17.0.2",
            "^18.2.0",
            "~18.2.0",
            "^19.0.0",
            "~19.0.0"
    })
    void upgradesSupportedRegistrySemverForms(String declaration) {
        rewriteRun(packageVersion("package.json", declaration));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react": "16.6.1"},
                  "devDependencies": {"react": "^16.14.0"},
                  "peerDependencies": {"react": "17.0.2"},
                  "optionalDependencies": {"react": "~19.0.0"}
                }
                """,
                """
                {
                  "dependencies": {"react": "19.2.7"},
                  "devDependencies": {"react": "19.2.7"},
                  "peerDependencies": {"react": "19.2.7"},
                  "optionalDependencies": {"react": "19.2.7"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesRootAndNestedWorkspaceManifests() {
        rewriteRun(
                json(
                        """
                        {"name":"ui-monorepo","private":true,"workspaces":["apps/*","packages/*"]}
                        """,
                        spec -> spec.path("package.json")
                ),
                packageVersion("apps/web/package.json", "^17.0.2"),
                packageVersion("packages/components/package.json", "18.2.0")
        );
    }

    @Test
    void preservesRendererTypesNativeAndReactEcosystemPackages() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "react": "16.14.0",
                    "react-dom": "16.14.0",
                    "react-native": "0.63.5",
                    "react-router": "5.3.4",
                    "react-router-dom": "5.3.4",
                    "react-test-renderer": "16.14.0"
                  },
                  "devDependencies": {
                    "@types/react": "16.14.62",
                    "@types/react-dom": "16.9.25",
                    "eslint-plugin-react-hooks": "4.6.2"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "react": "19.2.7",
                    "react-dom": "16.14.0",
                    "react-native": "0.63.5",
                    "react-router": "5.3.4",
                    "react-router-dom": "5.3.4",
                    "react-test-renderer": "16.14.0"
                  },
                  "devDependencies": {
                    "@types/react": "16.14.62",
                    "@types/react-dom": "16.9.25",
                    "eslint-plugin-react-hooks": "4.6.2"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTargetAndTargetRangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react": "19.2.7"},
                  "devDependencies": {"react": "^19.2.7"},
                  "peerDependencies": {"react": ">=19.2.7 <20"}
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
                  "dependencies": {"react": "19.2.8"},
                  "devDependencies": {"react": "^20.0.0"},
                  "peerDependencies": {"react": "21.0.0-canary.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves unlisted React declaration {0} untouched")
    @ValueSource(strings = {
            "16.6.0", "16.6.2", "16.13.1", "16.14.1", "17.0.0", "17.0.1",
            "17.0.3", "18.0.0", "18.1.0", "18.2.1", "18.3.1", "19.0.1", "19.1.0", "19.2.0"
    })
    void leavesUnlistedVersionsUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"react\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesBroadAndUnboundedRangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react": ">=16"},
                  "devDependencies": {"react": "16.x"},
                  "peerDependencies": {"react": "*"},
                  "optionalDependencies": {"react": "latest"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves ambiguous selected-version declaration {0} untouched")
    @ValueSource(strings = {
            "17.0.2-rc.1",
            "18.2.0+company.4",
            "v17.0.2",
            "=18.2.0",
            "^v18.2.0",
            ">=16.14.0 <17",
            " >= 18.2.0 <= 20",
            "16.6.1 - 18.2.0",
            "16.14.0 || ^18.2.0",
            "^17.0.2 || ~19.0.0"
    })
    void leavesPrereleaseBuildAndCompoundRangesUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"react\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesWorkspaceAliasesAndLocalReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react": "workspace:^18.2.0"},
                  "devDependencies": {"react": "npm:@example/react@19.0.0"},
                  "peerDependencies": {"react": "link:../react"},
                  "optionalDependencies": {"react": "file:../react-17.0.2"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesGitGithubUrlAndTarballReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react": "git+ssh://git@github.com/facebook/react.git#v18.2.0"},
                  "devDependencies": {"react": "github:facebook/react#v19.0.0"},
                  "peerDependencies": {"react": "https://registry.example/react-17.0.2.tgz"},
                  "optionalDependencies": {"react": "../artifacts/react-16.14.0.tgz"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTagsWildcardsAndNonSemverTextUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react": "next"},
                  "devDependencies": {"react": "18.*"},
                  "peerDependencies": {"react": "18.2.0 compatible"},
                  "optionalDependencies": {"react": "catalog:react"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void rejectsMalformedVersionLookalikes() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react": "16.14.00"},
                  "devDependencies": {"react": "17.0.20"},
                  "peerDependencies": {"react": "18.2.00"},
                  "optionalDependencies": {"react": "19.0.00"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyOverridesResolutionsOrPeerMetadata() {
        rewriteRun(json(
                """
                {
                  "overrides": {"react": "16.14.0"},
                  "resolutions": {"react": "17.0.2"},
                  "pnpm": {"overrides": {"react": "18.2.0"}},
                  "peerDependenciesMeta": {"react": {"optional": true}}
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
                    "": {"dependencies": {"react": "18.2.0"}},
                    "node_modules/react": {"version": "18.2.0"}
                  },
                  "dependencies": {"react": {"version": "18.2.0"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOrdinaryJsonOrSimilarNames() {
        rewriteRun(
                json(
                        "{\"dependencies\":{\"react\":\"18.2.0\"}}",
                        spec -> spec.path("fixtures/dependencies.json")
                ),
                json(
                        """
                        {
                          "dependencies": {
                            "@types/react": "18.2.0",
                            "preact": "18.2.0",
                            "react-dom": "18.2.0",
                            "react-is": "18.2.0",
                            "reactive": "18.2.0",
                            "React": "18.2.0"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void leavesSourceAndHtmlImportsForManualMigration() {
        rewriteRun(
                text(
                        """
                        import React, { createFactory, useRef } from 'react';
                        import ReactDOM from 'react-dom';
                        ReactDOM.render(<App />, document.getElementById('root'));
                        """,
                        spec -> spec.path("src/index.jsx")
                ),
                text(
                        """
                        <script src="https://unpkg.com/react@16.14.0/umd/react.production.min.js"></script>
                        """,
                        spec -> spec.path("public/index.html")
                )
        );
    }

    @Test
    void leavesNonStringDependencyValuesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react": {"version": "18.2.0"}},
                  "devDependencies": {"react": 18},
                  "peerDependencies": {"react": true},
                  "optionalDependencies": {"react": null}
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
        assertEquals("Upgrade React to 19.2.7", recipe.getDisplayName());
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static SourceSpecs packageVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"react\":\"" + version + "\"}}",
                "{\"dependencies\":{\"react\":\"19.2.7\"}}",
                spec -> spec.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.react")
                .scanYamlResources()
                .build();
    }
}
