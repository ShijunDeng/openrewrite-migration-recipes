package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class AngularCommonSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void movesStandaloneDocumentFollowingOfficialAngularMigration() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicAngularCommonSource()),
                typescript(
                        "import { DOCUMENT } from '@angular/common';\nconst token = DOCUMENT;\n",
                        "import { DOCUMENT } from '@angular/core';\nconst token = DOCUMENT;\n",
                        source -> source.path("packages/core/test/application_ref_spec.ts")
                )
        );
    }

    @Test
    void preservesDoubleQuotesAndDocumentAlias() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicAngularCommonSource()),
                typescript(
                        "import { DOCUMENT as APP_DOCUMENT } from \"@angular/common\";\n",
                        "import { DOCUMENT as APP_DOCUMENT } from \"@angular/core\";\n",
                        source -> source.path("src/document.ts")
                )
        );
    }

    @Test
    void movesStandaloneXhrFactoryFollowingAngularCommonExportMove() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicAngularCommonSource()),
                typescript(
                        "import { XhrFactory } from '@angular/common/http';\n",
                        "import { XhrFactory } from '@angular/common';\n",
                        source -> source.path("src/http-backend.ts")
                )
        );
    }

    @Test
    void preservesDoubleQuotesAndXhrFactoryAlias() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicAngularCommonSource()),
                typescript(
                        "import { XhrFactory as BackendFactory } from \"@angular/common/http\";\n",
                        "import { XhrFactory as BackendFactory } from \"@angular/common\";\n",
                        source -> source.path("src/http-backend.ts")
                )
        );
    }

    @Test
    void doesNotPartiallyRewriteMixedImports() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicAngularCommonSource()),
                typescript(
                        "import { CommonModule, DOCUMENT } from '@angular/common';\n",
                        source -> source.path("src/document.ts")
                ),
                typescript(
                        "import { HttpRequest, XhrFactory } from '@angular/common/http';\n",
                        source -> source.path("NativeScript/nativescript-angular/ns-http-backend.ts")
                )
        );
    }

    @Test
    void doesNotRewriteSameNamesFromUnrelatedPackagesOrDefaults() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicAngularCommonSource()),
                typescript("import { DOCUMENT } from './tokens';\n", source -> source.path("src/local.ts")),
                typescript("import DOCUMENT from '@angular/common';\n", source -> source.path("src/default.ts"))
        );
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicAngularCommonSource())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import { DOCUMENT } from '@angular/common';\nimport { XhrFactory } from '@angular/common/http';\n",
                        "import { DOCUMENT } from '@angular/core';\nimport { XhrFactory } from '@angular/common';\n",
                        source -> source.path("src/tokens.ts")
                )
        );
    }

    @Test
    void removesOnlyEnableIvyTrueFromAngularCompilerOptions() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantEnableIvy()),
                json(
                        """
                        {"compilerOptions":{"target":"es2022"},"angularCompilerOptions":{"enableIvy":true,"strictTemplates":true}}
                        """,
                        """
                        {"compilerOptions":{"target":"es2022"},"angularCompilerOptions":{"strictTemplates":true}}
                        """,
                        source -> source.path("tsconfig.app.json")
                )
        );
    }

    @Test
    void keepsEnableIvyFalseNestedAndNonTsconfigValues() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantEnableIvy()),
                json("{\"angularCompilerOptions\":{\"enableIvy\":false}}",
                        source -> source.path("tsconfig.lib.json")),
                json("{\"metadata\":{\"angularCompilerOptions\":{\"enableIvy\":true}}}",
                        source -> source.path("tsconfig.app.json")),
                json("{\"angularCompilerOptions\":{\"enableIvy\":true}}",
                        source -> source.path("angular-options.json"))
        );
    }

    @Test
    void configurationMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new RemoveRedundantEnableIvy())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json(
                        "{\"angularCompilerOptions\":{\"enableIvy\":true,\"strictTemplates\":true}}",
                        "{\"angularCompilerOptions\":{\"strictTemplates\":true}}",
                        source -> source.path("tsconfig.json")
                )
        );
    }
}
