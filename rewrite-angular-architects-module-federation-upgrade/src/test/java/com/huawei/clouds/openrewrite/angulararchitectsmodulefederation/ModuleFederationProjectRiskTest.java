package com.huawei.clouds.openrewrite.angulararchitectsmodulefederation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class ModuleFederationProjectRiskTest implements RewriteTest {
    @ParameterizedTest(name = "marks package graph risk {0}")
    @MethodSource("packageRisks")
    void marksExactPackageGraphRisks(String label, String entry, String message) {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json("{\"dependencies\":{\"@angular-architects/module-federation\":\"20.0.0\"," + entry + "}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> packageRisks() {
        return Stream.of(
                Arguments.of("angular-core", "\"@angular/core\":\"^15.2.10\"", "belongs to the Angular 20 line"),
                Arguments.of("angular-complex-range", "\"@angular/core\":\"^20.0.0 || ^21.0.0\"", "belongs to the Angular 20 line"),
                Arguments.of("angular-cli", "\"@angular/cli\":\"~16.2.0\"", "belongs to the Angular 20 line"),
                Arguments.of("runtime", "\"@angular-architects/module-federation-runtime\":\"^16.0.4\"", "explicitly owned runtime must align"),
                Arguments.of("build-plus", "\"ngx-build-plus\":\"^13.0.1\"", "requires ngx-build-plus ^20"),
                Arguments.of("enhanced", "\"@module-federation/enhanced\":\"^0.9.0\"", "enhanced/runtime-core peers"),
                Arguments.of("runtime-core", "\"@module-federation/runtime-core\":\"^0.6.21\"", "enhanced/runtime-core peers"),
                Arguments.of("nx-old", "\"@nrwl/angular\":\"15.5.2\"", "Nx 15/16 builder names"),
                Arguments.of("nx-current-owner", "\"@nx/angular\":\"20.0.0\"", "Nx 15/16 builder names"),
                Arguments.of("rxjs", "\"rxjs\":\"~7.8.0\"", "supported matrix"),
                Arguments.of("typescript", "\"typescript\":\"~5.1.6\"", "supported matrix"),
                Arguments.of("zone", "\"zone.js\":\"~0.13.3\"", "supported matrix")
        );
    }

    @ParameterizedTest(name = "marks ownership risk {0}")
    @MethodSource("ownershipRisks")
    void marksVersionAndConfigurationOwnershipRisks(String source, String message) {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json(source, input -> input.path("package.json").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> ownershipRisks() {
        return Stream.of(
                Arguments.of("{\"dependencies\":{\"@angular-architects/module-federation\":\">=15.0.3\"}}", "outside workbook versions"),
                Arguments.of("{\"dependencies\":{\"@angular-architects/module-federation\":\"workspace:^15.0.3\"}}", "dynamic/range/protocol/workspace"),
                Arguments.of("{\"dependencies\":{\"@angular-architects/module-federation\":\"20.0.0\"},\"overrides\":{\"@angular-architects/module-federation\":\"16.0.4\"}}", "override/resolution independently owns"),
                Arguments.of("{\"dependencies\":{\"@angular-architects/module-federation\":\"20.0.0\"},\"pnpm\":{\"overrides\":{\"@angular-architects/module-federation\":\"16.0.4\"}}}", "override/resolution independently owns"),
                Arguments.of("{\"peerDependencies\":{\"@angular-architects/module-federation\":\"20.0.0\"}}", "published peer contract"),
                Arguments.of("{\"optionalDependencies\":{\"@angular-architects/module-federation\":\"20.0.0\"}}", "optional install/runtime path")
        );
    }

    @ParameterizedTest(name = "marks explicit schematic stack {0}")
    @MethodSource("stackOwners")
    void marksExactScriptStackOwner(String label, String command, String message) {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json("{\"scripts\":{\"mf:init\":\"" + ModuleFederationSupport.escape(command) +
                     "\"},\"devDependencies\":{\"@angular-architects/module-federation\":\"20.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> stackOwners() {
        return Stream.of(
                Arguments.of("classic", "ng add @angular-architects/module-federation --stack module-federation-webpack", "explicitly owns the classic-webpack stack"),
                Arguments.of("rspack", "ng g @angular-architects/module-federation:init-rspack", "experimental rspack/rsbuild stack"),
                Arguments.of("native", "ng add @angular-architects/module-federation --stack native-federation-esbuild", "different package/runtime architecture"),
                Arguments.of("dynamic", "ng add @angular-architects/module-federation --project $PROJECT", "too dynamic/chained for AUTO")
        );
    }

    @ParameterizedTest(name = "marks Angular configuration risk {0}")
    @MethodSource("angularConfigRisks")
    void marksBuilderAndDeploymentConfiguration(String label, String member, String message) {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json("{\"module-federation\":true," + member + "}",
                        source -> source.path("angular.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> angularConfigRisks() {
        return Stream.of(
                Arguments.of("application", "\"builder\":\"@angular-devkit/build-angular:application\"", "cannot consume the existing webpack config"),
                Arguments.of("build-plus", "\"builder\":\"ngx-build-plus:browser\"", "Review this build/serve builder"),
                Arguments.of("nrwl", "\"builder\":\"@nrwl/angular:webpack-browser\"", "Review this build/serve builder"),
                Arguments.of("extra-config", "\"extraWebpackConfig\":\"webpack.config.js\"", "classic webpack target option"),
                Arguments.of("custom-config", "\"customWebpackConfig\":{\"path\":\"webpack.config.js\"}", "classic webpack target option"),
                Arguments.of("public-host", "\"publicHost\":\"http://localhost:4200\"", "classic webpack target option"),
                Arguments.of("browser-target", "\"browserTarget\":\"shell:build\"", "classic webpack target option"),
                Arguments.of("assets", "\"assets\":[\"src/assets\"]", "assets conventions"),
                Arguments.of("output", "\"outputPath\":\"dist/shell\"", "assets conventions"),
                Arguments.of("browser", "\"browser\":\"src/main.ts\"", "assets conventions"),
                Arguments.of("remote", "\"remoteEntry\":\"https://mfe/remoteEntry.js\"", "cross-deployment contract")
        );
    }

    @Test
    void marksOldTsTargetWhenConfigurationOwnsFederation() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json("{\"compilerOptions\":{\"target\":\"es2017\"},\"paths\":{\"module-federation\":[\"remoteEntry\"]}}",
                        source -> source.path("tsconfig.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "at least ES2020"))));
    }

    @Test
    void angular21OwnerIsNotMistakenForTheAngular20Line() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json("{\"dependencies\":{\"@angular-architects/module-federation\":\"20.0.0\",\"@angular/core\":\"^21.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "belongs to the Angular 20 line"))));
    }

    @Test
    void alignedAngular20AndEsNextTargetAreNotMarkedAsOutdated() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json("{\"dependencies\":{\"@angular-architects/module-federation\":\"20.0.0\",\"@angular/core\":\"^20.0.0\"}}",
                        source -> source.path("package.json")),
                json("{\"compilerOptions\":{\"target\":\"ESNext\"},\"paths\":{\"module-federation\":[\"remoteEntry\"]}}",
                        source -> source.path("tsconfig.json")));
    }

    @Test
    void multiProjectWorkspaceMarksOnlyTheFederationProjectBranch() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json("""
                        {"projects":{"shell":{"architect":{"build":{"builder":"ngx-build-plus:browser","options":{"assets":["src/assets"],"extraWebpackConfig":"webpack.config.js"}}}},"admin":{"architect":{"build":{"builder":"@angular-devkit/build-angular:application","options":{"assets":["src/assets"]}}}}}}
                        """, source -> source.path("angular.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "Review this build/serve builder");
                            assertContains(printed, "classic webpack target option");
                            assertFalse(printed.contains("cannot consume the existing webpack config"), printed);
                        })));
    }

    @Test
    void similarlyPrefixedSiblingPackageDoesNotCreateProjectOwnership() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json("{\"dependencies\":{\"@angular-architects/module-federation-runtime\":\"16.0.4\",\"@angular/core\":\"15.0.0\"}}",
                        source -> source.path("package.json").afterRecipe(after ->
                                assertFalse(after.printAll().contains("belongs to the Angular 20 line"), after.printAll()))));
    }

    @Test
    void marksRealDontCodeNx15OwnershipAtFixedCommit() {
        // dont-code/monorepo@5d4d4ab2e889b274446f8c3be22155a51315d8fa, apps/preview-ui/package.json
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json("""
                        {"dependencies":{"@angular-architects/module-federation":"^15.0.3","@angular-architects/module-federation-runtime":"^15.0.3","@angular/core":"^15.1.2"},"devDependencies":{"@nrwl/angular":"15.5.2","nx":"15.5.2","typescript":"^4.9.4"}}
                        """, source -> source.path("apps/preview-ui/package.json").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "Nx 15/16 builder names");
                            assertContains(after.printAll(), "explicitly owned runtime must align");
                        })));
    }

    @Test
    void projectFinderSkipsUnrelatedAndGeneratedFiles() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationProjectRisks()),
                json("{\"dependencies\":{\"@angular/core\":\"15.0.0\"}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"@angular-architects/module-federation\":\">=15\",\"@angular/core\":\"15\"}}", source -> source.path("dist/package.json")),
                json("{\"builder\":\"@angular-devkit/build-angular:application\"}", source -> source.path("angular.json")));
    }

    @Test
    void marksNpmAndYarnLockfiles() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationResourceRisks()),
                json("{\"packages\":{\"node_modules/@angular-architects/module-federation\":{\"version\":\"16.0.4\"}}}",
                        source -> source.path("package-lock.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Regenerate exactly this selected lockfile"))),
                text("@angular-architects/module-federation@^16.0.4:\n  version \"16.0.4\"\n",
                        source -> source.path("yarn.lock").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "no stale 15/16 runtime"))));
    }

    @Test
    void marksPnpmLockAndDeploymentYaml() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationResourceRisks()),
                yaml("lockfileVersion: 9\npackages:\n  '@angular-architects/module-federation@16.0.4': {}\n",
                        source -> source.path("pnpm-lock.yaml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "enhanced/runtime-core");
                            assertTrue(printed.indexOf("~~") > printed.indexOf("packages:"), printed);
                        })),
                yaml("name: deploy\nsteps:\n  - run: curl https://host/remoteEntry.js\n",
                        source -> source.path(".github/workflows/deploy.yml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "MIME type");
                            assertTrue(printed.indexOf("~~") > printed.indexOf("run:"), printed);
                        })));
    }

    @Test
    void marksNginxDockerAndHtmlDeploymentContracts() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationResourceRisks()),
                text("location = /remoteEntry.js { add_header Cache-Control no-cache; }", source -> source.path("nginx.conf")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "cache policy");
                            assertTrue(printed.indexOf("~~") > printed.indexOf("location = /"), printed);
                        })),
                text("RUN test -f dist/remoteEntry.js", source -> source.path("Dockerfile")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "output location"))),
                text("<script type=\"module\" src=\"https://mfe/remoteEntry.js\"></script>", source -> source.path("src/index.html")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "CORS/CSP/SRI"))));
    }

    @Test
    void installLeafIsReviewedButInstallDirectoryIsExcluded() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationResourceRisks()),
                text("<script src=\"/remoteEntry.js\"></script>", source -> source.path("src/install.html")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "CORS/CSP/SRI"))),
                text("<script src=\"/remoteEntry.js\"></script>", source -> source.path("install-cache/index.html")));
    }

    @Test
    void precisePlainTextResourceMarkerIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationResourceRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("location /apps/remoteEntry.js;", source -> source.path("nginx.conf")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.indexOf("~~") > printed.indexOf("location /apps/"), printed);
                            String message = "Review this deployment resource";
                            assertTrue(printed.indexOf(message) == printed.lastIndexOf(message), printed);
                        })));
    }

    @Test
    void resourceFinderProtectsUnrelatedAndGeneratedResources() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationResourceRisks()),
                text("left-pad@1.0.0:\n version 1.0.0", source -> source.path("yarn.lock")),
                text("location /assets { try_files $uri =404; }", source -> source.path("nginx.conf")),
                text("remoteEntry.js", source -> source.path("dist/nginx.conf")));
    }

    @Test
    void resourceFinderRejectsSiblingPackageAndAdditionalGeneratedTrees() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationResourceRisks()),
                text("@angular-architects/module-federation-runtime@16.0.4:\n  version 16.0.4\n",
                        source -> source.path("yarn.lock")),
                text("remoteEntry.js", source -> source.path(".cache/nginx.conf")),
                text("remoteEntry.js", source -> source.path("storybook-static/index.html")));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
