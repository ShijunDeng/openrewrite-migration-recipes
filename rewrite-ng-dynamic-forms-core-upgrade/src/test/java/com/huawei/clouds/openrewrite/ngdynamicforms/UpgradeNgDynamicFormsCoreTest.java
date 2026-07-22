package com.huawei.clouds.openrewrite.ngdynamicforms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeNgDynamicFormsCoreTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.ngdynamicforms.UpgradeNgDynamicFormsCoreTo18_0_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesElectronMailerAngular12Application() {
        // Adapted from dhrn/electron-mailer-poc at 51602c24:
        // https://github.com/dhrn/electron-mailer-poc/blob/51602c24bc36c400fe8d5a28a02d7a06188aa11f/package.json
        rewriteRun(json(
                """
                {
                  "name": "mailer-poc",
                  "dependencies": {
                    "@angular/core": "^12.2.0",
                    "@angular/forms": "^12.2.0",
                    "@ng-dynamic-forms/core": "^14.0.0",
                    "@ng-dynamic-forms/ui-material": "^14.0.0",
                    "rxjs": "~6.6.0"
                  },
                  "devDependencies": {"typescript": "~4.3.5"}
                }
                """,
                """
                {
                  "name": "mailer-poc",
                  "dependencies": {
                    "@angular/core": "^12.2.0",
                    "@angular/forms": "^12.2.0",
                    "@ng-dynamic-forms/core": "18.0.0",
                    "@ng-dynamic-forms/ui-material": "^14.0.0",
                    "rxjs": "~6.6.0"
                  },
                  "devDependencies": {"typescript": "~4.3.5"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesAngularFormBuilderApplication() {
        // Adapted from Patrick5078/Angular-form-builder at 1a5d5f64:
        // https://github.com/Patrick5078/Angular-form-builder/blob/1a5d5f64142c68bf869ccd75b312a24fbac7c181/package.json
        rewriteRun(json(
                """
                {
                  "name": "form-builder",
                  "dependencies": {
                    "@angular/core": "~13.3.10",
                    "@angular/forms": "~13.3.10",
                    "@ng-dynamic-forms/core": "^15.0.0",
                    "@ng-dynamic-forms/ui-basic": "^15.0.0",
                    "rxjs": "~6.6.0"
                  },
                  "devDependencies": {"typescript": "~4.6.4"}
                }
                """,
                """
                {
                  "name": "form-builder",
                  "dependencies": {
                    "@angular/core": "~13.3.10",
                    "@angular/forms": "~13.3.10",
                    "@ng-dynamic-forms/core": "18.0.0",
                    "@ng-dynamic-forms/ui-basic": "^15.0.0",
                    "rxjs": "~6.6.0"
                  },
                  "devDependencies": {"typescript": "~4.6.4"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesMdsoarAngularApplication() {
        // Adapted from umd-lib/mdsoar-angular at 0e309c2b:
        // https://github.com/umd-lib/mdsoar-angular/blob/0e309c2b2aeba34c6815b5e4df56fc26fe1bbc4b/package.json
        rewriteRun(json(
                """
                {
                  "name": "dspace-angular",
                  "dependencies": {
                    "@angular/core": "^17.3.11",
                    "@angular/forms": "^17.3.11",
                    "@ng-dynamic-forms/core": "^16.0.0",
                    "rxjs": "^7.8.2"
                  },
                  "devDependencies": {"typescript": "~5.4.5"}
                }
                """,
                """
                {
                  "name": "dspace-angular",
                  "dependencies": {
                    "@angular/core": "^17.3.11",
                    "@angular/forms": "^17.3.11",
                    "@ng-dynamic-forms/core": "18.0.0",
                    "rxjs": "^7.8.2"
                  },
                  "devDependencies": {"typescript": "~5.4.5"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"14.0.0", "15.0.0", "16.0.0"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(json(
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"" + oldVersion + "\"}}",
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"18.0.0\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"14.0.1", "14.2.9", "15.1.0", "16.0.7", "17.0.0", "17.4.2"})
    void upgradesSupportedPatchesAndIntermediateMajor(String oldVersion) {
        rewriteRun(json(
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"" + oldVersion + "\"}}",
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"18.0.0\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "^14.0.0", "~15.0.0", ">=16.0.0 <18.0.0", "17.x", "14", "v15.0.0",
            "  >=16.0.0 <19", "~v17.0.0", "16.0.0-beta.1", "^14.0.0 || ^16.0.0"
    })
    void upgradesCommonRegistrySemverForms(String oldVersion) {
        rewriteRun(json(
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"" + oldVersion + "\"}}",
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"18.0.0\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@ng-dynamic-forms/core": "14.0.0"},
                  "devDependencies": {"@ng-dynamic-forms/core": "~15.0.0"},
                  "peerDependencies": {"@ng-dynamic-forms/core": ">=16.0.0 <18"},
                  "optionalDependencies": {"@ng-dynamic-forms/core": "^17.0.0"}
                }
                """,
                """
                {
                  "dependencies": {"@ng-dynamic-forms/core": "18.0.0"},
                  "devDependencies": {"@ng-dynamic-forms/core": "18.0.0"},
                  "peerDependencies": {"@ng-dynamic-forms/core": "18.0.0"},
                  "optionalDependencies": {"@ng-dynamic-forms/core": "18.0.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesNestedWorkspaceManifest() {
        rewriteRun(json(
                """
                {"name":"@example/forms","peerDependencies":{"@angular/core":">=16","@ng-dynamic-forms/core":"^16.0.0"}}
                """,
                """
                {"name":"@example/forms","peerDependencies":{"@angular/core":">=16","@ng-dynamic-forms/core":"18.0.0"}}
                """,
                spec -> spec.path("packages/forms/package.json")
        ));
    }

    @Test
    void upgradesDeepAngularWorkspaceApplication() {
        rewriteRun(json(
                """
                {"dependencies":{"@ng-dynamic-forms/core":"15.x"}}
                """,
                """
                {"dependencies":{"@ng-dynamic-forms/core":"18.0.0"}}
                """,
                spec -> spec.path("projects/portal/apps/admin/package.json")
        ));
    }

    @Test
    void preservesAngularRuntimeAndCompanionUiVersions() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/common": "^15.2.0",
                    "@angular/core": "^15.2.0",
                    "@angular/forms": "^15.2.0",
                    "@ng-dynamic-forms/core": "16.0.0",
                    "@ng-dynamic-forms/ui-material": "16.0.0",
                    "core-js": "^3.26.1",
                    "rxjs": "^7.5.7",
                    "zone.js": "~0.12.0"
                  },
                  "devDependencies": {"typescript": "~4.8.4"}
                }
                """,
                """
                {
                  "dependencies": {
                    "@angular/common": "^15.2.0",
                    "@angular/core": "^15.2.0",
                    "@angular/forms": "^15.2.0",
                    "@ng-dynamic-forms/core": "18.0.0",
                    "@ng-dynamic-forms/ui-material": "16.0.0",
                    "core-js": "^3.26.1",
                    "rxjs": "^7.5.7",
                    "zone.js": "~0.12.0"
                  },
                  "devDependencies": {"typescript": "~4.8.4"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void preservesJsonFormattingAndComments() {
        rewriteRun(json(
                """
                {
                  // Keep the application migration notes beside the dependency.
                  "dependencies": {
                    "@ng-dynamic-forms/core" : "^15.0.0",
                  },
                }
                """,
                """
                {
                  // Keep the application migration notes beside the dependency.
                  "dependencies": {
                    "@ng-dynamic-forms/core" : "18.0.0",
                  },
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOfficialTargetManifestShapeUntouched() {
        // Reduced from the official v18.0.0 package manifest:
        // https://github.com/udos86/ng-dynamic-forms/blob/v18.0.0/projects/ng-dynamic-forms/core/package.json
        rewriteRun(json(
                """
                {
                  "name": "@ng-dynamic-forms/core",
                  "version": "18.0.0",
                  "peerDependencies": {
                    "@angular/common": "^16.0.0",
                    "@angular/core": "^16.0.0",
                    "@angular/forms": "^16.0.0",
                    "core-js": "^3.31.0",
                    "rxjs": "^7.5.7"
                  },
                  "sideEffects": false,
                  "dependencies": {"tslib": "^2.0.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotDowngradeTargetOrNewerVersions() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@ng-dynamic-forms/core": "18.0.0"},
                  "devDependencies": {"@ng-dynamic-forms/core": "^18.0.1"},
                  "peerDependencies": {"@ng-dynamic-forms/core": "19.0.0"},
                  "optionalDependencies": {"@ng-dynamic-forms/core": "20.0.0-beta.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesExternalProtocolsAndAliasesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@ng-dynamic-forms/core": "workspace:^16.0.0"},
                  "devDependencies": {"@ng-dynamic-forms/core": "npm:@example/forms-core@16.0.0"},
                  "peerDependencies": {"@ng-dynamic-forms/core": "github:udos86/ng-dynamic-forms#v16.0.0"},
                  "optionalDependencies": {"@ng-dynamic-forms/core": "file:../core"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesUrlsAndTarballsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@ng-dynamic-forms/core": "https://registry.example/core-16.0.0.tgz"},
                  "devDependencies": {"@ng-dynamic-forms/core": "git+ssh://git@github.com/udos86/ng-dynamic-forms.git#v16.0.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOutOfScopeOldTagsAndUnboundedRangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@ng-dynamic-forms/core": "13.0.0"},
                  "devDependencies": {"@ng-dynamic-forms/core": "*"},
                  "peerDependencies": {"@ng-dynamic-forms/core": "latest"},
                  "optionalDependencies": {"@ng-dynamic-forms/core": "next"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotMatchVersionNumberSubstrings() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@ng-dynamic-forms/core": "114.0.0"},
                  "devDependencies": {"@ng-dynamic-forms/core": "1.16.0"},
                  "peerDependencies": {"@ng-dynamic-forms/core": "2.17.0"}
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
                  "packages": {
                    "": {"dependencies": {"@ng-dynamic-forms/core": "16.0.0"}},
                    "node_modules/@ng-dynamic-forms/core": {"version": "16.0.0"}
                  }
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                "{\"dependencies\":{\"@ng-dynamic-forms/core\":\"16.0.0\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNames() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@ng-dynamic-forms/core-testing": "16.0.0",
                    "@example/ng-dynamic-forms-core": "16.0.0",
                    "@ng-dynamic-forms/ui-basic": "16.0.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyManifestWithoutCoreDependency() {
        rewriteRun(json(
                """
                {"name":"forms-app","dependencies":{"@angular/forms":"16.2.12","rxjs":"7.8.1"}}
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngdynamicforms")
                .scanYamlResources()
                .build();
    }
}
