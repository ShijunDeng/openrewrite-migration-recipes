package com.huawei.clouds.openrewrite.reactrouterdom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class UpgradeReactRouterDomTest implements RewriteTest {
    private static final String STRICT =
            "com.huawei.clouds.openrewrite.reactrouterdom.UpgradeReactRouterDomTo6_30_4";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.reactrouterdom.MigrateReactRouterDomTo6_30_4";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void yamlRecipesAreDiscoverableWithExpectedComposition() {
        Environment environment = environment();
        assertEquals(1, environment.activateRecipes(STRICT).getRecipeList().size());
        assertEquals(5, environment.activateRecipes(RECOMMENDED).getRecipeList().size());
    }

    @Test
    void strictYamlRecipeChangesOnlyDependency() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(STRICT)),
                json("{\"dependencies\":{\"react-router-dom\":\"^6.14.0\",\"@types/react-router-dom\":\"5.3.3\"}}",
                        "{\"dependencies\":{\"react-router-dom\":\"6.30.4\",\"@types/react-router-dom\":\"5.3.3\"}}",
                        s -> s.path("package.json")),
                typescript("import { NavLink } from 'react-router-dom';\nconst x=<NavLink exact to='/'/>;\n",
                        s -> s.path("src/nav.tsx")));
    }

    @Test
    void recommendedRecipeCombinesAutoMigrationAndPreciseMarkers() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"engines\":{\"node\":\"12.22.12\"},\"dependencies\":{\"react\":\"16.4.1\",\"react-router-dom\":\"^5.3.4\"},\"devDependencies\":{\"@types/react-router-dom\":\"5.3.3\"}}",
                        s -> s.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertTrue(out.contains("\"react-router-dom\":\"6.30.4\""), out);
                            assertTrue(!out.contains("@types/react-router-dom"), out);
                            assertTrue(out.contains("must be at least 16.8"), out);
                            assertTrue(out.contains("declares Node >=14"), out);
                        })),
                typescript("import { NavLink, Switch, Route } from 'react-router-dom';\nconst x=<Switch><Route exact path='/' render={()=><NavLink exact activeClassName='on' to='/'/>}/></Switch>;\n",
                        s -> s.path("src/App.tsx").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertTrue(out.contains("<NavLink end"), out);
                            assertTrue(out.contains("Switch becomes Routes"), out);
                            assertTrue(out.contains("Route render rendering changes"), out);
                            assertTrue(out.contains("NavLink activeClassName was removed"), out);
                        })));
    }

    @Test
    void recommendedRecipeIsIdempotentAfterMarkersArePresent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"react-router-dom\":\"5.2.0\"}}",
                        "{\"dependencies\":{\"react-router-dom\":\"6.30.4\"}}", s -> s.path("package.json")),
                typescript("import { NavLink } from 'react-router-dom';\nconst x=<NavLink exact to='/'/>;\n",
                        s -> s.path("src/nav.tsx").after(actual -> actual)));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.reactrouterdom")
                .scanYamlResources().build();
    }
}
