package com.huawei.clouds.openrewrite.reactrouterdom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class ReactRouterDomRiskMarkerTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksPinnedNavStateSwitchRouteFixtureAtExactNodes() {
        assertTs("""
                import { BrowserRouter, Switch, Route } from 'react-router-dom';
                export const App = () => <BrowserRouter><Switch>
                  <Route exact path="/" component={Home}/><Route path="/post/:id" component={Post}/>
                </Switch></BrowserRouter>;
                """, "ohansemmanuel/nav-state-react-router/src/containers/App.js",
                "Switch becomes Routes", "Route exact is removed", "Route component rendering changes",
                "Route path uses v6 ranked relative patterns");
    }

    @Test
    void marksPinnedConnectedRouterHistoryFixture() {
        assertTs("""
                import { ConnectedRouter } from 'connected-react-router';
                import { Switch, Route } from 'react-router-dom';
                export const App = ({history}) => <ConnectedRouter history={history}><Switch><Route path="/" component={Home}/></Switch></ConnectedRouter>;
                """, "supasate/connected-react-router/examples/basic/src/App.js",
                "ConnectedRouter owns external history", "External history passed to ConnectedRouter", "Switch becomes Routes");
    }

    @Test
    void marksPinnedTurkishReactGuideUseHistoryFixture() {
        assertTs("""
                import { Link, useHistory } from 'react-router-dom';
                export function Home() { const history = useHistory(); return <><Link to="/settings">Settings</Link><button onClick={() => history.push('/profile')}/></>; }
                """, "orcuntuna/react-turkce-kaynak/README-useHistory.tsx",
                "useHistory navigation must preserve", "history.push must move to useNavigate",
                "Navigation destination uses v6 route-relative resolution");
    }

    @Test
    void marksPinnedReactNativeWebUiSsrFixture() {
        assertTs("""
                import { StaticRouter, Switch, Route } from 'react-router-dom';
                export const ServerApp = ({url, context}) => <StaticRouter location={url} context={context}><Switch><Route path="/" component={Home}/></Switch></StaticRouter>;
                """, "CareLuLu/react-native-web-ui-components/README-ssr.tsx",
                "StaticRouter moved to react-router-dom/server", "StaticRouter SSR must resolve",
                "Switch becomes Routes", "Router location changes routing/SSR integration boundaries");
    }

    @Test
    void marksNavLinkAndCustomLinkMigrationDecisions() {
        assertTs("""
                import { NavLink, Link } from 'react-router-dom';
                export const Nav = () => <><NavLink exact strict activeClassName="on" activeStyle={{color:'red'}} to="/users">Users</NavLink><Link component={Button} state={{from:'nav'}} to={{pathname:'/x', state:{old:true}}}/></>;
                """, "JofArnold/NavLink.tsx",
                "NavLink activeClassName was removed", "NavLink activeStyle was removed", "NavLink strict was removed",
                "Link component was removed", "Navigation destination uses v6 route-relative resolution", "Navigation state is history state");
    }

    @Test
    void deterministicNavLinkExactAutoLeavesOnlySemanticMarkers() {
        assertTsWithRecommendedRecipe("""
                import { NavLink } from 'react-router-dom';
                export const Nav = () => <NavLink exact activeClassName="selected" to="/"/>;
                """, "Klerith/Navbar.tsx", "end", "NavLink activeClassName was removed");
    }

    @Test
    void marksRedirectPromptAndLegacyRouteRendering() {
        assertTs("""
                import { Redirect, Prompt, Route } from 'react-router-dom';
                export const App = ({dirty}) => <><Prompt when={dirty} message="Leave?"/><Route render={p => p.ok ? <Home/> : <Redirect push to="/login"/>}/></>;
                """, "src/legacy.tsx", "Prompt was removed", "Route render rendering changes",
                "Redirect requires Navigate", "v5 defaults to replace");
    }

    @Test
    void marksMatchingCallsIncludingAliases() {
        assertTs("""
                import { useRouteMatch as useLegacyMatch, matchPath, generatePath } from 'react-router-dom';
                export function Child({pathname}) { const match = useLegacyMatch({strict:true, sensitive:true}); return [matchPath(pathname,{path:'/u/:id',exact:true}), generatePath('/u/:id',{id:1})]; }
                """, "src/matching.ts", "useRouteMatch uses the v6 path-pattern", "matchPath uses the v6 path-pattern",
                "generatePath uses the v6 path-pattern");
    }

    @Test
    void marksEveryTrackedHistoryOperation() {
        assertTs("""
                import { useHistory } from 'react-router-dom';
                export function nav() { const h = useHistory(); h.push('/a'); h.replace('/b'); h.go(-2); h.goBack(); h.goForward(); h.listen(fn); h.block(fn); }
                """, "src/history.ts", "history.push", "history.replace", "history.go must", "history.goBack",
                "history.goForward", "history.listen", "history.block");
    }

    @Test
    void marksReactRouterConfigAndDataRouteObjectLifecycle() {
        assertTs("""
                import { renderRoutes } from 'react-router-config';
                import { createBrowserRouter, RouterProvider } from 'react-router-dom';
                const router = createBrowserRouter([{path:'/', element:<Home/>, loader:load, action:save, errorElement:<Error/>, children:[{index:true, element:<Index/>}], lazy:loadRoute}]);
                export const App = ({routes}) => <><RouterProvider router={router}/>{renderRoutes(routes)}</>;
                """, "src/router.tsx", "createBrowserRouter route configuration", "Route object path participates",
                "Route object loader participates", "Route object action participates", "Route object errorElement participates",
                "Route object lazy participates", "RouterProvider enables data-router lifecycle", "react-router-config:renderRoutes");
    }

    @Test
    void marksBlockerRedirectSecurityAndRoutesSemantics() {
        assertTs("""
                import { Routes, Route, Navigate, useBlocker, unstable_usePrompt, useBeforeUnload, redirect } from 'react-router-dom';
                export function App(){ useBlocker(dirty); unstable_usePrompt({when:dirty,message:'Leave?'}); useBeforeUnload(save); return <Routes><Route path="old" element={<Navigate to="//example.test"/>}/></Routes>; }
                export async function loader(){ return redirect('//example.test'); }
                """, "src/data-router.tsx", "Routes uses ranked relative matching", "useBlocker needs an explicit",
                "unstable_usePrompt needs an explicit", "useBeforeUnload needs an explicit", "redirect response must be validated",
                "Navigation destination uses v6 route-relative resolution");
    }

    @Test
    void marksPrivateEntryAndMixedStaticRouterImportWithoutCoarseFileMarker() {
        assertTs("""
                import { UNSAFE_DataRouterContext } from 'react-router-dom/dist/index';
                import { StaticRouter, Route } from 'react-router-dom';
                export const App = () => <StaticRouter><Route path="/"/></StaticRouter>;
                """, "server/private.tsx", "Private react-router-dom deep entry detected",
                "StaticRouter moved to react-router-dom/server", "StaticRouter SSR must resolve");
    }

    @Test
    void marksNamespaceDefaultAndCommonJsImportsAtTheirModuleExpression() {
        assertTs("""
                import * as Router from 'react-router-dom';
                import LegacyRouter, { Link } from 'react-router-dom';
                const server = require('react-router-dom/server');
                export const x = <Router.Switch><Link to="/"/></Router.Switch>;
                """, "src/import-styles.tsx", "Namespace/default React Router import requires manual inventory",
                "CommonJS React Router import requires manual inventory");
    }

    @Test
    void sameNamedForeignAndLocalApisAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindReactRouterDomTypeScriptRisks()),
                typescript("import { Switch, Route, useHistory } from '@company/router';\nconst h=useHistory(); h.push('/'); const x=<Switch><Route exact path='/'/></Switch>;\n",
                        s -> s.path("src/foreign.tsx").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("function useHistory(){return {push(){}}} const Switch=p=>p.children; const Route=p=>null; const h=useHistory(); h.push('/'); const x=<Switch><Route exact/></Switch>;\n",
                        s -> s.path("src/local.tsx").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import { NavLink, createBrowserRouter } from 'react-router-dom';\nfunction Preview(NavLink: any, createBrowserRouter: any) { const routes=createBrowserRouter({path:'/'}); return <NavLink exact to='/'/>; }\n",
                        s -> s.path("src/shadowed.tsx").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import { Link } from 'react-router-dom';\nconst config = configure({path:'/', children:[], loader:load});\n",
                        s -> s.path("src/foreign-config.ts").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksPackageRuntimeCompanionsTypesAndCentralOwner() {
        rewriteRun(spec -> spec.recipe(new FindReactRouterDomJsonRisks()), json("""
                {"engines":{"node":"12.22.12"},"dependencies":{"react":"^16.4.1","react-dom":"15.6.2",
                 "react-router":"^5.3.4","react-router-dom":">=5.2.0 <6","history":"^4.10.1",
                 "connected-react-router":"^6.9.3","react-router-config":"^5.1.1","react-router-dom-v5-compat":"^6.30.0"},
                 "devDependencies":{"@types/react-router":"^5.1.20","@types/react-router-dom":"^5.3.3"},
                 "pnpm":{"overrides":{"react-router-dom":"5.3.4"}}}
                """, s -> s.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    for (String message : new String[]{"must be at least 16.8", "mixed majors", "unlisted, newer, complex",
                            "legacy router/history architecture", "describes v4/v5 APIs", "declares Node >=14", "Central package-manager ownership"}) {
                        assertTrue(out.contains(message), out);
                    }
                })));
    }

    @Test
    void targetWithSupportedRuntimeAndUnrelatedPackageAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindReactRouterDomJsonRisks()),
                json("{\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"react\":\"^18.3.1\",\"react-dom\":\"^18.3.1\",\"react-router-dom\":\"6.30.4\"}}",
                        s -> s.path("package.json").afterRecipe(after -> assertNoMarker(after.printAll()))),
                json("{\"engines\":{\"node\":\"10.0.0\"},\"dependencies\":{\"react\":\"15.6.2\"}}",
                        s -> s.path("services/api/package.json").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    private void assertTs(String before, String path, String... messages) {
        rewriteRun(spec -> spec.recipe(new FindReactRouterDomTypeScriptRisks()),
                typescript(before, s -> s.path(path).after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    for (String message : messages) assertTrue(out.contains(message), out);
                })));
    }

    private void assertTsWithRecommendedRecipe(String before, String path, String... messages) {
        org.openrewrite.config.Environment environment = org.openrewrite.config.Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.reactrouterdom").scanYamlResources().build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.reactrouterdom.MigrateReactRouterDomTo6_30_4")),
                typescript(before, s -> s.path(path).after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    for (String message : messages) assertTrue(out.contains(message), out);
                })));
    }

    private static void assertNoMarker(String source) {
        assertFalse(source.contains("~~("), source);
    }
}
