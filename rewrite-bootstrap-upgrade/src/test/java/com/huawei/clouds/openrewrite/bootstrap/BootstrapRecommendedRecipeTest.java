package com.huawei.clouds.openrewrite.bootstrap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class BootstrapRecommendedRecipeTest implements RewriteTest {
    private static final String MIGRATION = "com.huawei.clouds.openrewrite.bootstrap.MigrateBootstrapTo5_3_6";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(BootstrapDependencyTest.environment().activateRecipes(MIGRATION));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesOfficialBootstrap4DependencyContractsAtPinnedTag() {
        // twbs/bootstrap v4.6.2 @ e5643aaa89eb67327a5b4abe7db976f0ea276b70
        // package.json peers on jquery and popper.js v1.
        rewriteRun(
                json("{\"dependencies\":{\"bootstrap\":\"4.6.2\",\"jquery\":\"3.5.1\",\"popper.js\":\"^1.16.1\"},\"devDependencies\":{\"node-sass\":\"6.0.1\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"bootstrap\":\"5.3.6\""));
                                    assertTrue(printed.contains("dropped jQuery"));
                                    assertTrue(printed.contains("Popper 2"));
                                    assertTrue(printed.contains("Dart Sass"));
                                })),
                typescript("import $ from 'jquery';\nimport 'bootstrap';\n$('#dialog').modal('show');\n",
                        source -> source.path("src/bootstrap.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("dropped jQuery plugins")))));
    }

    @Test
    void migratesRuangAdminMarkupAtPinnedCommit() {
        // indrijunanda/RuangAdmin @ 0ab27d0024263bfa0d334850dd708ebb669d222e
        // 404.html: Bootstrap stylesheet, collapse/dropdown triggers, mr/ml and float-right utilities.
        rewriteRun(
                json("{\"dependencies\":{\"bootstrap\":\"^4.6.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                text("<link href=\"vendor/bootstrap/css/bootstrap.min.css\" rel=\"stylesheet\"><button class=\"navbar-toggler mr-3\" data-toggle=\"collapse\" data-target=\"#sidebar\"></button><ul class=\"navbar-nav ml-auto\"><li class=\"small float-right\">50%</li></ul>",
                        source -> source.path("404.html").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("data-bs-toggle=\"collapse\""));
                                    assertTrue(printed.contains("data-bs-target=\"#sidebar\""));
                                    assertTrue(printed.contains("navbar-toggler me-3"));
                                    assertTrue(printed.contains("navbar-nav ms-auto"));
                                    assertTrue(printed.contains("small float-end"));
                                })));
    }

    @Test
    void appliesAndMarksOfficialMigrationGuideExamplesAtPinnedTarget() {
        // twbs/bootstrap v5.3.6 @ f849680d16a9695c9a6c9c062d6cff55ddcf071e
        // site/src/content/docs/migration.mdx: data prefix, utilities, forms, whiteList, and Popper.
        rewriteRun(
                json("{\"dependencies\":{\"bootstrap\":\"~3.4.1\",\"jquery\":\"3.7.1\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                text("<button class='btn float-left ml-2 sr-only' data-toggle='modal' data-target='#dialog'>Open</button><div class='custom-control custom-checkbox'><input></div>",
                        source -> source.path("src/guide.html").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("btn float-start ms-2 visually-hidden"));
                                    assertTrue(printed.contains("data-bs-toggle='modal'"));
                                    assertTrue(printed.contains("structurally redesigned forms"));
                                })),
                typescript("import { Modal, Tooltip } from 'bootstrap';\nModal._getInstance(dialog);\nnew Tooltip(button, { whiteList: rules, html: true });\n",
                        source -> source.path("src/guide.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Modal.getInstance(dialog)"));
                                    assertTrue(printed.contains("allowList: rules"));
                                    assertTrue(printed.contains("uses Popper 2"));
                                })));
    }

    @Test
    void targetManifestAndModernPublicUsageAreNoOp() {
        // twbs/bootstrap v5.3.6 package and modern docs @ f849680d16a9695c9a6c9c062d6cff55ddcf071e
        rewriteRun(
                json("{\"dependencies\":{\"bootstrap\":\"5.3.6\"},\"browserslist\":[\"defaults and supports es6-module\"]}",
                        source -> source.path("package.json")),
                text("<button class='btn btn-primary float-end me-2' data-bs-toggle='modal' data-bs-target='#dialog'>Open</button>",
                        source -> source.path("src/modern.html")),
                typescript("import { Modal } from 'bootstrap';\nconst modal = new Modal('#dialog');\nmodal.show();\n",
                        source -> source.path("src/modern.ts")));
    }

    @Test
    void followsPinnedOpenRewriteImportShapesAndIsIdempotent() {
        // openrewrite/rewrite-javascript @ 9e3b820e6a44808b095bb7e3aab670fd67de99a5
        // rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"bootstrap\":\"5.2.3\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript("import { Modal as BsModal , Tooltip as BsTooltip } from 'bootstrap';\nBsModal._getInstance(dialog);\nnew BsTooltip(button, { whiteList: rules });\n",
                        source -> source.path("src/imports.ts").after(actual -> actual)));
        Recipe recipe = BootstrapDependencyTest.environment().activateRecipes(MIGRATION);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(BootstrapDependencyTest.environment().listRecipes().stream()
                .anyMatch(candidate -> MIGRATION.equals(candidate.getName())));
    }
}
