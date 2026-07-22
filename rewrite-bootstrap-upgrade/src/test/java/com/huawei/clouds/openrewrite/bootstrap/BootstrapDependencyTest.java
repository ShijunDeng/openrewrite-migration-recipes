package com.huawei.clouds.openrewrite.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class BootstrapDependencyTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.bootstrap.UpgradeBootstrapTo5_3_6";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.4.1", "4.6.0", "4.6.1", "4.6.2", "5.2.0", "5.2.3"})
    void upgradesEveryXlsxExactSource(String version) {
        rewriteRun(json("{\"dependencies\":{\"bootstrap\":\"" + version + "\"}}",
                "{\"dependencies\":{\"bootstrap\":\"5.3.6\"}}", source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.4.1", "4.6.0", "4.6.1", "4.6.2", "5.2.0", "5.2.3"})
    void preservesCaretForEveryXlsxSource(String version) {
        rewriteRun(json("{\"dependencies\":{\"bootstrap\":\"^" + version + "\"}}",
                "{\"dependencies\":{\"bootstrap\":\"^5.3.6\"}}", source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.4.1", "4.6.0", "4.6.1", "4.6.2", "5.2.0", "5.2.3"})
    void preservesTildeForEveryXlsxSource(String version) {
        rewriteRun(json("{\"dependencies\":{\"bootstrap\":\"~" + version + "\"}}",
                "{\"dependencies\":{\"bootstrap\":\"~5.3.6\"}}", source -> source.path("package.json")));
    }

    @Test
    void upgradesAllFourDirectSections() {
        rewriteRun(json(
                "{\"dependencies\":{\"bootstrap\":\"3.4.1\"},\"devDependencies\":{\"bootstrap\":\"^4.6.0\"},\"peerDependencies\":{\"bootstrap\":\"~5.2.0\"},\"optionalDependencies\":{\"bootstrap\":\"4.6.2\"}}",
                "{\"dependencies\":{\"bootstrap\":\"5.3.6\"},\"devDependencies\":{\"bootstrap\":\"^5.3.6\"},\"peerDependencies\":{\"bootstrap\":\"~5.3.6\"},\"optionalDependencies\":{\"bootstrap\":\"5.3.6\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "=3.4.1", "v4.6.0", "4.6.2-beta.1", "5.2.3+company.1", ">=3.4.1", ">4.6.2",
            "3.4.1 || 5.2.3", "4.6.0 - 5.2.0", "4.x", "*", "latest", "${BOOTSTRAP_VERSION}",
            "workspace:^4.6.2", "npm:@example/bootstrap@4.6.2", "file:../bootstrap", "link:../bootstrap",
            "github:twbs/bootstrap#v4.6.2", "git+https://github.com/twbs/bootstrap.git#v4.6.2",
            "https://example.test/bootstrap.tgz", "3.4.0", "4.5.3", "5.2.2", "5.3.6", "^5.3.6", "6.0.0"
    })
    void leavesComplexPrereleaseDynamicProtocolAndUnlistedDeclarations(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"bootstrap\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void ignoresOverridesScriptsLocksOtherJsonAndSimilarPackages() {
        rewriteRun(
                json("{\"overrides\":{\"bootstrap\":\"4.6.2\"},\"resolutions\":{\"bootstrap\":\"5.2.3\"},\"scripts\":{\"css\":\"bootstrap 4.6.2\"},\"dependencies\":{\"react-bootstrap\":\"4.6.2\",\"bootstrap-vue\":\"5.2.0\"}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"bootstrap\":\"4.6.2\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"bootstrap\":\"3.4.1\"}}",
                        source -> source.path("fixtures/dependencies.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"node_modules/pkg/package.json", "vendor/bootstrap/package.json", "dist/package.json",
            "build/package.json", "generated/package.json", "install/package.json", "target/package.json",
            ".mvn/package.json", ".m2/cache/package.json", ".yarn/cache/package.json"})
    void excludesInstalledGeneratedAndBuiltTrees(String path) {
        rewriteRun(json("{\"dependencies\":{\"bootstrap\":\"4.6.2\"}}", source -> source.path(path)));
    }

    @Test
    void exactWhitelistIsPackageVisible() {
        assertEquals(Set.of("3.4.1", "4.6.0", "4.6.1", "4.6.2", "5.2.0", "5.2.3"),
                BootstrapSupport.SOURCES);
    }

    @Test
    void strictRecipeIsDiscoverableValidAndIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"bootstrap\":\"~4.6.1\"}}",
                        "{\"dependencies\":{\"bootstrap\":\"~5.3.6\"}}",
                        source -> source.path("package.json")));
        Recipe recipe = environment().activateRecipes(UPGRADE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> UPGRADE.equals(candidate.getName())));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.bootstrap")
                .scanYamlResources()
                .build();
    }
}
