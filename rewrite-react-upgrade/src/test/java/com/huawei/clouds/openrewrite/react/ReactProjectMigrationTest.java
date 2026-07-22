package com.huawei.clouds.openrewrite.react;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class ReactProjectMigrationTest implements RewriteTest {
    private static final String PROJECT = "com.huawei.clouds.openrewrite.react.AuditReact19ProjectCompatibility";
    private static final String RESOURCE = "com.huawei.clouds.openrewrite.react.AuditReact19ResourceCompatibility";

    @ParameterizedTest(name = "marks companion package {0}")
    @MethodSource("companionPackages")
    void marksEachCompanionDependency(String packageName, String version, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        "{\"dependencies\":{\"" + packageName + "\":\"" + version + "\"}}",
                        "{\"dependencies\":{/*~~(" + message + ")~~>*/\"" + packageName + "\":\"" + version + "\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void marksCompanionsInEveryDirectDependencySection() {
        String message = "Align react-dom with React 19.2.7, then migrate root, hydration and server-rendering APIs together";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        """
                        {
                          "dependencies": {"react-dom": "18.2.0"},
                          "devDependencies": {"react-dom": "18.2.0"},
                          "peerDependencies": {"react-dom": "18.2.0"},
                          "optionalDependencies": {"react-dom": "18.2.0"}
                        }
                        """,
                        """
                        {
                          "dependencies": {/*~~(%s)~~>*/"react-dom": "18.2.0"},
                          "devDependencies": {/*~~(%s)~~>*/"react-dom": "18.2.0"},
                          "peerDependencies": {/*~~(%s)~~>*/"react-dom": "18.2.0"},
                          "optionalDependencies": {/*~~(%s)~~>*/"react-dom": "18.2.0"}
                        }
                        """.formatted(message, message, message, message),
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void leavesAlignedRenderersAndUnrelatedPackagesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        """
                        {
                          "dependencies": {
                            "react-dom": "19.2.7",
                            "react-test-renderer": "^19.2.7",
                            "preact": "10.27.2",
                            "react-is": "19.2.7"
                          }
                        }
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void leavesOverridesNestedMetadataLockfilesAndOrdinaryJsonUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"overrides\":{\"react-dom\":\"18.2.0\"}}", source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"react-dom\":\"18.2.0\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"react-dom\":\"18.2.0\"}}", source -> source.path("fixtures/data.json"))
        );
    }

    @Test
    void marksClassicTypeScriptJsxConfiguration() {
        String message = "React 19 requires the modern JSX transform; select react-jsx/react-jsxdev unless the framework owns JSX compilation";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        "{\"compilerOptions\":{\"jsx\":\"react\"}}",
                        "{\"compilerOptions\":{/*~~(" + message + ")~~>*/\"jsx\":\"react\"}}",
                        source -> source.path("tsconfig.json")
                )
        );
    }

    @Test
    void marksPreservedJsxForFrameworkReview() {
        String message = "React 19 requires the modern JSX transform; select react-jsx/react-jsxdev unless the framework owns JSX compilation";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        "{\"compilerOptions\":{\"jsx\":\"preserve\"}}",
                        "{\"compilerOptions\":{/*~~(" + message + ")~~>*/\"jsx\":\"preserve\"}}",
                        source -> source.path("jsconfig.json")
                )
        );
    }

    @Test
    void leavesModernTypeScriptJsxConfigurationUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"compilerOptions\":{\"jsx\":\"react-jsx\"}}", source -> source.path("tsconfig.json")),
                json("{\"compilerOptions\":{\"jsx\":\"react-jsxdev\"}}", source -> source.path("jsconfig.json"))
        );
    }

    @Test
    void marksBabelClassicRuntime() {
        String message = "React 19 requires the modern JSX transform; switch the React preset runtime to automatic after verifying the compiler chain";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        "{\"presets\":[[\"@babel/preset-react\",{\"runtime\":\"classic\"}]]}",
                        "{\"presets\":[[\"@babel/preset-react\",{/*~~(" + message + ")~~>*/\"runtime\":\"classic\"}]]}",
                        source -> source.path("babel.config.json")
                )
        );
    }

    @Test
    void marksRemovedReactUmdArtifactsInHtml() {
        String url = "https://unpkg.com/react@16.14.0/umd/react.production.min.js";
        String message = "React 19 removed UMD builds; use an ESM CDN or a build tool and update react/react-dom together";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RESOURCE)),
                text(
                        "<script src=\"" + url + "\"></script>\n",
                        "<script src=\"~~(" + message + ")~~>" + url + "\"></script>\n",
                        source -> source.path("public/index.html")
                )
        );
    }

    @Test
    void marksJsdelivrReactDomUmdArtifact() {
        String url = "https://cdn.jsdelivr.net/npm/react-dom@18.2.0/umd/react-dom.production.min.js";
        String message = "React 19 removed UMD builds; use an ESM CDN or a build tool and update react/react-dom together";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RESOURCE)),
                text(
                        "<script src='" + url + "'></script>\n",
                        "<script src='~~(" + message + ")~~>" + url + "'></script>\n",
                        source -> source.path("public/index.htm")
                )
        );
    }

    @Test
    void marksInlineLegacyRootInHtml() {
        String message = "React 19 removed ReactDOM.render/hydrate; move inline bootstrap code to createRoot/hydrateRoot and an ESM client entry";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RESOURCE)),
                text(
                        "<script>ReactDOM.render(App, root);</script>\n",
                        "<script>~~(" + message + ")~~>ReactDOM.render(App, root);</script>\n",
                        source -> source.path("public/index.html")
                )
        );
    }

    @Test
    void resourceAuditLeavesEsmUrlsAndNonHtmlUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RESOURCE)),
                text("<script type=\"module\" src=\"https://esm.sh/react@19.2.7\"></script>\n",
                        source -> source.path("public/index.html")),
                text("https://unpkg.com/react@18.2.0/umd/react.production.min.js\n",
                        source -> source.path("docs/migration.md"))
        );
    }

    @Test
    void discoversAndValidatesProjectAndResourceRecipes() {
        Environment environment = environment();
        for (String name : new String[]{PROJECT, RESOURCE}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
            assertEquals(name, recipe.getName());
        }
    }

    private static Stream<Arguments> companionPackages() {
        return Stream.of(
                companion("react-dom", "18.2.0", "Align react-dom with React 19.2.7, then migrate root, hydration and server-rendering APIs together"),
                companion("react-test-renderer", "18.2.0", "react-test-renderer is deprecated; align it temporarily and migrate tests to a maintained renderer"),
                companion("react-native", "0.76.0", "Verify the React Native release's declared React peer before changing React; do not force the web renderer matrix"),
                companion("@types/react", "18.3.20", "Align @types/react with React 19 and resolve ref, JSX, reducer and ReactElement type breaks"),
                companion("@types/react-dom", "18.3.7", "Align @types/react-dom with react-dom 19 and resolve client/server entry-point changes"),
                companion("react-router", "5.3.4", "Verify this React Router major supports React 19 and test navigation, transitions and SSR"),
                companion("react-router-dom", "5.3.4", "Verify this React Router DOM major supports React 19 and test navigation, hydration and data routers"),
                companion("next", "12.1.0", "Use a Next.js release that declares React 19 support; follow its compiler, server-components and hydration upgrade guide"),
                companion("gatsby", "5.14.0", "Verify Gatsby's React 19 support and test SSR, hydration, plugins and webpack aliases before upgrading"),
                companion("enzyme", "3.11.0", "Enzyme has no official React 18/19 adapter; replace or explicitly own an unofficial adapter strategy"),
                companion("@testing-library/react", "13.4.0", "Upgrade React Testing Library for React 19 act/root support and re-run async interaction tests"),
                companion("eslint-plugin-react-hooks", "4.6.2", "Upgrade eslint-plugin-react-hooks and review newly reported effect/compiler diagnostics"),
                companion("@vitejs/plugin-react", "4.0.4", "Verify the Vite React plugin and JSX transform support the selected React 19 toolchain"),
                companion("mobx-react", "7.5.0", "Verify mobx-react's React 19 peer range and observer behavior under StrictMode and concurrent rendering"),
                companion("@material-ui/core", "4.11.2", "Material UI v4 targets older React generations; migrate to a React 19-compatible MUI release and retest styling/SSR")
        );
    }

    private static Arguments companion(String name, String version, String message) {
        return Arguments.of(name, version, message);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.react")
                .scanYamlResources()
                .build();
    }
}
