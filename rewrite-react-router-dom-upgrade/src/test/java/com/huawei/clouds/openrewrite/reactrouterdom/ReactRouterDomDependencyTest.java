package com.huawei.clouds.openrewrite.reactrouterdom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.json.Assertions.json;

class ReactRouterDomDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedReactRouterDomDependency());
    }

    @ParameterizedTest(name = "upgrades visible spreadsheet declaration {0}")
    @MethodSource("visibleDeclarations")
    void upgradesEveryVisibleExactCaretAndTildeDeclaration(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"react-router-dom\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"react-router-dom\":\"6.30.4\"}}",
                s -> s.path("package.json")));
    }

    static Stream<String> visibleDeclarations() {
        return Stream.of("4.3.1", "5.2.0", "5.2.1", "5.3.0", "5.3.1", "5.3.2", "5.3.4",
                        "6.10.0", "6.14.0", "6.14.2")
                .flatMap(version -> Stream.of(version, "^" + version, "~" + version));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {"dependencies":{"react-router-dom":"4.3.1"},
                 "devDependencies":{"react-router-dom":"^5.2.1"},
                 "peerDependencies":{"react-router-dom":"~5.3.4"},
                 "optionalDependencies":{"react-router-dom":"6.14.2"}}
                """,
                """
                {"dependencies":{"react-router-dom":"6.30.4"},
                 "devDependencies":{"react-router-dom":"6.30.4"},
                 "peerDependencies":{"react-router-dom":"6.30.4"},
                 "optionalDependencies":{"react-router-dom":"6.30.4"}}
                """,
                s -> s.path("apps/web/package.json")));
    }

    @ParameterizedTest(name = "leaves unlisted or non-scalar declaration {0}")
    @ValueSource(strings = {
            "4.3.0", "4.4.0", "5.1.2", "5.3.3", "5.3.3", "6.14.1", "6.30.4", "7.0.0",
            ">=4.3.1 <5", "4.3.1 - 5.3.4", "4.3.1 || ^5.3.4", ">=6.10.0",
            "v4.3.1", "=4.3.1", " 4.3.1", "4.3.1 ", "4.3.1-rc.1", "4.3.1+company.7",
            "workspace:^4.3.1", "npm:router@4.3.1", "file:../router", "link:../router",
            "git+https://github.com/remix-run/react-router.git", "https://example.test/router.tgz", "latest", "$ROUTER_VERSION"
    })
    void leavesUnlistedComplexAndNonRegistryDeclarations(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"react-router-dom\":\"" + declaration + "\"}}",
                s -> s.path("package.json")));
    }

    @Test
    void leavesCentralOwnersNestedLookalikesAndOtherFilesAlone() {
        rewriteRun(
                json("{\"overrides\":{\"react-router-dom\":\"4.3.1\"},\"resolutions\":{\"react-router-dom\":\"5.3.4\"}}",
                        s -> s.path("package.json")),
                json("{\"dependencies\":{\"nested\":{\"react-router-dom\":\"4.3.1\"}},\"metadata\":{\"react-router-dom\":\"5.3.4\"}}",
                        s -> s.path("package.json")),
                json("{\"dependencies\":{\"react-router\":\"4.3.1\",\"@types/react-router-dom\":\"4.3.1\",\"react-router-dom-extra\":\"4.3.1\"}}",
                        s -> s.path("package.json")),
                json("{\"dependencies\":{\"react-router-dom\":\"4.3.1\"}}", s -> s.path("package-lock.json")),
                json("{\"dependencies\":{\"react-router-dom\":\"4.3.1\"}}", s -> s.path("config.json"))
        );
    }

    @Test
    void upgradesPinnedClassToFunctionHooksFixture() {
        rewriteRun(json(
                """
                {"name":"new","dependencies":{"create-effect":"0.3.1","react":"16.8.0",
                 "react-dom":"16.8.0","react-router-dom":"4.3.1","react-scripts":"2.0.3"}}
                """,
                """
                {"name":"new","dependencies":{"create-effect":"0.3.1","react":"16.8.0",
                 "react-dom":"16.8.0","react-router-dom":"6.30.4","react-scripts":"2.0.3"}}
                """,
                s -> s.path("s-yadav/class-to-function-with-react-hooks/package.json")));
    }

    @Test
    void upgradesPinnedNavStateFixtureButPreservesCompanions() {
        rewriteRun(json(
                """
                {"name":"nav-state-react-router","dependencies":{"lodash":"^4.17.10","react":"^16.4.1",
                 "react-dom":"^16.4.1","react-redux":"^5.0.7","react-router":"^4.3.1",
                 "react-router-dom":"^4.3.1","react-scripts":"1.1.4","redux":"^4.0.0"}}
                """,
                """
                {"name":"nav-state-react-router","dependencies":{"lodash":"^4.17.10","react":"^16.4.1",
                 "react-dom":"^16.4.1","react-redux":"^5.0.7","react-router":"^4.3.1",
                 "react-router-dom":"6.30.4","react-scripts":"1.1.4","redux":"^4.0.0"}}
                """,
                s -> s.path("ohansemmanuel/nav-state-react-router/package.json")));
    }

    @Test
    void upgradesPinnedConnectedReactRouterFixtureButPreservesArchitecture() {
        rewriteRun(json(
                """
                {"name":"connected-react-router-example-basic","dependencies":{"connected-react-router":"^6.0.0",
                 "history":"^4.7.2","react":"^16.6.0","react-dom":"^16.6.0","react-redux":"^6.0.0",
                 "react-router":"^4.3.1","react-router-dom":"^4.3.1","redux":"^4.0.1"}}
                """,
                """
                {"name":"connected-react-router-example-basic","dependencies":{"connected-react-router":"^6.0.0",
                 "history":"^4.7.2","react":"^16.6.0","react-dom":"^16.6.0","react-redux":"^6.0.0",
                 "react-router":"^4.3.1","react-router-dom":"6.30.4","redux":"^4.0.1"}}
                """,
                s -> s.path("supasate/connected-react-router/examples/basic/package.json")));
    }

    @Test
    void dependencyMigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), json(
                "{\"dependencies\":{\"react-router-dom\":\"^5.3.4\"}}",
                "{\"dependencies\":{\"react-router-dom\":\"6.30.4\"}}",
                s -> s.path("package.json")));
    }
}
