package com.huawei.clouds.openrewrite.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class BootstrapTemplateStyleRiskTest implements RewriteTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "<div class='custom-control custom-checkbox'><input></div>",
            "<div class='input-group-append'><button></button></div>",
            "<div class='glyphicon glyphicon-user'></div>",
            "<div class='panel panel-default'></div>",
            "<span class='badge badge-primary'></span>",
            "<button class='close'>&times;</button>",
            "<table><thead class='thead-dark'></thead></table>",
            "<div class='text-hide'></div>",
            "<div class='rounded-lg'></div>",
            "<div class='ml-n3'></div>"
    })
    void marksStructuralAndRemovedMarkup(String markup) {
        rewriteRun(spec -> spec.recipe(new FindBootstrapTemplateStyleRisks()),
                text(markup, source -> source.path("src/fragment.html").after(actual -> actual)
                        .afterRecipe(file -> assertTrue(file.printAll().contains("~~")))));
    }

    @Test
    void marksUnconvertedDataAttributesAndProgressAccessibility() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapTemplateStyleRisks()),
                text("<button data-toggle='application'>Open</button><div class='progress'><div class='progress-bar' role='progressbar' aria-valuenow='25'></div></div>",
                        source -> source.path("src/widgets.html").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("Bootstrap 3/4 data attribute"));
                                    assertTrue(printed.contains("outer .progress element"));
                                })));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "@import 'bootstrap/scss/variables';\n",
            ".theme { color: theme-color-level('primary', 2); }\n",
            "$enable-prefers-reduced-motion-media-query: false;\n",
            ".custom-control { color: red; }\n",
            ".ml-3 > .close { opacity: 1; }\n"
    })
    void marksSassAndCssContracts(String style) {
        rewriteRun(spec -> spec.recipe(new FindBootstrapTemplateStyleRisks()),
                text(style, source -> source.path("src/theme.scss").after(actual -> actual)
                        .afterRecipe(file -> assertTrue(file.printAll().contains("~~")))));
    }

    @Test
    void marksCdnAndCopiedAssets() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapTemplateStyleRisks()),
                text("<link href='https://cdn.jsdelivr.net/npm/bootstrap@4.6.2/dist/css/bootstrap.min.css'><script src='vendor/bootstrap/dist/js/bootstrap.js'></script>",
                        source -> source.path("src/index.html").after(actual -> actual)
                                .afterRecipe(file -> {
                                    assertTrue(file.printAll().contains("SRI"));
                                    assertTrue(file.printAll().contains("copied or install-layout"));
                                })));
    }

    @Test
    void ignoresCommentsAndModernMarkupAndStyle() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapTemplateStyleRisks()),
                text("<!-- <button data-toggle='modal' class='close'>old</button> --><button class='btn-close' data-bs-dismiss='modal'></button>",
                        source -> source.path("src/modern.html")),
                text("/* .custom-control { old: true; } */\n.form-check { color: var(--bs-body-color); }\n",
                        source -> source.path("src/modern.scss")));
    }

    @Test
    void excludesVendorGeneratedAndBuiltText() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapTemplateStyleRisks()),
                text("<button data-toggle='modal' class='close'>x</button>",
                        source -> source.path("vendor/bootstrap/example.html")),
                text(".custom-control { color:red }", source -> source.path("dist/theme.css")),
                text("$enable-pointer-cursor-for-buttons: true;", source -> source.path("generated/theme.scss")));
    }

    @Test
    void templateMarkerRecipeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapTemplateStyleRisks()).cycles(2),
                text("<button class='close'>x</button>", source -> source.path("src/idempotent.html")
                        .after(actual -> actual).afterRecipe(file -> {
                            assertTrue(file.printAll().contains("removed Bootstrap 4 class"));
                            assertFalse(file.printAll().contains("~~(~~("));
                        })));
    }
}
