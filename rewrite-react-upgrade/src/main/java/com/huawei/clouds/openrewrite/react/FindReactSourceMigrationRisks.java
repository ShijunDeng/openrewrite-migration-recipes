package com.huawei.clouds.openrewrite.react;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Mark React 18/19 source migrations that require framework or application context. */
public final class FindReactSourceMigrationRisks extends Recipe {
    private static final String IDENTIFIER = "[A-Za-z_$][\\w$]*";
    private static final Pattern DEFAULT_IMPORT = Pattern.compile(
            "\\bimport\\s+(?<alias>" + IDENTIFIER + ")\\s+from\\s*([\"'])react-dom\\2");
    private static final Pattern NAMESPACE_IMPORT = Pattern.compile(
            "\\bimport\\s*\\*\\s*as\\s*(?<alias>" + IDENTIFIER + ")\\s*from\\s*([\"'])react-dom\\2");
    private static final Pattern REQUIRE = Pattern.compile(
            "\\b(?:const|let|var)\\s+(?<alias>" + IDENTIFIER + ")\\s*=\\s*require\\(\\s*([\"'])react-dom\\2\\s*\\)");
    private static final Pattern REACT_EVIDENCE = Pattern.compile(
            "(?m)\\bimport\\b[^;\\r\\n]*?(?:from\\s*)?([\"'])react(?:-dom|-test-renderer)?(?:/[^\"']+)?\\1|" +
            "\\brequire\\s*\\(\\s*([\"'])react(?:-dom|-test-renderer)?(?:/[^\"']+)?\\2\\s*\\)|" +
            "\\bReact(?:DOM)?\\s*[.]");

    @Override
    public String getDisplayName() {
        return "Find React 18 and 19 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Precisely mark removed React APIs and context-dependent root, ref, context, testing, StrictMode, " +
               "Suspense, hydration, batching, server rendering and TypeScript changes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!ReactSourceText.isSupported(visited)) {
                    return visited;
                }
                String source = visited.getText();
                Set<String> domAliases = new LinkedHashSet<>();
                domAliases.add("ReactDOM");
                ReactSourceText.collectCodeGroup(source, DEFAULT_IMPORT, "alias", domAliases);
                ReactSourceText.collectCodeGroup(source, NAMESPACE_IMPORT, "alias", domAliases);
                ReactSourceText.collectCodeGroup(source, REQUIRE, "alias", domAliases);
                String path = visited.getSourcePath().toString().toLowerCase();
                boolean reactEvidence = path.endsWith(".jsx") || path.endsWith(".tsx") ||
                                        ReactSourceText.hasCodeMatch(source, REACT_EVIDENCE);
                return ReactSourceText.markMatches(visited, risks(domAliases, reactEvidence));
            }
        };
    }

    private static List<ReactSourceText.RiskPattern> risks(Set<String> domAliases, boolean reactEvidence) {
        List<ReactSourceText.RiskPattern> risks = new ArrayList<>();
        String aliases = domAliases.stream().map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right).orElse("ReactDOM");
        risks.add(risk(Pattern.compile("\\b(?:" + aliases + ")\\s*[.]\\s*(?:render|hydrate)(?=\\s*\\()"),
                "React 19 removed ReactDOM.render/hydrate; migrate the root manually when callbacks, multiple roots or dynamic containers prevent the safe transform"));
        risks.add(risk(Pattern.compile("\\bimport\\s*\\{[^}]*\\b(?:render|hydrate|unmountComponentAtNode)\\b[^}]*}\\s*from\\s*([\"'])react-dom\\1"),
                "React 19 removed legacy named react-dom root APIs; migrate each binding to react-dom/client while preserving root ownership"));
        risks.add(risk(Pattern.compile("\\b(?:" + aliases + ")\\s*[.]\\s*unmountComponentAtNode(?=\\s*\\()"),
                "React 19 removed unmountComponentAtNode; retain the createRoot handle and call root.unmount()"));
        risks.add(risk(Pattern.compile("\\b(?:" + aliases + ")\\s*[.]\\s*findDOMNode(?=\\s*\\()"),
                "React 19 removed findDOMNode; attach a DOM ref to the owned element without crossing component boundaries"));
        risks.add(risk(Pattern.compile("(?<!React[.])\\bJSX\\s*[.]"),
                "React 19 TypeScript uses scoped JSX; augment/import React.JSX instead of the removed global JSX namespace"));
        if (!reactEvidence) {
            return risks;
        }
        risks.add(risk(Pattern.compile("(?<![.\\w$])findDOMNode(?=\\s*\\()"),
                "React 19 removed findDOMNode; attach a DOM ref to the owned element without crossing component boundaries"));
        risks.add(risk(Pattern.compile("\\b(?:React\\s*[.]\\s*)?createFactory(?=\\s*\\()"),
                "React 19 removed createFactory; replace it with JSX while preserving keys, children and dynamic component selection"));
        risks.add(risk(Pattern.compile("\\breturn\\s*\\{\\s*(?:render|componentWillMount)\\s*(?:\\(|:)"),
                "React 19 removed module-pattern component factories; return JSX from a normal function component and verify lifecycle behavior"));
        risks.add(risk(Pattern.compile("\\b" + IDENTIFIER + "\\s*[.]\\s*propTypes\\s*="),
                "React 19 ignores function propTypes; decide whether this component needs TypeScript/another runtime validator (class propTypes differ)"));
        risks.add(risk(Pattern.compile("\\b" + IDENTIFIER + "\\s*[.]\\s*defaultProps\\s*="),
                "React 19 removes function defaultProps; use parameter defaults only after verifying undefined, wrapper and class-component semantics"));
        risks.add(risk(Pattern.compile("\\b" + IDENTIFIER + "\\s*[.]\\s*(?:contextTypes|childContextTypes)\\s*="),
                "React 19 removed legacy context; migrate the full provider/consumer chain to createContext and Context.Provider"));
        risks.add(risk(Pattern.compile("\\bgetChildContext\\s*\\("),
                "React 19 removed legacy getChildContext; migrate the full provider/consumer chain to modern context"));
        risks.add(risk(Pattern.compile("\\bref\\s*=\\s*[\"'][^\"'\\r\\n]+[\"']"),
                "React 19 removed string refs; replace with useRef/createRef or a callback ref after checking ownership"));
        risks.add(risk(Pattern.compile("\\b(?:element|reactElement|child)\\s*[.]\\s*ref\\b"),
                "React 19 treats ref as a regular prop and deprecates element.ref; read element.props.ref and verify library compatibility"));
        risks.add(risk(Pattern.compile("\\bref\\s*=\\s*\\{\\s*" + IDENTIFIER + "\\s*=>\\s*(?!\\{)"),
                "React 19 allows ref cleanup functions; make implicit returns explicit and test StrictMode attach/detach behavior"));
        risks.add(risk(Pattern.compile("\\b(?:import|export)\\s+.*?from\\s*([\"'])react-dom/test-utils\\1"),
                "React 19 removed react-dom/test-utils except act; import act from react and replace remaining test utilities"));
        risks.add(risk(Pattern.compile("\\b(?:import|require)\\s*\\(\\s*([\"'])react-dom/test-utils\\1\\s*\\)"),
                "React 19 removed react-dom/test-utils except act; import act from react and replace remaining test utilities"));
        risks.add(risk(Pattern.compile("\\b(?:import|require\\()\\s*.*?([\"'])react-test-renderer(?:/shallow)?\\1"),
                "react-test-renderer is deprecated and may use concurrent behavior; migrate assertions to a maintained DOM/native testing library"));
        risks.add(risk(Pattern.compile("<(?:React\\s*[.]\\s*)?StrictMode\\b"),
                "React 18/19 StrictMode replays effects and ref callbacks in development; verify cleanup and reusable state"));
        risks.add(risk(Pattern.compile("\\b(?:React\\s*[.]\\s*)?(?:useEffect|useLayoutEffect)(?=\\s*\\()"),
                "React 18 StrictMode exposes non-idempotent effects; verify cleanup, subscriptions and duplicate external writes"));
        risks.add(risk(Pattern.compile("<(?:React\\s*[.]\\s*)?Suspense\\b"),
                "React 18/19 changed Suspense commit, retry and sibling-prewarming behavior; test fallback timing, effects and reveal order"));
        risks.add(risk(Pattern.compile("\\bhydrateRoot(?=\\s*\\()"),
                "React hydration now reports recoverable errors differently and React 19 has stricter mismatch diagnostics; test SSR output and onRecoverableError"));
        risks.add(risk(Pattern.compile("\\bcreateRoot(?=\\s*\\()"),
                "React 19 changed uncaught/caught root error reporting; verify Error Boundaries, monitoring, and onUncaughtError/onCaughtError callbacks"));
        risks.add(risk(Pattern.compile("\\b(?:renderToString|renderToNodeStream|renderToPipeableStream|renderToReadableStream|prerender|resume)(?=\\s*\\()"),
                "React server rendering and Suspense streaming semantics changed; verify runtime, abort, bootstrap, hydration and error handling"));
        risks.add(risk(Pattern.compile("\\b(?:unstable_batchedUpdates|flushSync)(?=\\s*\\()"),
                "React 18 automatically batches more updates; re-check ordering and keep flushSync only where synchronous DOM reads require it"));
        risks.add(risk(Pattern.compile("\\buseRef\\s*<[^>]+>\\s*\\(\\s*\\)|\\buseRef\\s*\\(\\s*\\)"),
                "React 19 TypeScript requires an argument to useRef; choose an initial value that matches nullability and lifecycle"));
        risks.add(risk(Pattern.compile("\\b(?:React[.])?(?:ReactText|ReactChild|ReactNodeArray|VFC)\\b"),
                "Removed React TypeScript alias; choose the current explicit type without broadening children or return values"));
        risks.add(risk(Pattern.compile("\\b(?:React[.])?ReactElement\\b(?!\\s*<)"),
                "React 19 defaults ReactElement props to unknown; add an explicit props type before accessing props"));
        risks.add(risk(Pattern.compile("\\buseReducer\\s*<"),
                "React 19 TypeScript changed useReducer inference; prefer inference or provide both state and action tuple types"));
        risks.add(risk(Pattern.compile("\\buseFormState\\b"),
                "React 19 renamed useFormState to useActionState and changed the returned pending state; migrate the binding and callers together"));
        risks.add(risk(Pattern.compile("\\{\\s*[.]{3}\\s*" + IDENTIFIER + "\\s*}"),
                "React 19 warns when a spread props object contains key; extract key explicitly before spreading this object into JSX"));
        risks.add(risk(Pattern.compile("\\bSECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED\\b"),
                "React 19 removed access to secret internals; replace the integration with a supported public API"));
        return risks;
    }

    private static ReactSourceText.RiskPattern risk(Pattern pattern, String message) {
        return new ReactSourceText.RiskPattern(pattern, message);
    }
}
