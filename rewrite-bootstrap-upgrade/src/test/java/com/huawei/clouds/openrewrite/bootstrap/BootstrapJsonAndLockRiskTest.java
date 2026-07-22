package com.huawei.clouds.openrewrite.bootstrap;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class BootstrapJsonAndLockRiskTest implements RewriteTest {
    @Test
    void marksSkippedComplexBootstrapConstraint() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJsonRisks()),
                json("{\"dependencies\":{\"bootstrap\":\">=4.6.2 <6\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("Strict migration skipped")))));
    }

    @Test
    void marksJqueryPopperAndSassToolchain() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJsonRisks()),
                json("{\"dependencies\":{\"bootstrap\":\"5.3.6\",\"jquery\":\"3.7.1\",\"popper.js\":\"1.16.1\",\"@popperjs/core\":\"2.9.0\"},\"devDependencies\":{\"node-sass\":\"7.0.1\",\"sass-loader\":\"10.4.1\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("dropped jQuery"));
                                    assertTrue(printed.contains("Popper 2"));
                                    assertTrue(printed.contains("@popperjs/core ^2.11.8"));
                                    assertTrue(printed.contains("Dart Sass"));
                                })));
    }

    @Test
    void marksFrameworkWrappersWithoutGuessingTheirVersions() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJsonRisks()),
                json("{\"dependencies\":{\"bootstrap\":\"^5.3.6\",\"react-bootstrap\":\"1.6.8\",\"reactstrap\":\"8.10.1\",\"ngx-bootstrap\":\"6.2.0\",\"@ng-bootstrap/ng-bootstrap\":\"9.1.3\",\"bootstrap-vue\":\"2.23.1\",\"bootstrap-vue-next\":\"0.14.10\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("owns Bootstrap markup")))));
    }

    @Test
    void marksLegacyBrowserPolicyOnlyWhenBootstrapIsPresent() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJsonRisks()),
                json("{\"browserslist\":[\"defaults\",\"ie 11\"],\"dependencies\":{\"bootstrap\":\"5.3.6\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("dropped Internet Explorer")))),
                json("{\"browserslist\":[\"ie 11\"],\"dependencies\":{\"react\":\"19.0.0\"}}",
                        source -> source.path("apps/other/package.json")));
    }

    @Test
    void marksNpmLockV1AndV3WithoutChangingIntegrity() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJsonRisks()),
                json("{\"lockfileVersion\":1,\"dependencies\":{\"bootstrap\":{\"version\":\"4.6.2\",\"resolved\":\"https://registry.example/bootstrap.tgz\",\"integrity\":\"sha512-old\"}}}",
                        source -> source.path("package-lock.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("Regenerate this npm lockfile"));
                                    assertTrue(printed.contains("sha512-old"));
                                    assertTrue(printed.contains("4.6.2"));
                                })),
                json("{\"lockfileVersion\":3,\"packages\":{\"\":{\"dependencies\":{\"bootstrap\":\"^4.6.2\"}},\"node_modules/bootstrap\":{\"version\":\"4.6.2\",\"integrity\":\"sha512-pinned\"}}}",
                        source -> source.path("apps/ui/package-lock.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("sha512-pinned")))));
    }

    @Test
    void marksYarnAndPnpmExactPackageKeys() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapTextLockRisks()),
                text("bootstrap@^4.6.2:\n  version \"4.6.2\"\n  integrity sha512-old\nbootstrap-vue@2.23.1:\n  version \"2.23.1\"\n",
                        source -> source.path("yarn.lock").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("Regenerate this lockfile"));
                                    assertTrue(printed.contains("bootstrap-vue@2.23.1"));
                                    assertTrue(printed.contains("sha512-old"));
                                })),
                text("lockfileVersion: '9.0'\npackages:\n  'bootstrap@5.2.3':\n    resolution: {integrity: sha512-old}\n",
                        source -> source.path("pnpm-lock.yaml").after(actual -> actual)
                                .afterRecipe(file -> assertTrue(file.printAll().contains("Regenerate this lockfile")))));
    }

    @Test
    void marksPhysicalJsonBuildAliasAndIgnoresPublicRoot() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJsonRisks()),
                json("{\"alias\":{\"bootstrap-js\":\"./node_modules/bootstrap/dist/js/bootstrap.bundle.js\",\"bootstrap\":\"bootstrap\",\"other\":\"@example/bootstrap/dist/js/app.js\"}}",
                        source -> source.path("webpack.config.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("physical distribution path"));
                                    assertTrue(printed.contains("@example/bootstrap/dist/js/app.js"));
                                })));
    }

    @Test
    void targetOnlyManifestHasNoRiskMarkers() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJsonRisks()),
                json("{\"browserslist\":[\"defaults and supports es6-module\"],\"dependencies\":{\"bootstrap\":\"5.3.6\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void excludesGeneratedInstalledAndBuiltLocksAndConfig() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJsonRisks()),
                json("{\"dependencies\":{\"bootstrap\":{\"version\":\"4.6.2\"}}}",
                        source -> source.path("node_modules/pkg/package-lock.json")),
                json("{\"alias\":\"bootstrap/dist/js/bootstrap.js\"}",
                        source -> source.path("dist/webpack.config.json")));
        rewriteRun(spec -> spec.recipe(new FindBootstrapTextLockRisks()),
                text("bootstrap@4.6.2:\n", source -> source.path("build/yarn.lock")));
    }

    @Test
    void lockRecipesIgnoreSimilarScopedAndWrapperPackages() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapTextLockRisks()),
                text("react-bootstrap@2.10.0:\n@scope/bootstrap@4.6.2:\nbootstrap-vue@2.23.1:\n",
                        source -> source.path("yarn.lock")));
        rewriteRun(spec -> spec.recipe(new FindBootstrapJsonRisks()),
                json("{\"dependencies\":{\"react-bootstrap\":{\"version\":\"1.6.8\"}}}",
                        source -> source.path("package-lock.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("Regenerate this npm lockfile")))));
    }
}
