package com.huawei.clouds.openrewrite.i18next;

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
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeI18nextTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.i18next.UpgradeI18nextTo25_10_10";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesModuleFederationWorkspaceDevelopmentAndPeerDeclarations() {
        // Reduced from module-federation/module-federation-examples at 9c4e554a.
        // The source deliberately retains initImmediate and old exported types for manual v24 migration:
        // https://github.com/module-federation/module-federation-examples/blob/9c4e554af5b5a7d4d2b1dce9adf263fd1a46d6b5/i18next-nextjs-react/i18next-shared-lib/package.json
        // https://github.com/module-federation/module-federation-examples/blob/9c4e554af5b5a7d4d2b1dce9adf263fd1a46d6b5/i18next-nextjs-react/i18next-shared-lib/src/i18nService.ts
        rewriteRun(
                json(
                        """
                        {
                          "name": "i18next-shared-lib",
                          "devDependencies": {
                            "typescript": "5.5.3",
                            "i18next": "21.10.0",
                            "i18next-browser-languagedetector": "6.1.8",
                            "react-i18next": "11.18.6",
                            "react": "18.3.1"
                          },
                          "peerDependencies": {
                            "i18next": "^21.10.0",
                            "i18next-browser-languagedetector": "^6.1.8",
                            "react-i18next": "^11.18.6",
                            "react": "^18.2.0"
                          }
                        }
                        """,
                        """
                        {
                          "name": "i18next-shared-lib",
                          "devDependencies": {
                            "typescript": "5.5.3",
                            "i18next": "25.10.10",
                            "i18next-browser-languagedetector": "6.1.8",
                            "react-i18next": "11.18.6",
                            "react": "18.3.1"
                          },
                          "peerDependencies": {
                            "i18next": "25.10.10",
                            "i18next-browser-languagedetector": "^6.1.8",
                            "react-i18next": "^11.18.6",
                            "react": "^18.2.0"
                          }
                        }
                        """,
                        spec -> spec.path("i18next-nextjs-react/i18next-shared-lib/package.json")
                ),
                text(
                        """
                        import i18next, { i18n, InitOptions as I18nInitOptions } from 'i18next';
                        const initOptions: I18nInitOptions = { initImmediate: false };
                        i18next.createInstance().init(initOptions);
                        """,
                        spec -> spec.path("i18next-nextjs-react/i18next-shared-lib/src/i18nService.ts")
                )
        );
    }

    @Test
    void upgradesNtfyNestedWebApplicationAndPreservesCompanionPackages() {
        // Reduced from binwiederhier/ntfy at 7680cb49. It is a nested Vite/React web app:
        // https://github.com/binwiederhier/ntfy/blob/7680cb490687e5e80b9d3ce501bb538db8ee1776/web/package.json
        // https://github.com/binwiederhier/ntfy/blob/7680cb490687e5e80b9d3ce501bb538db8ee1776/web/src/app/i18n.js
        rewriteRun(
                json(
                        """
                        {
                          "name": "ntfy",
                          "scripts": {"build": "vite build", "test": "vitest run"},
                          "dependencies": {
                            "i18next": "^21.6.14",
                            "i18next-browser-languagedetector": "^8.2.1",
                            "i18next-http-backend": "^4.0.0",
                            "react-i18next": "^11.16.2",
                            "react": "latest"
                          }
                        }
                        """,
                        """
                        {
                          "name": "ntfy",
                          "scripts": {"build": "vite build", "test": "vitest run"},
                          "dependencies": {
                            "i18next": "25.10.10",
                            "i18next-browser-languagedetector": "^8.2.1",
                            "i18next-http-backend": "^4.0.0",
                            "react-i18next": "^11.16.2",
                            "react": "latest"
                          }
                        }
                        """,
                        spec -> spec.path("web/package.json")
                ),
                text(
                        """
                        import i18next from "i18next";
                        import Backend from "i18next-http-backend";
                        import LanguageDetector from "i18next-browser-languagedetector";
                        import { initReactI18next } from "react-i18next";
                        i18next.use(Backend).use(LanguageDetector).use(initReactI18next).init({ fallbackLng: "en" });
                        """,
                        spec -> spec.path("web/src/app/i18n.js")
                )
        );
    }

    @Test
    void upgradesOpenMrsApplicationWithoutClaimingReactI18nextCompatibility() {
        // Reduced from openmrs/openmrs-esm-fast-data-entry-app at e7b81a0f:
        // https://github.com/openmrs/openmrs-esm-fast-data-entry-app/blob/e7b81a0fb60ccb028eb7dd74a5af30e79e75f593/package.json
        // https://github.com/openmrs/openmrs-esm-fast-data-entry-app/blob/e7b81a0fb60ccb028eb7dd74a5af30e79e75f593/src/CancelModal.tsx
        rewriteRun(
                json(
                        """
                        {
                          "name": "@openmrs/esm-fast-data-entry-app",
                          "dependencies": {"i18next": "^21.10.0"},
                          "devDependencies": {"typescript": "^5.0.0"},
                          "peerDependencies": {
                            "react": "18.x",
                            "react-i18next": "11.x"
                          }
                        }
                        """,
                        """
                        {
                          "name": "@openmrs/esm-fast-data-entry-app",
                          "dependencies": {"i18next": "25.10.10"},
                          "devDependencies": {"typescript": "^5.0.0"},
                          "peerDependencies": {
                            "react": "18.x",
                            "react-i18next": "11.x"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { useTranslation } from 'react-i18next';
                        const CancelModal = () => {
                          const { t } = useTranslation();
                          return t('areYouSure', 'Are you sure?');
                        };
                        """,
                        spec -> spec.path("src/CancelModal.tsx")
                )
        );
    }

    @Test
    void upgradesSleepingOwlVueApplicationAndLeavesRuntimeCallsForReview() {
        // Reduced from LaravelRUS/SleepingOwlAdmin at 2ef22d8e. It uses Vue 2 and a global helper:
        // https://github.com/LaravelRUS/SleepingOwlAdmin/blob/2ef22d8e656aa159a9e21f77ef603dbd259e431a/package.json
        // https://github.com/LaravelRUS/SleepingOwlAdmin/blob/2ef22d8e656aa159a9e21f77ef603dbd259e431a/resources/assets/js_owl/libs/i18next.js
        rewriteRun(
                json(
                        """
                        {
                          "scripts": {"development": "mix", "production": "mix --production"},
                          "dependencies": {
                            "i18next": "^21.10.0",
                            "laravel-mix": "^6.0.49",
                            "vue": "^2.7.16"
                          }
                        }
                        """,
                        """
                        {
                          "scripts": {"development": "mix", "production": "mix --production"},
                          "dependencies": {
                            "i18next": "25.10.10",
                            "laravel-mix": "^6.0.49",
                            "vue": "^2.7.16"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import i18next from 'i18next';
                        i18next.init({ lng: Admin.locale, resources: {} });
                        window.trans = (key) => i18next.t(key);
                        """,
                        spec -> spec.path("resources/assets/js_owl/libs/i18next.js")
                )
        );
    }

    @ParameterizedTest(name = "upgrades spreadsheet i18next version {0}")
    @ValueSource(strings = {"21.10.0", "21.6.14", "22.4.10", "22.4.9", "22.5.1"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(packageVersion("package.json", oldVersion));
    }

    @ParameterizedTest(name = "upgrades safe npm range {0}")
    @ValueSource(strings = {
            "^21.10.0",
            "~21.6.14",
            "v22.4.10",
            "=22.4.9",
            "22.5.1-rc.1",
            "21.10.0+vendor.3",
            ">=21.6.14 <23",
            " >= 22.4.9 <= 24",
            "21.6.14 - 22.5.1",
            "21.10.0 || ^22.5.1",
            "^22.4.10 || ~21.6.14"
    })
    void upgradesSupportedRegistrySemverForms(String oldVersion) {
        rewriteRun(packageVersion("package.json", oldVersion));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"i18next": "21.10.0"},
                  "devDependencies": {"i18next": "^21.6.14"},
                  "peerDependencies": {"i18next": ">=22.4.9 <23"},
                  "optionalDependencies": {"i18next": "~22.5.1"}
                }
                """,
                """
                {
                  "dependencies": {"i18next": "25.10.10"},
                  "devDependencies": {"i18next": "25.10.10"},
                  "peerDependencies": {"i18next": "25.10.10"},
                  "optionalDependencies": {"i18next": "25.10.10"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesWorkspaceChildManifestsButPreservesWorkspaceConfiguration() {
        rewriteRun(
                json(
                        """
                        {
                          "name": "translation-monorepo",
                          "private": true,
                          "workspaces": ["apps/*", "packages/*"],
                          "packageManager": "pnpm@10.0.0"
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                packageVersion("apps/web/package.json", "^21.10.0"),
                packageVersion("packages/i18n/package.json", "22.5.1")
        );
    }

    @Test
    void preservesAdjacentI18nextEcosystemPackages() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "i18next": "22.4.10",
                    "i18next-browser-languagedetector": "^8.2.1",
                    "i18next-fs-backend": "^2.6.0",
                    "i18next-http-backend": "^3.0.2",
                    "i18next-v4-format-converter": "^1.0.0",
                    "next-i18next": "^15.4.0",
                    "react-i18next": "^14.1.0"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "i18next": "25.10.10",
                    "i18next-browser-languagedetector": "^8.2.1",
                    "i18next-fs-backend": "^2.6.0",
                    "i18next-http-backend": "^3.0.2",
                    "i18next-v4-format-converter": "^1.0.0",
                    "next-i18next": "^15.4.0",
                    "react-i18next": "^14.1.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTargetAndTargetRangeUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"i18next": "25.10.10"},
                  "devDependencies": {"i18next": "^25.10.10"}
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
                  "dependencies": {"i18next": "25.10.11"},
                  "devDependencies": {"i18next": "^26.0.0"},
                  "peerDependencies": {"i18next": ">=27.0.0"}
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
                  "dependencies": {"i18next": "21.9.2"},
                  "devDependencies": {"i18next": "21.10.1"},
                  "peerDependencies": {"i18next": "22.4.8"},
                  "optionalDependencies": {"i18next": "22.5.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesBroadAndUnboundedRangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"i18next": ">=21"},
                  "devDependencies": {"i18next": "21.x"},
                  "peerDependencies": {"i18next": "*"},
                  "optionalDependencies": {"i18next": "latest"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void rejectsWorkspaceProtocolNpmAliasesAndLocalReferences() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"i18next": "workspace:^21.10.0"},
                  "devDependencies": {"i18next": "npm:@example/i18next@22.5.1"},
                  "peerDependencies": {"i18next": "link:../i18next"},
                  "optionalDependencies": {"i18next": "file:../i18next-22.4.10"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void rejectsGitGithubAndUrlReferences() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"i18next": "git+ssh://git@github.com/i18next/i18next.git#v21.10.0"},
                  "devDependencies": {"i18next": "github:i18next/i18next#v22.4.9"},
                  "peerDependencies": {"i18next": "https://registry.example/i18next-22.5.1.tgz"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void rejectsTagsWildcardsAndNonSemverText() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"i18next": "next"},
                  "devDependencies": {"i18next": "22.*"},
                  "peerDependencies": {"i18next": "21.10.0 compatible"},
                  "optionalDependencies": {"i18next": ""}
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
                  "dependencies": {"i18next": "21.10.00"},
                  "devDependencies": {"i18next": "21.6.140"},
                  "peerDependencies": {"i18next": "22.4.90"},
                  "optionalDependencies": {"i18next": "22.5.10"}
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
                  "overrides": {"i18next": "21.10.0"},
                  "resolutions": {"i18next": "21.6.14"},
                  "pnpm": {"overrides": {"i18next": "22.4.10"}},
                  "peerDependenciesMeta": {"i18next": {"optional": true}}
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
                    "": {"dependencies": {"i18next": "21.10.0"}},
                    "node_modules/i18next": {"version": "21.10.0"}
                  },
                  "dependencies": {"i18next": {"version": "21.10.0"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOrdinaryJsonOrTranslationResources() {
        rewriteRun(
                json(
                        "{\"dependencies\":{\"i18next\":\"22.5.1\"}}",
                        spec -> spec.path("fixtures/dependencies.json")
                ),
                json(
                        "{\"item_one\":\"one\",\"item_other\":\"many\"}",
                        spec -> spec.path("public/locales/en/translation.json")
                )
        );
    }

    @Test
    void doesNotModifySimilarNamesOrCaseVariants() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@types/i18next": "21.10.0",
                    "i18next-cli": "22.5.1",
                    "i18next-http-backend": "22.4.10",
                    "i18nextify": "21.6.14",
                    "I18next": "22.4.9"
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
        assertEquals("Upgrade i18next to 25.10.10", recipe.getDisplayName());
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static org.openrewrite.test.SourceSpecs packageVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"i18next\":\"" + version + "\"}}",
                "{\"dependencies\":{\"i18next\":\"25.10.10\"}}",
                spec -> spec.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.i18next")
                .scanYamlResources()
                .build();
    }
}
