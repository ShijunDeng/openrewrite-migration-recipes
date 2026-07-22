package com.huawei.clouds.openrewrite.ngxinfinitescroll;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeNgxInfiniteScrollTest implements RewriteTest {
    private static final String UPGRADE_RECIPE =
            "com.huawei.clouds.openrewrite.ngxinfinitescroll.UpgradeNgxInfiniteScrollTo17_0_1";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.ngxinfinitescroll.MigrateInfiniteScrollModuleToStandaloneDirective";
    private static final String AUDIT_RECIPE =
            "com.huawei.clouds.openrewrite.ngxinfinitescroll.AuditNgxInfiniteScroll17Compatibility";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.ngxinfinitescroll.MigrateNgxInfiniteScrollTo17_0_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE_RECIPE));
    }

    @ParameterizedTest(name = "upgrades {0} {1}")
    @MethodSource("spreadsheetVersionsAndSections")
    void upgradesEverySpreadsheetVersionInEveryDirectSection(String section, String version) {
        rewriteRun(packageVersion(section, version, "matrix/" + section + "/" + version + "/package.json"));
    }

    @ParameterizedTest(name = "upgrades selected semver declaration {0}")
    @ValueSource(strings = {
            "^9.1.0", "~9.1.0",
            "^10.0.1", "~10.0.1",
            "^13.0.1", "~13.0.1",
            "^13.1.0", "~13.1.0",
            "^14.0.1", "~14.0.1"
    })
    void upgradesOnlyCommonAnchoredPrefixes(String declaration) {
        rewriteRun(packageVersion("dependencies", declaration, "prefix/" + declaration.hashCode() + "/package.json"));
    }

    @Test
    void upgradesAllFourDirectDependencySectionsTogether() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"ngx-infinite-scroll": "9.1.0"},
                  "devDependencies": {"ngx-infinite-scroll": "^10.0.1"},
                  "peerDependencies": {"ngx-infinite-scroll": "~13.1.0"},
                  "optionalDependencies": {"ngx-infinite-scroll": "14.0.1"}
                }
                """,
                """
                {
                  "dependencies": {"ngx-infinite-scroll": "17.0.1"},
                  "devDependencies": {"ngx-infinite-scroll": "17.0.1"},
                  "peerDependencies": {"ngx-infinite-scroll": "17.0.1"},
                  "optionalDependencies": {"ngx-infinite-scroll": "17.0.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void migratesMaherNajarShopModuleAndDependency() {
        // Reduced from MaherNajar/shop at 4b199ca08ae3795ef311936ec2c5bc0f6a13b255.
        // https://github.com/MaherNajar/shop/blob/4b199ca08ae3795ef311936ec2c5bc0f6a13b255/package.json
        // https://github.com/MaherNajar/shop/blob/4b199ca08ae3795ef311936ec2c5bc0f6a13b255/src/app/components/products/products.module.ts
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                json(
                        """
                        {"dependencies":{"@angular/core":"~9.1.7","ngx-infinite-scroll":"^9.1.0"}}
                        """,
                        """
                        {"dependencies":{/*~~>*/"@angular/core":"~9.1.7","ngx-infinite-scroll":"17.0.1"}}
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { CommonModule } from '@angular/common';
                        import { NgModule } from '@angular/core';
                        import { InfiniteScrollModule } from 'ngx-infinite-scroll';

                        @NgModule({
                          imports: [CommonModule, InfiniteScrollModule],
                          exports: [InfiniteScrollModule]
                        })
                        export class ProductsModule {}
                        """,
                        """
                        import { CommonModule } from '@angular/common';
                        import { NgModule } from '@angular/core';
                        import { InfiniteScrollDirective } from 'ngx-infinite-scroll';

                        @NgModule({
                          imports: [CommonModule, InfiniteScrollDirective],
                          exports: [InfiniteScrollDirective]
                        })
                        export class ProductsModule {}
                        """,
                        spec -> spec.path("src/app/components/products/products.module.ts")
                )
        );
    }

    @Test
    void migratesAngular4RedditApplicationAndTestBed() {
        // Reduced from punkrocker178/angular4reddit at f20f806e0710deffd5109ae612b3ab9ac6532520.
        // https://github.com/punkrocker178/angular4reddit/blob/f20f806e0710deffd5109ae612b3ab9ac6532520/package.json
        // https://github.com/punkrocker178/angular4reddit/blob/f20f806e0710deffd5109ae612b3ab9ac6532520/src/app/app.module.ts
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                json(
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"^10.0.1\"}}",
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"17.0.1\"}}",
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                        import { NgModule } from '@angular/core';

                        @NgModule({
                            imports: [
                                InfiniteScrollModule,
                            ],
                        })
                        export class AppModule {}
                        """,
                        """
                        import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                        import { NgModule } from '@angular/core';

                        @NgModule({
                            imports: [
                                InfiniteScrollDirective,
                            ],
                        })
                        export class AppModule {}
                        """,
                        spec -> spec.path("src/app/app.module.ts")
                ),
                text(
                        """
                        import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                        TestBed.configureTestingModule({ imports: [InfiniteScrollModule] });
                        """,
                        """
                        import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                        TestBed.configureTestingModule({ imports: [InfiniteScrollDirective] });
                        """,
                        spec -> spec.path("src/app/app.component.spec.ts")
                )
        );
    }

    @Test
    void migratesJHipsterGeneratedSharedModule() {
        // Reduced from jhipster/generator-jhipster at 47567f4dfb08935c7e4f89fd2113618e3e25fc1a.
        // https://github.com/jhipster/generator-jhipster/blob/47567f4dfb08935c7e4f89fd2113618e3e25fc1a/generators/client/templates/angular/package.json
        // https://github.com/jhipster/generator-jhipster/blob/47567f4dfb08935c7e4f89fd2113618e3e25fc1a/generators/client/templates/angular/src/main/webapp/app/shared/shared-libs.module.ts.ejs
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                json(
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"13.0.1\"}}",
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"17.0.1\"}}",
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                        @NgModule({
                          imports: [InfiniteScrollModule],
                          exports: [InfiniteScrollModule]
                        })
                        export class SharedLibsModule {}
                        """,
                        """
                        import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                        @NgModule({
                          imports: [InfiniteScrollDirective],
                          exports: [InfiniteScrollDirective]
                        })
                        export class SharedLibsModule {}
                        """,
                        spec -> spec.path("src/main/webapp/app/shared/shared-libs.module.ts")
                )
        );
    }

    @Test
    void migratesCessdaSharedLibrariesModule() {
        // Reduced from cessda/cessda.cvs.two at 60c3bc60a5f87f04c1420aad8b1d066ff2bf8942.
        // https://github.com/cessda/cessda.cvs.two/blob/60c3bc60a5f87f04c1420aad8b1d066ff2bf8942/package.json
        // https://github.com/cessda/cessda.cvs.two/blob/60c3bc60a5f87f04c1420aad8b1d066ff2bf8942/src/main/webapp/app/shared/shared-libs.module.ts
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                json(
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"14.0.1\"}}",
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"17.0.1\"}}",
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { CommonModule } from '@angular/common';
                        import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                        import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

                        @NgModule({
                          imports: [CommonModule, InfiniteScrollModule, FontAwesomeModule],
                          exports: [CommonModule, InfiniteScrollModule, FontAwesomeModule]
                        })
                        export class SharedLibsModule {}
                        """,
                        """
                        import { CommonModule } from '@angular/common';
                        import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                        import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

                        @NgModule({
                          imports: [CommonModule, InfiniteScrollDirective, FontAwesomeModule],
                          exports: [CommonModule, InfiniteScrollDirective, FontAwesomeModule]
                        })
                        export class SharedLibsModule {}
                        """,
                        spec -> spec.path("src/main/webapp/app/shared/shared-libs.module.ts")
                )
        );
    }

    @Test
    void migratesCodeCrowApplicationModule() {
        // Reduced from CodeCrowCorp/cro-website at 4076096174c4398061d8cbbd520020c4f5ec158e,
        // retained by the public da7a90-backup/cro-website mirror at the same commit.
        // https://github.com/da7a90-backup/cro-website/blob/4076096174c4398061d8cbbd520020c4f5ec158e/package.json
        // https://github.com/da7a90-backup/cro-website/blob/4076096174c4398061d8cbbd520020c4f5ec158e/src/app/app.module.ts
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                json(
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"^14.0.1\"}}",
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"17.0.1\"}}",
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { InfiniteScrollModule } from 'ngx-infinite-scroll'
                        @NgModule({
                            imports: [InfiniteScrollModule],
                        })
                        export class AppModule {}
                        """,
                        """
                        import { InfiniteScrollDirective } from 'ngx-infinite-scroll'
                        @NgModule({
                            imports: [InfiniteScrollDirective],
                        })
                        export class AppModule {}
                        """,
                        spec -> spec.path("src/app/app.module.ts")
                )
        );
    }

    @Test
    void sourceRecipeHandlesMultilineImportAndMetadata() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import {
                          InfiniteScrollModule,
                          IInfiniteScrollEvent
                        } from "ngx-infinite-scroll";

                        @NgModule({
                          imports: [
                            CommonModule,
                            InfiniteScrollModule
                          ],
                          exports: [InfiniteScrollModule]
                        })
                        export class FeedModule {}
                        """,
                        """
                        import {
                          InfiniteScrollDirective,
                          IInfiniteScrollEvent
                        } from "ngx-infinite-scroll";

                        @NgModule({
                          imports: [
                            CommonModule,
                            InfiniteScrollDirective
                          ],
                          exports: [InfiniteScrollDirective]
                        })
                        export class FeedModule {}
                        """,
                        spec -> spec.path("src/feed.module.ts")
                )
        );
    }

    @Test
    void sourceRecipePreservesImportAlias() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import { InfiniteScrollModule as ScrollImports } from 'ngx-infinite-scroll';
                        @NgModule({ imports: [ScrollImports] })
                        export class FeedModule {}
                        """,
                        """
                        import { InfiniteScrollDirective as ScrollImports } from 'ngx-infinite-scroll';
                        @NgModule({ imports: [ScrollImports] })
                        export class FeedModule {}
                        """,
                        spec -> spec.path("src/feed.module.ts")
                )
        );
    }

    @Test
    void sourceRecipeDoesNotRewriteAmbiguousDualImport() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import { InfiniteScrollDirective, InfiniteScrollModule } from 'ngx-infinite-scroll';
                        @NgModule({ imports: [InfiniteScrollModule] })
                        export class LegacyModule {}
                        """,
                        spec -> spec.path("src/legacy.module.ts")
                )
        );
    }

    @Test
    void sourceRecipeDoesNotRewriteSameSymbolFromAnotherPackage() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import { InfiniteScrollModule } from '@example/scroll';
                        @NgModule({ imports: [InfiniteScrollModule] })
                        export class OtherModule {}
                        """,
                        spec -> spec.path("src/other.module.ts")
                )
        );
    }

    @Test
    void upgradesWorkspaceChildWithoutChangingWorkspaceRoot() {
        rewriteRun(
                json(
                        "{\"name\":\"ui-workspace\",\"private\":true,\"workspaces\":[\"apps/*\"]}",
                        spec -> spec.path("package.json")
                ),
                json(
                        "{\"name\":\"feed\",\"dependencies\":{\"ngx-infinite-scroll\":\"~13.1.0\"}}",
                        "{\"name\":\"feed\",\"dependencies\":{\"ngx-infinite-scroll\":\"17.0.1\"}}",
                        spec -> spec.path("apps/feed/package.json")
                )
        );
    }

    @Test
    void preservesFormattingAndAdjacentAngularDependencies() {
        rewriteRun(json(
                """
                {
                    "dependencies" : {
                        "@angular/common" : "~14.2.0",
                        "@angular/core" : "~14.2.0",
                        "ngx-infinite-scroll" : "~14.0.1",
                        "rxjs" : "~7.5.0"
                    }
                }
                """,
                """
                {
                    "dependencies" : {
                        "@angular/common" : "~14.2.0",
                        "@angular/core" : "~14.2.0",
                        "ngx-infinite-scroll" : "17.0.1",
                        "rxjs" : "~7.5.0"
                    }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void usesOnlyTargetLeafValueAsPredicate() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@example/widget": "14.0.1",
                    "ngx-infinite-scroll": "16.0.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves unlisted or current version {0}")
    @ValueSource(strings = {
            "9.0.0", "9.1.1", "10.0.0", "10.0.2", "10.1.0",
            "11.0.0", "12.0.0", "13.0.0", "13.0.2", "13.1.1",
            "14.0.0", "14.0.2", "15.0.0", "16.0.0", "17.0.0",
            "17.0.1", "^17.0.1", "~17.0.1", "17.0.2", "18.0.0", "22.0.0"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String declaration) {
        rewriteRun(noChangeVersion(declaration, "unlisted/" + declaration.hashCode() + "/package.json"));
    }

    @ParameterizedTest(name = "leaves unsupported semver declaration {0}")
    @ValueSource(strings = {
            "=9.1.0", "=10.0.1", "=13.0.1", "=13.1.0", "=14.0.1",
            ">=9.1.0", ">10.0.1", "<=14.0.1", "9.1.0 || 14.0.1",
            "10.0.1 - 14.0.1", "13.x", "*", "latest", "next",
            "13.1.0-beta.1", "14.0.1+company.2", "${NGX_SCROLL_VERSION}", "$scrollVersion"
    })
    void leavesRangesTagsPrereleasesBuildsAndVariablesUntouched(String declaration) {
        rewriteRun(noChangeVersion(declaration, "ambiguous/" + declaration.hashCode() + "/package.json"));
    }

    @ParameterizedTest(name = "leaves protocol or alias {0}")
    @ValueSource(strings = {
            "workspace:9.1.0", "workspace:^14.0.1", "npm:@example/scroll@13.1.0",
            "file:../ngx-infinite-scroll", "link:../ngx-infinite-scroll",
            "portal:../ngx-infinite-scroll", "catalog:ngx-infinite-scroll",
            "github:orizens/ngx-infinite-scroll#v14.0.1",
            "git+https://github.com/orizens/ngx-infinite-scroll.git#bf09910",
            "https://registry.example/ngx-infinite-scroll-13.0.1.tgz"
    })
    void leavesProtocolsAliasesAndExternalReferencesUntouched(String declaration) {
        rewriteRun(noChangeVersion(declaration, "references/" + declaration.hashCode() + "/package.json"));
    }

    @Test
    void leavesNullObjectArrayBooleanAndNumberValuesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"ngx-infinite-scroll": null},
                  "devDependencies": {"ngx-infinite-scroll": {"version": "14.0.1"}},
                  "peerDependencies": {"ngx-infinite-scroll": ["13.1.0"]},
                  "optionalDependencies": {"ngx-infinite-scroll": false},
                  "custom": {"ngx-infinite-scroll": 14.01}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOverridesResolutionsAndPackageManagerConfigurationUntouched() {
        rewriteRun(json(
                """
                {
                  "overrides": {"ngx-infinite-scroll": "14.0.1"},
                  "resolutions": {"ngx-infinite-scroll": "13.1.0"},
                  "pnpm": {"overrides": {"ngx-infinite-scroll": "13.0.1"}},
                  "dependenciesMeta": {"ngx-infinite-scroll": {"built": false}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesSimilarPackageNamesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@types/ngx-infinite-scroll": "14.0.1",
                    "@example/ngx-infinite-scroll": "13.1.0",
                    "angular2-infinite-scroll": "10.0.1",
                    "ngx-infinite-scroll-component": "9.1.0",
                    "react-infinite-scroll-component": "14.0.1"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesPackageLockAndOtherJsonUntouched() {
        rewriteRun(
                json(
                        """
                        {"packages":{"":{"dependencies":{"ngx-infinite-scroll":"14.0.1"}},"node_modules/ngx-infinite-scroll":{"version":"14.0.1"}}}
                        """,
                        spec -> spec.path("package-lock.json")
                ),
                json(
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"13.1.0\"}}",
                        spec -> spec.path("fixtures/dependencies.json")
                ),
                json(
                        "{\"dependencies\":{\"ngx-infinite-scroll\":\"10.0.1\"}}",
                        spec -> spec.path("package.json.backup")
                )
        );
    }

    @Test
    void leavesPnpmYarnAndNpmShrinkwrapUntouched() {
        rewriteRun(
                text("ngx-infinite-scroll@14.0.1:\n  resolution: {integrity: sha512-example}\n", spec -> spec.path("pnpm-lock.yaml")),
                text("ngx-infinite-scroll@^13.1.0:\n  version \"13.1.0\"\n", spec -> spec.path("yarn.lock")),
                json("{\"dependencies\":{\"ngx-infinite-scroll\":{\"version\":\"10.0.1\"}}}", spec -> spec.path("npm-shrinkwrap.json"))
        );
    }

    @Test
    void dependencyRecipeLeavesSourceTemplatesAndConfigurationUntouched() {
        rewriteRun(
                text(
                        "import { InfiniteScrollModule } from 'ngx-infinite-scroll';\n",
                        spec -> spec.path("src/app/app.module.ts")
                ),
                text(
                        "<div infiniteScroll [scrollWindow]=\"false\" (scrolled)=\"next()\"></div>\n",
                        spec -> spec.path("src/app/feed.component.html")
                ),
                text(
                        "export default { optimizeDeps: { include: ['ngx-infinite-scroll'] } };\n",
                        spec -> spec.path("vite.config.ts")
                ),
                json(
                        "{\"compilerOptions\":{\"target\":\"ES2022\"}}",
                        spec -> spec.path("tsconfig.json")
                )
        );
    }

    @Test
    void auditMarksAngularPeersDeprecatedModuleDeepImportAndSensitiveInputs() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                json(
                        "{\"dependencies\":{\"@angular/core\":\"~14.2.0\",\"@angular/common\":\"~14.2.0\"}}",
                        "{\"dependencies\":{/*~~>*/\"@angular/core\":\"~14.2.0\",/*~~>*/\"@angular/common\":\"~14.2.0\"}}",
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { InfiniteScrollModule } from 'ngx-infinite-scroll';
                        import { helper } from 'ngx-infinite-scroll/lib/services/scroll-register';
                        """,
                        """
                        import { ~~(InfiniteScrollModule)~~>InfiniteScrollModule } from 'ngx-infinite-scroll';
                        import { helper } ~~(from 'ngx-infinite-scroll/)~~>from 'ngx-infinite-scroll/lib/services/scroll-register';
                        """,
                        spec -> spec.path("src/legacy.ts")
                ),
                text(
                        """
                        <div infiniteScroll [scrollWindow]="false" [infiniteScrollThrottle]="1500"></div>
                        """,
                        """
                        <div infiniteScroll [~~(scrollWindow)~~>scrollWindow]="false" [~~(infiniteScrollThrottle)~~>infiniteScrollThrottle]="1500"></div>
                        """,
                        spec -> spec.path("src/feed.html")
                )
        );
    }

    @Test
    void discoversAndValidatesAllRecipes() {
        Environment environment = environment();
        for (String name : new String[]{UPGRADE_RECIPE, SOURCE_RECIPE, AUDIT_RECIPE, MIGRATION_RECIPE}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> name + ": " + recipe.validate().failures());
        }
    }

    private static Stream<Arguments> spreadsheetVersionsAndSections() {
        return Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                .flatMap(section -> Stream.of("9.1.0", "10.0.1", "13.0.1", "13.1.0", "14.0.1")
                        .map(version -> Arguments.of(section, version)));
    }

    private static SourceSpecs packageVersion(String section, String declaration, String path) {
        return json(
                "{\"" + section + "\":{\"ngx-infinite-scroll\":\"" + declaration + "\"}}",
                "{\"" + section + "\":{\"ngx-infinite-scroll\":\"17.0.1\"}}",
                spec -> spec.path(path)
        );
    }

    private static SourceSpecs noChangeVersion(String declaration, String path) {
        return json(
                "{\"dependencies\":{\"ngx-infinite-scroll\":\"" + declaration + "\"}}",
                spec -> spec.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxinfinitescroll")
                .scanYamlResources()
                .build();
    }
}
