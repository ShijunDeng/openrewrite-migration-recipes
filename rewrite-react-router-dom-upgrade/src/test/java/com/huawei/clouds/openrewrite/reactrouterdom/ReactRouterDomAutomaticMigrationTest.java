package com.huawei.clouds.openrewrite.reactrouterdom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class ReactRouterDomAutomaticMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void removesOnlyObsoleteDomTypesAfterTargetIsPresent() {
        rewriteRun(spec -> spec.recipe(new MigrateReactRouterDomPackageJson()), json(
                """
                {"dependencies":{"react-router-dom":"6.30.4","@types/react-router-dom":"^5.3.3"},
                 "devDependencies":{"@types/react-router":"^5.1.20","typescript":"5.7.3"}}
                """,
                """
                {"dependencies":{"react-router-dom":"6.30.4"},
                 "devDependencies":{"@types/react-router":"^5.1.20","typescript":"5.7.3"}}
                """,
                s -> s.path("package.json")));
    }

    @Test
    void leavesDomTypesWhenTargetIsAbsentAndNeverTouchesCentralOwner() {
        rewriteRun(spec -> spec.recipe(new MigrateReactRouterDomPackageJson()),
                json("{\"dependencies\":{\"react-router-dom\":\"5.3.4\"},\"devDependencies\":{\"@types/react-router-dom\":\"^5.3.3\"}}",
                        s -> s.path("package.json")),
                json("{\"dependencies\":{\"react-router-dom\":\"6.30.4\"},\"overrides\":{\"@types/react-router-dom\":\"5.3.3\"}}",
                        s -> s.path("package.json")));
    }

    @Test
    void renamesOfficialNavLinkExactPropIncludingAliasAndAssignedValue() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactRouterDomSource()), typescript(
                "import { NavLink as RouterLink } from 'react-router-dom';\nexport const Nav = () => <><RouterLink exact to='/'>Home</RouterLink><RouterLink exact={rootOnly} to='/a' /></>;\n",
                "import { NavLink as RouterLink } from 'react-router-dom';\nexport const Nav = () => <><RouterLink end to='/'>Home</RouterLink><RouterLink end={rootOnly} to='/a' /></>;\n",
                s -> s.path("src/Nav.tsx")));
    }

    @Test
    void movesSoleStaticRouterImportToOfficialServerEntry() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactRouterDomSource()),
                typescript(
                        "import { StaticRouter } from 'react-router-dom';\nexport const App = () => <StaticRouter location='/x'><main /></StaticRouter>;\n",
                        "import { StaticRouter } from 'react-router-dom/server';\nexport const App = () => <StaticRouter location='/x'><main /></StaticRouter>;\n",
                        s -> s.path("server/render.tsx")),
                typescript(
                        "import { StaticRouter as ServerRouter } from \"react-router-dom\";\nexport const App = () => <ServerRouter location='/x'><main /></ServerRouter>;\n",
                        "import { StaticRouter as ServerRouter } from \"react-router-dom/server\";\nexport const App = () => <ServerRouter location='/x'><main /></ServerRouter>;\n",
                        s -> s.path("server/aliased.tsx"))
        );
    }

    @Test
    void avoidsSameNamedForeignComponentsAndUnsafeMixedImports() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactRouterDomSource()),
                typescript("import { NavLink } from '@company/ui';\nconst x = <NavLink exact to='/' />;\n", s -> s.path("src/ui.tsx")),
                typescript("const NavLink = props => <a {...props}/>;\nconst x = <NavLink exact to='/' />;\n", s -> s.path("src/local.tsx")),
                typescript("import { StaticRouter, Route } from 'react-router-dom';\nconst x = <StaticRouter><Route exact path='/' /></StaticRouter>;\n", s -> s.path("server/mixed.tsx")),
                typescript("import Router, { StaticRouter } from 'react-router-dom';\nconst x = <StaticRouter/>;\n", s -> s.path("server/default-mixed.tsx")),
                typescript("import { Route } from 'react-router-dom';\nconst x = <Route exact sensitive path='/' />;\n", s -> s.path("src/route.tsx")),
                typescript("import { NavLink } from 'react-router-dom';\nfunction Preview(NavLink: any) { return <NavLink exact to='/' />; }\n",
                        s -> s.path("src/shadowed.tsx"))
        );
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactRouterDomSource())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { NavLink } from 'react-router-dom';\nconst x = <NavLink exact to='/' />;\n",
                        "import { NavLink } from 'react-router-dom';\nconst x = <NavLink end to='/' />;\n",
                        s -> s.path("src/nav.tsx")));
    }
}
