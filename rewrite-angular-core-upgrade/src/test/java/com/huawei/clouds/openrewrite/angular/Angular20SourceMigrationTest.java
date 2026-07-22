package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;

class Angular20SourceMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDeterministicAngular20Source());
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesBitpayStyleTestBedGetCall() {
        rewriteRun(typescript(
                """
                import { TestBed } from '@angular/core/testing';
                const service = TestBed.get(ProfileProvider);
                """,
                """
                import { TestBed } from '@angular/core/testing';
                const service = TestBed.inject(ProfileProvider);
                """,
                spec -> spec.path("src/app/app.component.spec.ts")
        ));
    }

    @Test
    void migratesAliasedTestBedAndPropertyReference() {
        rewriteRun(typescript(
                """
                import { TestBed as AngularTestBed } from '@angular/core/testing';
                export const GET = AngularTestBed.get;
                const service = AngularTestBed.get(Token, null);
                """,
                """
                import { TestBed as AngularTestBed } from '@angular/core/testing';
                export const GET = AngularTestBed.inject;
                const service = AngularTestBed.inject(Token, null);
                """,
                spec -> spec.path("src/service.spec.ts")
        ));
    }

    @Test
    void doesNotRenameUnrelatedGetMethod() {
        rewriteRun(typescript(
                """
                class TestBed { static get(token: unknown) { return token; } }
                const service = TestBed.get(Token);
                """,
                spec -> spec.path("src/local.ts")
        ));
    }

    @Test
    void conservativelyLeavesShadowedImportedName() {
        rewriteRun(typescript(
                """
                import { TestBed } from '@angular/core/testing';
                function lookup(TestBed: LocalRegistry) {
                  return TestBed.get(Token);
                }
                """,
                spec -> spec.path("src/shadowed.spec.ts")
        ));
    }

    @Test
    void movesStandaloneDocumentImportToCore() {
        rewriteRun(typescript(
                """
                import { DOCUMENT } from '@angular/common';
                console.log(DOCUMENT);
                """,
                """
                import { DOCUMENT } from '@angular/core';
                console.log(DOCUMENT);
                """,
                spec -> spec.path("src/document.ts")
        ));
    }

    @Test
    void movesAliasedStandaloneDocumentImportAndPreservesQuotes() {
        rewriteRun(typescript(
                """
                import { DOCUMENT as MY_DOC } from "@angular/common";
                console.log(MY_DOC);
                """,
                """
                import { DOCUMENT as MY_DOC } from "@angular/core";
                console.log(MY_DOC);
                """,
                spec -> spec.path("src/document.ts")
        ));
    }

    @Test
    void mixedCommonImportNeedsMarkerRecipeAndIsNotAutoChanged() {
        rewriteRun(typescript(
                """
                import { CommonModule, DOCUMENT } from '@angular/common';
                console.log(CommonModule, DOCUMENT);
                """,
                spec -> spec.path("src/document.ts")
        ));
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        """
                        import { TestBed } from '@angular/core/testing';
                        import { DOCUMENT } from '@angular/common';
                        const value = TestBed.get(DOCUMENT);
                        """,
                        """
                        import { TestBed } from '@angular/core/testing';
                        import { DOCUMENT } from '@angular/core';
                        const value = TestBed.inject(DOCUMENT);
                        """,
                        spec -> spec.path("src/document.spec.ts")
                )
        );
    }
}
