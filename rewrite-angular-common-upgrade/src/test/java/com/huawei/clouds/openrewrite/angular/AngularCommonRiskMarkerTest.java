package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class AngularCommonRiskMarkerTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksMixedXhrFactoryImportFromPinnedNativeScriptFixture() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTypeScriptRisks()),
                typescript(
                        "import { HttpRequest, HttpBackend, XhrFactory, HttpResponse } from '@angular/common/http';\n",
                        "/*~~(XhrFactory moved to @angular/common; this mixed HTTP import needs an intentional split)~~>*/import { HttpRequest, HttpBackend, XhrFactory, HttpResponse } from '@angular/common/http';\n",
                        source -> source.path("nativescript-angular/http-client/ns-http-backend.ts")
                )
        );
    }

    @Test
    void marksLegacyHttpModuleFromPinnedNgcValidateFixture() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTypeScriptRisks()),
                typescript(
                        "import { HttpClientModule } from '@angular/common/http';\n",
                        "/*~~(HttpClientModule uses legacy HTTP NgModule/DI configuration; choose provideHttpClient features and preserve interceptor/XSRF order explicitly)~~>*/import { HttpClientModule } from '@angular/common/http';\n",
                        source -> source.path("wardbell/ngc-validate/src/main.ts")
                )
        );
    }

    @Test
    void marksDocumentOnlyWhenMixedBecauseStandaloneImportIsAutoSafe() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTypeScriptRisks()),
                typescript(
                        "import { CommonModule, DOCUMENT } from '@angular/common';\n",
                        "/*~~(DOCUMENT is exported from @angular/core in Angular 20; this mixed import needs an intentional split/merge)~~>*/import { CommonModule, DOCUMENT } from '@angular/common';\n",
                        source -> source.path("src/document.ts")
                ),
                typescript("import { DOCUMENT } from '@angular/common';\n", source -> source.path("src/standalone.ts"))
        );
    }

    @Test
    void marksRemovedWorkerApis() {
        assertTypeScriptMarker(
                "import { isPlatformWorkerApp } from '@angular/common';\n",
                "isPlatformWorkerApp was removed",
                "src/worker.ts"
        );
    }

    @Test
    void marksAsyncPipeAndControlFlowImports() {
        assertTypeScriptMarker(
                "import { AsyncPipe } from '@angular/common';\n",
                "AsyncPipe now reports",
                "src/async.ts"
        );
        assertTypeScriptMarker(
                "import { NgForOf } from '@angular/common';\n",
                "deprecated in Angular 20",
                "src/list.ts"
        );
    }

    @Test
    void marksStrictTemplateOutletAndOptimizedImageChoices() {
        assertTypeScriptMarker(
                "import { NgTemplateOutlet } from '@angular/common';\n",
                "NgTemplateOutlet context is strictly typed",
                "src/outlet.ts"
        );
        assertTypeScriptMarker(
                "import { NgOptimizedImage } from '@angular/common';\n",
                "loader/CDN",
                "src/image.ts"
        );
    }

    @Test
    void marksLocaleCurrencyAndDatePipeBusinessSemantics() {
        assertTypeScriptMarker(
                "import { CurrencyPipe } from '@angular/common';\n",
                "locale/CLDR, currency, rounding",
                "src/currency.ts"
        );
        assertTypeScriptMarker(
                "import { DatePipe } from '@angular/common';\n",
                "timezone, or week semantics",
                "src/date.ts"
        );
    }

    @Test
    void marksLocationAndTestLocationBehavior() {
        assertTypeScriptMarker(
                "import { LocationStrategy } from '@angular/common';\n",
                "Custom LocationStrategy implementations",
                "src/location.ts"
        );
        assertTypeScriptMarker(
                "import { MockPlatformLocation } from '@angular/common/testing';\n",
                "default test location",
                "src/location.spec.ts"
        );
    }

    @Test
    void marksHttpXsrfInterceptorsAndTestingProviderOrdering() {
        assertTypeScriptMarker(
                "import { HTTP_INTERCEPTORS } from '@angular/common/http';\n",
                "preserve interceptor/XSRF order",
                "src/http.ts"
        );
        assertTypeScriptMarker(
                "import { HttpClientTestingModule } from '@angular/common/http/testing';\n",
                "provideHttpClient before provideHttpClientTesting",
                "src/http.spec.ts"
        );
    }

    @Test
    void marksSuspiciousFormatDateWeekYearAtTheLiteral() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTypeScriptRisks()),
                typescript(
                        "import { formatDate } from '@angular/common';\nconst value = formatDate(now, 'YYYY-MM-dd', 'en');\n",
                        "import { formatDate } from '@angular/common';\nconst value = formatDate(now, /*~~(Angular 20 rejects a week-year Y format without week number w; choose calendar-year y or add the intended week field)~~>*/'YYYY-MM-dd', 'en');\n",
                        source -> source.path("kainonly/litho/src/shared/pipes/date.pipe.ts")
                )
        );
    }

    @Test
    void supportsAliasedFormatDateAndLikelyDatePipeButNotSafeFormats() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTypeScriptRisks()),
                typescript(
                        "import { formatDate as renderDate } from '@angular/common';\nrenderDate(now, 'YYYY', 'en');\n",
                        source -> source.path("src/alias.ts").after(actual -> actual)
                                .afterRecipe(after -> assertTrue(after.printAll().contains("Angular 20 rejects")))
                ),
                typescript(
                        "import { DatePipe } from '@angular/common';\ndatePipe.transform(now, 'YYYY');\n",
                        source -> source.path("src/pipe.ts").after(actual -> actual)
                                .afterRecipe(after -> assertTrue(after.printAll().contains("Angular 20 rejects")))
                ),
                typescript(
                        "import { formatDate } from '@angular/common';\nformatDate(now, 'yyyy-MM-dd', 'en');\nformatDate(now, 'YYYY-ww', 'en');\n",
                        source -> source.path("src/safe.ts")
                )
        );
    }

    @Test
    void marksAngularPackageGroupToolchainRuntimeAndNgccValues() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonJsonRisks()),
                json(
                        """
                        {
                          "engines":{"node":"14.x"},
                          "scripts":{"postinstall":"ngcc"},
                          "dependencies":{"@angular/common":"20.3.26","@angular/core":"12.2.17","rxjs":"6.5.3"},
                          "devDependencies":{"typescript":"4.3.5"}
                        }
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("Angular framework packages must be aligned"));
                                    assertTrue(printed.contains("requires TypeScript"));
                                    assertTrue(printed.contains("supports RxJS"));
                                    assertTrue(printed.contains("requires Node"));
                                    assertTrue(printed.contains("no longer supports ngcc"));
                                })
                )
        );
    }

    @Test
    void marksRangeAndCentralVersionOwnerWithoutChangingThem() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonJsonRisks()),
                json(
                        "{\"dependencies\":{\"@angular/common\":\">=10.0.14 <14\"},\"pnpm\":{\"overrides\":{\"@angular/common\":\"10.0.14\"}}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("range, protocol, variable, unlisted"));
                                    assertTrue(printed.contains("Central package-manager version ownership"));
                                })
                )
        );
    }

    @Test
    void scopesPackageToolchainMarkersToAngularCommonConsumers() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonJsonRisks()),
                json(
                        "{\"engines\":{\"node\":\"14.x\"},\"devDependencies\":{\"typescript\":\"4.3.5\"}}",
                        source -> source.path("services/api/package.json")
                )
        );
    }

    @Test
    void marksWorkspaceBuildersSsrAndCompilerOptions() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonJsonRisks()),
                json(
                        "{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@nrwl/angular:webpack-browser\"},\"server\":{},\"prerender\":{}}}}}",
                        source -> source.path("angular.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertTrue(after.printAll().contains("Custom builder detected"));
                                    assertTrue(after.printAll().contains("SSR/prerender"));
                                })
                ),
                json(
                        "{\"angularCompilerOptions\":{\"enableIvy\":false,\"strictTemplates\":false}}",
                        source -> source.path("tsconfig.app.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertTrue(after.printAll().contains("enableIvy false"));
                                    assertTrue(after.printAll().contains("Enable strictTemplates"));
                                })
                ),
                json("{\"compilerOptions\":{\"target\":\"es2022\"}}",
                        source -> source.path("services/api/tsconfig.json"))
        );
    }

    @Test
    void recommendedRecipeCombinesAutoMigrationAndRiskMarkers() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build();
        rewriteRun(
                spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.angular.MigrateAngularCommonTo20_3_26")),
                json(
                        "{\"dependencies\":{\"@angular/common\":\"12.2.17\"}}",
                        "{\"dependencies\":{\"@angular/common\":\"20.3.26\"}}",
                        source -> source.path("package.json")
                ),
                typescript(
                        "import { DOCUMENT } from '@angular/common';\n",
                        "import { DOCUMENT } from '@angular/core';\n",
                        source -> source.path("src/document.ts")
                ),
                json(
                        "{\"angularCompilerOptions\":{\"enableIvy\":true,\"strictTemplates\":true}}",
                        "{\"angularCompilerOptions\":{\"strictTemplates\":true}}",
                        source -> source.path("tsconfig.app.json")
                )
        );
    }

    private void assertTypeScriptMarker(String before, String message, String path) {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTypeScriptRisks()),
                typescript(before, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> assertTrue(after.printAll().contains(message))))
        );
    }
}
