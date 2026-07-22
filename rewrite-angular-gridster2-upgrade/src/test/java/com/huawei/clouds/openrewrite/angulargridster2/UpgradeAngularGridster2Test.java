package com.huawei.clouds.openrewrite.angulargridster2;

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

class UpgradeAngularGridster2Test implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.angulargridster2.UpgradeAngularGridster2To20_2_4";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesHyperIotAngularTwelveDashboardAndPreservesSource() {
        // Reduced from HyperIoT-Labs/HyperIoT-UI at da8e6ebd:
        // https://github.com/HyperIoT-Labs/HyperIoT-UI/blob/da8e6ebd16aba40fc1996e6da706b2954c3b12f3/package.json
        // https://github.com/HyperIoT-Labs/HyperIoT-UI/blob/da8e6ebd16aba40fc1996e6da706b2954c3b12f3/projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.ts
        rewriteRun(
                json(
                        """
                        {
                          "name": "hyperiot",
                          "dependencies": {
                            "@angular/core": "12.2.16",
                            "angular-gridster2": "~12.1.1",
                            "rxjs": "7.5.5",
                            "zone.js": "0.11.8"
                          }
                        }
                        """,
                        """
                        {
                          "name": "hyperiot",
                          "dependencies": {
                            "@angular/core": "12.2.16",
                            "angular-gridster2": "20.2.4",
                            "rxjs": "7.5.5",
                            "zone.js": "0.11.8"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { GridsterConfig, GridsterItem } from 'angular-gridster2';
                        export class WidgetsLayoutComponent {
                          options: GridsterConfig = { draggable: { enabled: true } };
                          dashboard: Array<GridsterItem> = [];
                        }
                        """,
                        spec -> spec.path("projects/widgets/src/lib/dashboard/widgets-layout/widgets-layout.component.ts")
                )
        );
    }

    @Test
    void upgradesErniAngularThirteenStarter() {
        // Reduced from ERNI-Academy/starterkit-angular-and-dotnet-api at 72188b3c:
        // https://github.com/ERNI-Academy/starterkit-angular-and-dotnet-api/blob/72188b3cb657b4a7d318f4e29ae751995d5efe5b/src/App.UI/package.json
        rewriteRun(json(
                """
                {
                  "name": "appui",
                  "dependencies": {
                    "@angular/core": "~13.2.1",
                    "angular-gridster2": "^13.3.0",
                    "rxjs": "~6.6.6"
                  }
                }
                """,
                """
                {
                  "name": "appui",
                  "dependencies": {
                    "@angular/core": "~13.2.1",
                    "angular-gridster2": "20.2.4",
                    "rxjs": "~6.6.6"
                  }
                }
                """,
                spec -> spec.path("src/App.UI/package.json")
        ));
    }

    @Test
    void upgradesFischertechnikAngularThirteenWorkspaceAndPreservesModuleUse() {
        // Reduced from fischertechnik/Agile-Production-Simulation-24V-Dev at 24568073:
        // https://github.com/fischertechnik/Agile-Production-Simulation-24V-Dev/blob/24568073f7f70d0d31dcaa83bcfcd7b595baba1f/frontend/package.json
        // https://github.com/fischertechnik/Agile-Production-Simulation-24V-Dev/blob/24568073f7f70d0d31dcaa83bcfcd7b595baba1f/frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.ts
        rewriteRun(
                json(
                        """
                        {
                          "name": "ff-frontend",
                          "dependencies": {
                            "@angular/core": "13.4.0",
                            "angular-gridster2": "^13.3.2",
                            "rxjs": "~7.5.0"
                          }
                        }
                        """,
                        """
                        {
                          "name": "ff-frontend",
                          "dependencies": {
                            "@angular/core": "13.4.0",
                            "angular-gridster2": "20.2.4",
                            "rxjs": "~7.5.0"
                          }
                        }
                        """,
                        spec -> spec.path("frontend/package.json")
                ),
                text(
                        """
                        import { GridsterConfig, GridsterItem } from 'angular-gridster2';
                        export class FactoryLayoutComponent {
                          options: GridsterConfig;
                          dashboard: GridsterItem[];
                          changedOptions() { this.options.api?.optionsChanged?.(); }
                        }
                        """,
                        spec -> spec.path("frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.ts")
                )
        );
    }

    @Test
    void upgradesSostradesAngularSixteenDashboardAndPreservesCompanions() {
        // Reduced from os-climate/sostrades-webgui at fe148be3:
        // https://github.com/os-climate/sostrades-webgui/blob/fe148be3c158cd681fb7bce739575f9d24f2c2d2/package.json
        // https://github.com/os-climate/sostrades-webgui/blob/fe148be3c158cd681fb7bce739575f9d24f2c2d2/src/app/modules/dashboard/dashboard.component.ts
        rewriteRun(
                json(
                        """
                        {
                          "name": "sos-trades-gui",
                          "dependencies": {
                            "@angular/core": "16.2.12",
                            "@angular/material": "16.2.12",
                            "angular-gridster2": "^16.0.0",
                            "rxjs": "7.8.1"
                          }
                        }
                        """,
                        """
                        {
                          "name": "sos-trades-gui",
                          "dependencies": {
                            "@angular/core": "16.2.12",
                            "@angular/material": "16.2.12",
                            "angular-gridster2": "20.2.4",
                            "rxjs": "7.8.1"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { GridsterConfig, GridsterItem } from 'angular-gridster2';
                        export class DashboardComponent {
                          options: GridsterConfig = { mobileBreakpoint: 640 };
                          dashboard: GridsterItem[] = [];
                        }
                        """,
                        spec -> spec.path("src/app/modules/dashboard/dashboard.component.ts")
                )
        );
    }

    @ParameterizedTest(name = "upgrades spreadsheet angular-gridster2 version {0}")
    @ValueSource(strings = {"12.1.1", "13.3.0", "13.3.2", "16.0.0"})
    void upgradesEverySpreadsheetVersion(String version) {
        rewriteRun(packageVersion("package.json", version));
    }

    @ParameterizedTest(name = "upgrades safe selected declaration {0}")
    @ValueSource(strings = {
            "^12.1.1", "~12.1.1", "=12.1.1", "v12.1.1", "^v12.1.1",
            "^13.3.0", "~13.3.0", "=13.3.0", "v13.3.0", "^v13.3.0",
            "^13.3.2", "~13.3.2", "=13.3.2", "v13.3.2", "^v13.3.2",
            "^16.0.0", "~16.0.0", "=16.0.0", "v16.0.0", "^v16.0.0"
    })
    void upgradesSupportedRegistryDeclarations(String declaration) {
        rewriteRun(packageVersion("package.json", declaration));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"angular-gridster2": "12.1.1"},
                  "devDependencies": {"angular-gridster2": "^13.3.0"},
                  "peerDependencies": {"angular-gridster2": "~13.3.2"},
                  "optionalDependencies": {"angular-gridster2": "=16.0.0"}
                }
                """,
                """
                {
                  "dependencies": {"angular-gridster2": "20.2.4"},
                  "devDependencies": {"angular-gridster2": "20.2.4"},
                  "peerDependencies": {"angular-gridster2": "20.2.4"},
                  "optionalDependencies": {"angular-gridster2": "20.2.4"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesWorkspaceChildrenWithoutChangingWorkspaceConfiguration() {
        rewriteRun(
                json(
                        "{\"private\":true,\"workspaces\":[\"apps/*\",\"packages/*\"]}",
                        spec -> spec.path("package.json")
                ),
                packageVersion("apps/dashboard/package.json", "^13.3.2"),
                packageVersion("packages/widgets/package.json", "16.0.0")
        );
    }

    @Test
    void preservesAngularRxjsMaterialAndDifferentGridPackages() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/common": "16.2.12",
                    "@angular/core": "16.2.12",
                    "@angular/material": "16.2.12",
                    "angular-gridster2": "16.0.0",
                    "gridstack": "7.1.1",
                    "ngx-gridster": "16.0.0",
                    "rxjs": "7.8.1",
                    "zone.js": "0.13.3"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@angular/common": "16.2.12",
                    "@angular/core": "16.2.12",
                    "@angular/material": "16.2.12",
                    "angular-gridster2": "20.2.4",
                    "gridstack": "7.1.1",
                    "ngx-gridster": "16.0.0",
                    "rxjs": "7.8.1",
                    "zone.js": "0.13.3"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves unlisted version {0} untouched")
    @ValueSource(strings = {
            "12.1.0", "12.1.2", "13.2.0", "13.3.1", "13.3.3", "14.1.0",
            "15.0.4", "16.0.1", "16.2.0", "17.0.0", "18.0.1", "19.0.0"
    })
    void leavesUnlistedVersionsUntouched(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"angular-gridster2\":\"" + version + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves target or later declaration {0} untouched")
    @ValueSource(strings = {"20.2.4", "^20.2.4", "20.2.5", "21.0.0", "22.0.0-next.1"})
    void leavesTargetAndNewerVersionsUntouched(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"angular-gridster2\":\"" + version + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "rejects ambiguous declaration {0}")
    @ValueSource(strings = {
            ">=12.1.1 <13", "13.x", "12.1.1 - 16.0.0", "13.3.0 || ^16.0.0",
            "13.3.2-rc.1", "16.0.0+company.2", "latest", "next", "*", "${GRIDSTER_VERSION}"
    })
    void leavesRangesPrereleasesTagsAndVariablesUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"angular-gridster2\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "rejects non-registry reference {0}")
    @ValueSource(strings = {
            "workspace:^16.0.0", "catalog:angular-gridster2", "npm:@example/gridster@16.0.0",
            "file:../gridster", "link:../gridster", "portal:../gridster",
            "github:tiberiuzuld/angular-gridster2#v16.0.0",
            "git+ssh://git@github.com/tiberiuzuld/angular-gridster2.git#v16.0.0",
            "https://registry.example/angular-gridster2-16.0.0.tgz"
    })
    void leavesProtocolsAliasesAndExternalReferencesUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"angular-gridster2\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOverridesResolutionsAndPeerMetadataUntouched() {
        rewriteRun(json(
                """
                {
                  "overrides": {"angular-gridster2": "12.1.1"},
                  "resolutions": {"angular-gridster2": "13.3.0"},
                  "pnpm": {"overrides": {"angular-gridster2": "13.3.2"}},
                  "peerDependenciesMeta": {"angular-gridster2": {"optional": true}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNonStringDependencyValuesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"angular-gridster2": {"version": "16.0.0"}},
                  "devDependencies": {"angular-gridster2": 16},
                  "peerDependencies": {"angular-gridster2": true},
                  "optionalDependencies": {"angular-gridster2": null}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyLockfilesOtherJsonOrSimilarNames() {
        rewriteRun(
                json(
                        """
                        {"packages":{"":{"dependencies":{"angular-gridster2":"16.0.0"}},"node_modules/angular-gridster2":{"version":"16.0.0"}}}
                        """,
                        spec -> spec.path("package-lock.json")
                ),
                json(
                        "{\"dependencies\":{\"angular-gridster2\":\"16.0.0\"}}",
                        spec -> spec.path("fixtures/dependencies.json")
                ),
                json(
                        """
                        {"dependencies":{"angular-gridster":"16.0.0","angular-gridster2-plugin":"16.0.0","Angular-Gridster2":"16.0.0"}}
                        """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void leavesTemplatesStylesAndBuildConfigurationUntouched() {
        rewriteRun(
                text(
                        """
                        <gridster [options]="options">
                          <gridster-item *ngFor="let item of dashboard" [item]="item"></gridster-item>
                        </gridster>
                        """,
                        spec -> spec.path("src/app/dashboard.component.html")
                ),
                text(
                        """
                        gridster { display: flex; height: 100%; }
                        gridster-item { overflow: hidden; }
                        """,
                        spec -> spec.path("src/app/dashboard.component.scss")
                ),
                json(
                        "{\"projects\":{\"app\":{\"architect\":{\"build\":{\"options\":{\"styles\":[]}}}}}}",
                        spec -> spec.path("angular.json")
                )
        );
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertEquals("Upgrade angular-gridster2 to 20.2.4", recipe.getDisplayName());
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static SourceSpecs packageVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"angular-gridster2\":\"" + version + "\"}}",
                "{\"dependencies\":{\"angular-gridster2\":\"20.2.4\"}}",
                spec -> spec.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angulargridster2")
                .scanYamlResources()
                .build();
    }
}
