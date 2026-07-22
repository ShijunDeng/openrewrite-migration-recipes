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

class ReactSourceMigrationTest implements RewriteTest {
    private static final String SOURCE = "com.huawei.clouds.openrewrite.react.MigrateDeterministicReactSourceTo19";
    private static final String AUDIT = "com.huawei.clouds.openrewrite.react.AuditReact19SourceCompatibility";
    private static final String MIGRATION = "com.huawei.clouds.openrewrite.react.MigrateReactTo19_2_7";

    @Test
    void migratesOfficialReactCodemodRenderFixture() {
        // reactjs/react-codemod, fixed commit 5207d594fad6f8b39c51fd7edd2bcb51047dc872.
        // https://github.com/reactjs/react-codemod/blob/5207d594fad6f8b39c51fd7edd2bcb51047dc872/transforms/__testfixtures__/replace-reactdom-render/default.input.js
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        import ReactDom from "react-dom";
                        import Component from "Component";

                        ReactDom.render(<Component />, document.getElementById("app"));
                        """,
                        """
                        import { createRoot } from "react-dom/client";
                        import ReactDom from "react-dom";
                        import Component from "Component";

                        const root = createRoot(document.getElementById("app"));
                        root.render(<Component />);
                        """,
                        source -> source.path("transforms/__testfixtures__/replace-reactdom-render/default.input.js")
                )
        );
    }

    @Test
    void migratesRealDesignPatternsApplicationBootstrap() {
        // zoltantothcom/Design-Patterns-JavaScript, fixed commit 2c7ef902dbefb8a7a2ecea407ac7e8e6682f5b0a.
        // https://github.com/zoltantothcom/Design-Patterns-JavaScript/blob/2c7ef902dbefb8a7a2ecea407ac7e8e6682f5b0a/index.js
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        import React from 'react';
                        import ReactDOM from 'react-dom';
                        import Layout from './src/Layout';
                        const App = () => (<Layout />);
                        ReactDOM.render(<App />, document.getElementById('root'));
                        """,
                        """
                        import React from 'react';
                        import { createRoot } from 'react-dom/client';
                        import ReactDOM from 'react-dom';
                        import Layout from './src/Layout';
                        const App = () => (<Layout />);
                        const root = createRoot(document.getElementById('root'));
                        root.render(<App />);
                        """,
                        source -> source.path("index.js")
                )
        );
    }

    @Test
    void migratesNamespaceHydrateWithSimpleContainer() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        import * as DOM from 'react-dom';
                        DOM.hydrate(<App />, container);
                        """,
                        """
                        import { hydrateRoot } from 'react-dom/client';
                        import * as DOM from 'react-dom';
                        hydrateRoot(container, <App />);
                        """,
                        source -> source.path("src/hydrate.tsx")
                )
        );
    }

    @Test
    void migratesExactActImport() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "import { act } from 'react-dom/test-utils';\nawait act(async () => render());\n",
                        "import { act } from 'react';\nawait act(async () => render());\n",
                        source -> source.path("src/App.test.tsx")
                )
        );
    }

    @Test
    void migratesOfficialImplicitRefAssignmentShape() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "const view = <div ref={current => (instance = current)} />;\n",
                        "const view = <div ref={current => { instance = current; }} />;\n",
                        source -> source.path("src/ref.tsx")
                )
        );
    }

    @Test
    void deterministicMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text(
                        "import ReactDOM from 'react-dom';\nReactDOM.render(<App />, rootNode);\n",
                        "import { createRoot } from 'react-dom/client';\nimport ReactDOM from 'react-dom';\nconst root = createRoot(rootNode);\nroot.render(<App />);\n",
                        source -> source.path("src/idempotent.jsx")
                )
        );
    }

    @Test
    void preservesCrLfLineEndingsDuringRootMigration() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "import ReactDOM from \"react-dom\";\r\nReactDOM.render(<App />, node);\r\n",
                        "import { createRoot } from \"react-dom/client\";\r\nimport ReactDOM from \"react-dom\";\r\n" +
                        "const root = createRoot(node);\r\nroot.render(<App />);\r\n",
                        source -> source.path("src/windows.jsx")
                )
        );
    }

    @Test
    void automaticMigrationLeavesCommentsStringsTemplatesRegexAndLookalikesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        // ReactDOM.render(<App />, root)
                        /* ReactDOM.hydrate(<App />, root) */
                        const docs = "ReactDOM.render(<App />, root)";
                        const template = `ReactDOM.hydrate(<App />, root)`;
                        const matcher = /ReactDOM\\.render/;
                        service.render(<App />, root);
                        """,
                        source -> source.path("src/docs.tsx")
                )
        );
    }

    @Test
    void automaticMigrationLeavesCallbacksAndMultipleRootsForAudit() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        import ReactDOM from 'react-dom';
                        ReactDOM.render(<App />, first, onCommit);
                        ReactDOM.render(<Admin />, second);
                        """,
                        source -> source.path("src/multiple.jsx")
                )
        );
    }

    @Test
    void automaticMigrationLeavesExistingClientRootAndActBindingUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        import { createRoot } from 'react-dom/client';
                        import ReactDOM from 'react-dom';
                        import { act } from 'react-dom/test-utils';
                        const act = wrapper.act;
                        ReactDOM.render(<App />, rootNode);
                        """,
                        source -> source.path("src/conflicts.tsx")
                )
        );
    }

    @Test
    void sourceRecipesLeaveMarkdownAndHtmlToTheResourceAudit() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text("ReactDOM.render(<App />, root);\n", source -> source.path("docs/react.md")),
                text("<script>ReactDOM.render(<App />, root);</script>\n", source -> source.path("public/index.html"))
        );
    }

    @Test
    void marksRealHitachiFunctionPropTypesAssignment() {
        // Hitachi-Automotive-And-Industry-Lab/semantic-segmentation-editor, fixed commit b159ccf46001420b6018e6d0faca3e64b0955cf9.
        // https://github.com/Hitachi-Automotive-And-Industry-Lab/semantic-segmentation-editor/blob/b159ccf46001420b6018e6d0faca3e64b0955cf9/imports/common/SsePopup.jsx#L88
        String message = "React 19 ignores function propTypes; decide whether this component needs TypeScript/another runtime validator (class propTypes differ)";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "SsePopup.propTypes = { trigger: PropTypes.element.isRequired };\n",
                        mark(message, "SsePopup.propTypes =") + " { trigger: PropTypes.element.isRequired };\n",
                        source -> source.path("imports/common/SsePopup.jsx")
                )
        );
    }

    @Test
    void marksRealStorybookDynamicTestUtilsFallback() {
        // storybookjs/storybook, fixed commit 5675a31efd5ffca36b8a3c46c2f5a43ef6863834.
        // https://github.com/storybookjs/storybook/blob/5675a31efd5ffca36b8a3c46c2f5a43ef6863834/code/renderers/react/src/act-compat.ts
        String token = "import('react-dom/test-utils')";
        String message = "React 19 removed react-dom/test-utils except act; import act from react and replace remaining test utilities";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "const deprecatedTestUtils = await " + token + ";\n",
                        "const deprecatedTestUtils = await " + mark(message, token) + ";\n",
                        source -> source.path("code/renderers/react/src/act-compat.ts")
                )
        );
    }

    @ParameterizedTest(name = "marks React 19 source risk {0}")
    @MethodSource("sourceRiskCases")
    void marksEachContextDependentRisk(String name, String path, String before, String token, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(before, before.replace(token, mark(message, token)), source -> source.path(path))
        );
    }

    @Test
    void auditLeavesCommentsStringsTemplatesRegexAndUnrelatedMembersUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        """
                        // ReactDOM.render(<App />, root)
                        const docs = "Widget.propTypes = {} and useRef()";
                        const template = `findDOMNode(component)`;
                        const matcher = /ReactDOM\\.hydrate/;
                        router.render(view);
                        record.ref;
                        """,
                        source -> source.path("src/docs.ts")
                )
        );
    }

    @Test
    void auditLeavesUnimportedReactLookalikesInPlainTypeScriptUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        """
                        function useEffect(callback: () => void) { callback(); }
                        function findDOMNode(value: unknown) { return value; }
                        function renderToString(value: unknown) { return String(value); }
                        const value = useEffect(run);
                        findDOMNode(value);
                        renderToString(value);
                        """,
                        source -> source.path("src/unrelated.ts")
                )
        );
    }

    @Test
    void recommendedRecipeUpgradesMigratesAndMarksRemainingWork() {
        String domMessage = "Align react-dom with React 19.2.7, then migrate root, hydration and server-rendering APIs together";
        String strictMessage = "React 18/19 StrictMode replays effects and ref callbacks in development; verify cleanup and reusable state";
        String rootErrorMessage = "React 19 changed uncaught/caught root error reporting; verify Error Boundaries, monitoring, and onUncaughtError/onCaughtError callbacks";
        String umdMessage = "React 19 removed UMD builds; use an ESM CDN or a build tool and update react/react-dom together";
        String url = "https://unpkg.com/react@18.2.0/umd/react.production.min.js";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION)),
                json(
                        "{\"dependencies\":{\"react\":\"^18.2.0\",\"react-dom\":\"18.2.0\"}}",
                        "{\"dependencies\":{\"react\":\"19.2.7\",/*~~(" + domMessage + ")~~>*/\"react-dom\":\"18.2.0\"}}",
                        source -> source.path("package.json")
                ),
                text(
                        "import ReactDOM from 'react-dom';\nReactDOM.render(<React.StrictMode><App /></React.StrictMode>, node);\n",
                        "import { createRoot } from 'react-dom/client';\nimport ReactDOM from 'react-dom';\n" +
                        "const root = " + mark(rootErrorMessage, "createRoot") + "(node);\nroot.render(" + mark(strictMessage, "<React.StrictMode") +
                        "><App /></React.StrictMode>);\n",
                        source -> source.path("src/main.tsx")
                ),
                text(
                        "<script src=\"" + url + "\"></script>\n",
                        "<script src=\"" + mark(umdMessage, url) + "\"></script>\n",
                        source -> source.path("public/index.html")
                )
        );
    }

    @Test
    void discoversAndValidatesEverySourceRecipe() {
        Environment environment = environment();
        for (String name : new String[]{SOURCE, AUDIT, MIGRATION}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
            assertEquals(name, recipe.getName());
        }
    }

    private static Stream<Arguments> sourceRiskCases() {
        return Stream.of(
                risk("render callback", "src/root.jsx", "ReactDOM.render(<App />, node, done);\n", "ReactDOM.render",
                        "React 19 removed ReactDOM.render/hydrate; migrate the root manually when callbacks, multiple roots or dynamic containers prevent the safe transform"),
                risk("named root import", "src/root.jsx", "import { render } from 'react-dom';\n", "import { render } from 'react-dom'",
                        "React 19 removed legacy named react-dom root APIs; migrate each binding to react-dom/client while preserving root ownership"),
                risk("unmount", "src/root.jsx", "ReactDOM.unmountComponentAtNode(node);\n", "ReactDOM.unmountComponentAtNode",
                        "React 19 removed unmountComponentAtNode; retain the createRoot handle and call root.unmount()"),
                risk("findDOMNode", "src/legacy.jsx", "ReactDOM.findDOMNode(component);\n", "ReactDOM.findDOMNode",
                        "React 19 removed findDOMNode; attach a DOM ref to the owned element without crossing component boundaries"),
                risk("createFactory", "src/factory.js", "const factory = React.createFactory(Button);\n", "React.createFactory",
                        "React 19 removed createFactory; replace it with JSX while preserving keys, children and dynamic component selection"),
                risk("module pattern", "src/factory.js", "import React from 'react';\nfunction Factory() { return { render() { return <View />; } }; }\n", "return { render(",
                        "React 19 removed module-pattern component factories; return JSX from a normal function component and verify lifecycle behavior"),
                risk("defaultProps", "src/Button.jsx", "Button.defaultProps = {size: 'm'};\n", "Button.defaultProps =",
                        "React 19 removes function defaultProps; use parameter defaults only after verifying undefined, wrapper and class-component semantics"),
                risk("contextTypes", "src/Provider.jsx", "Provider.childContextTypes = {theme: PropTypes.object};\n", "Provider.childContextTypes =",
                        "React 19 removed legacy context; migrate the full provider/consumer chain to createContext and Context.Provider"),
                risk("getChildContext", "src/Provider.jsx", "getChildContext() { return {theme}; }\n", "getChildContext(",
                        "React 19 removed legacy getChildContext; migrate the full provider/consumer chain to modern context"),
                risk("string ref", "src/Form.jsx", "return <input ref=\"name\" />;\n", "ref=\"name\"",
                        "React 19 removed string refs; replace with useRef/createRef or a callback ref after checking ownership"),
                risk("element ref", "src/clone.tsx", "const forwarded = element.ref;\n", "element.ref",
                        "React 19 treats ref as a regular prop and deprecates element.ref; read element.props.ref and verify library compatibility"),
                risk("implicit ref", "src/ref.tsx", "return <div ref={node => cache(node)} />;\n", "ref={node =>",
                        "React 19 allows ref cleanup functions; make implicit returns explicit and test StrictMode attach/detach behavior"),
                risk("test utils", "src/test.tsx", "import { Simulate } from 'react-dom/test-utils';\n", "import { Simulate } from 'react-dom/test-utils'",
                        "React 19 removed react-dom/test-utils except act; import act from react and replace remaining test utilities"),
                risk("test renderer", "src/test.tsx", "import renderer from 'react-test-renderer';\n", "import renderer from 'react-test-renderer'",
                        "react-test-renderer is deprecated and may use concurrent behavior; migrate assertions to a maintained DOM/native testing library"),
                risk("StrictMode", "src/main.tsx", "return <React.StrictMode><App /></React.StrictMode>;\n", "<React.StrictMode",
                        "React 18/19 StrictMode replays effects and ref callbacks in development; verify cleanup and reusable state"),
                risk("effect", "src/useSocket.ts", "import React from 'react';\nuseEffect(() => subscribe(), []);\n", "useEffect",
                        "React 18 StrictMode exposes non-idempotent effects; verify cleanup, subscriptions and duplicate external writes"),
                risk("Suspense", "src/App.tsx", "return <Suspense fallback={<Spinner />}><Page /></Suspense>;\n", "<Suspense",
                        "React 18/19 changed Suspense commit, retry and sibling-prewarming behavior; test fallback timing, effects and reveal order"),
                risk("hydration", "src/client.tsx", "hydrateRoot(node, <App />);\n", "hydrateRoot",
                        "React hydration now reports recoverable errors differently and React 19 has stricter mismatch diagnostics; test SSR output and onRecoverableError"),
                risk("root errors", "src/client.tsx", "const root = createRoot(node);\n", "createRoot",
                        "React 19 changed uncaught/caught root error reporting; verify Error Boundaries, monitoring, and onUncaughtError/onCaughtError callbacks"),
                risk("server rendering", "server/render.tsx", "const html = renderToString(<App />);\n", "renderToString",
                        "React server rendering and Suspense streaming semantics changed; verify runtime, abort, bootstrap, hydration and error handling"),
                risk("batching", "src/measure.tsx", "flushSync(() => setOpen(true));\n", "flushSync",
                        "React 18 automatically batches more updates; re-check ordering and keep flushSync only where synchronous DOM reads require it"),
                risk("useRef type", "src/ref.tsx", "const ref = useRef<Node>();\n", "useRef<Node>()",
                        "React 19 TypeScript requires an argument to useRef; choose an initial value that matches nullability and lifecycle"),
                risk("VFC", "src/View.tsx", "const View: React.VFC<Props> = props => null;\n", "React.VFC",
                        "Removed React TypeScript alias; choose the current explicit type without broadening children or return values"),
                risk("ReactText", "src/types.ts", "import React from 'react';\ntype Legacy = ReactText;\n", "ReactText",
                        "Removed React TypeScript alias; choose the current explicit type without broadening children or return values"),
                risk("ReactElement", "src/types.ts", "let child: React.ReactElement;\n", "React.ReactElement",
                        "React 19 defaults ReactElement props to unknown; add an explicit props type before accessing props"),
                risk("global JSX", "src/types.ts", "type Item = JSX.Element;\n", "JSX.",
                        "React 19 TypeScript uses scoped JSX; augment/import React.JSX instead of the removed global JSX namespace"),
                risk("useReducer", "src/state.ts", "import React from 'react';\nconst [state] = useReducer<State, Action>(reducer, initial);\n", "useReducer<",
                        "React 19 TypeScript changed useReducer inference; prefer inference or provide both state and action tuple types"),
                risk("form state", "src/form.tsx", "const [state, action] = useFormState(save, initial);\n", "useFormState",
                        "React 19 renamed useFormState to useActionState and changed the returned pending state; migrate the binding and callers together"),
                risk("key spread", "src/Row.tsx", "return <Row {...props} />;\n", "{...props}",
                        "React 19 warns when a spread props object contains key; extract key explicitly before spreading this object into JSX"),
                risk("internals", "src/integration.js", "const internals = React.SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED;\n", "SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED",
                        "React 19 removed access to secret internals; replace the integration with a supported public API")
        );
    }

    private static Arguments risk(String name, String path, String before, String token, String message) {
        return Arguments.of(name, path, before, token, message);
    }

    private static String mark(String message, String token) {
        return "~~(" + message + ")~~>" + token;
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.react")
                .scanYamlResources()
                .build();
    }
}
