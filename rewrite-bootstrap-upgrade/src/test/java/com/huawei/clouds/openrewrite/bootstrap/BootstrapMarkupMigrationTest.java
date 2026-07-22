package com.huawei.clouds.openrewrite.bootstrap;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;

class BootstrapMarkupMigrationTest implements RewriteTest {
    @Test
    void migratesOwnedModalAndCollapseDataAttributes() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapMarkup()),
                text("<link href=\"bootstrap.css\"><button class=\"btn\" data-toggle=\"modal\" data-target=\"#dialog\">Open</button><button class=\"navbar-toggler\" data-toggle=\"collapse\" data-target=\"#nav\" data-parent=\"#root\"></button>",
                        "<link href=\"bootstrap.css\"><button class=\"btn\" data-bs-toggle=\"modal\" data-bs-target=\"#dialog\">Open</button><button class=\"navbar-toggler\" data-bs-toggle=\"collapse\" data-bs-target=\"#nav\" data-bs-parent=\"#root\"></button>",
                        source -> source.path("src/index.html")));
    }

    @Test
    void migratesDismissCarouselAndScrollSpyAttributes() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapMarkup()),
                text("<div class='modal'><button data-dismiss='modal'>x</button></div><div class='carousel' data-ride='carousel' data-interval='3000'></div><body data-spy='scroll' data-target='#nav' data-offset='20'></body>",
                        "<div class='modal'><button data-bs-dismiss='modal'>x</button></div><div class='carousel' data-bs-ride='carousel' data-bs-interval='3000'></div><body data-bs-spy='scroll' data-bs-target='#nav' data-bs-offset='20'></body>",
                        source -> source.path("src/widgets.html")));
    }

    @Test
    void migratesLogicalDirectionSpacingAndTypographyUtilities() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapMarkup()),
                text("<div class=\"container float-left float-md-right text-left text-lg-right border-left rounded-right ml-3 mr-md-auto pl-2 pr-xl-5 text-monospace font-italic font-weight-bold\"></div>",
                        "<div class=\"container float-start float-md-end text-start text-lg-end border-start rounded-end ms-3 me-md-auto ps-2 pe-xl-5 font-monospace fst-italic fw-bold\"></div>",
                        source -> source.path("src/utilities.html")));
    }

    @Test
    void migratesScreenReaderGutterFormsBadgeAndRatioHelpers() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapMarkup()),
                text("<div class='row no-gutters'><label class='sr-only sr-only-focusable'>Name</label><select class='custom-select'></select><input class='custom-range'><span class='badge badge-pill'></span><div class='embed-responsive embed-responsive-16by9'><iframe class='embed-responsive-item'></iframe></div></div>",
                        "<div class='row g-0'><label class='visually-hidden visually-hidden-focusable'>Name</label><select class='form-select'></select><input class='form-range'><span class='badge rounded-pill'></span><div class='ratio ratio-16x9'><iframe class=''></iframe></div></div>",
                        source -> source.path("src/helpers.html")));
    }

    @Test
    void leavesCommentsDynamicBindingsAndApplicationDataAttributesAlone() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapMarkup()),
                text("<link href='bootstrap.css'><!-- <button class='ml-3' data-toggle='modal'>old</button> --><div :class=\"layoutClass\" data-toggle='application-menu'></div><div data-target='#application'></div>",
                        source -> source.path("src/App.vue")));
    }

    @Test
    void leavesBootstrapLikeClassesWithoutFileOwnershipEvidence() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapMarkup()),
                text("<div class='ml-3 text-left sr-only'>Application-owned classes</div>",
                        source -> source.path("src/partial.html")));
    }

    @Test
    void leavesCssAndJavaScriptFilesForAstOrReviewRecipes() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapMarkup()),
                text(".ml-3 { margin-left: 1rem; }", source -> source.path("src/theme.css")),
                text("const selector = '[data-toggle=modal]'", source -> source.path("src/app.js")));
    }

    @Test
    void excludesInstalledGeneratedAndBuiltMarkup() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapMarkup()),
                text("<div class='container ml-3'></div>", source -> source.path("node_modules/pkg/index.html")),
                text("<div class='container ml-3'></div>", source -> source.path("vendor/theme/index.html")),
                text("<div class='container ml-3'></div>", source -> source.path("dist/index.html")),
                text("<div class='container ml-3'></div>", source -> source.path("generated/index.html")));
    }

    @Test
    void markupMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapMarkup())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("<button class='btn float-right mr-2' data-toggle='modal' data-target='#dialog'></button>",
                        "<button class='btn float-end me-2' data-bs-toggle='modal' data-bs-target='#dialog'></button>",
                        source -> source.path("src/idempotent.html")));
    }
}
