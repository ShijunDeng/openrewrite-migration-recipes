package com.huawei.clouds.openrewrite.react;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.regex.Pattern;

/** Apply only React 19 source migrations that are one-to-one under narrow lexical guards. */
public final class MigrateDeterministicReactSource extends Recipe {
    private static final String IDENTIFIER = "[A-Za-z_$][\\w$]*";
    private static final Pattern REACT_DOM_IMPORT = Pattern.compile(
            "(?m)^(?<indent>[ \\t]*)import\\s+(?:(?<default>" + IDENTIFIER + ")|\\*\\s+as\\s+(?<namespace>" +
            IDENTIFIER + "))\\s+from\\s*(?<quote>[\"'])react-dom\\k<quote>\\s*;?[ \\t]*$");
    private static final Pattern CLIENT_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+.+?\\s+from\\s*([\"'])react-dom/client\\1");
    private static final Pattern ACT_IMPORT = Pattern.compile(
            "(?m)^(?<indent>[ \\t]*)import\\s*\\{\\s*act\\s*}\\s*from\\s*(?<quote>[\"'])" +
            "react-dom/test-utils\\k<quote>\\s*(?<semi>;?)[ \\t]*$");
    private static final Pattern REACT_ACT_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s*\\{[^}]*\\bact\\b[^}]*}\\s*from\\s*([\"'])react\\1");
    private static final Pattern ACT_DECLARATION = Pattern.compile(
            "\\b(?:const|let|var|function|class)\\s+act\\b");
    private static final Pattern IMPLICIT_REF_ASSIGNMENT = Pattern.compile(
            "\\bref\\s*=\\s*\\{\\s*(?<parameter>" + IDENTIFIER + ")\\s*=>\\s*\\(\\s*" +
            "(?<target>" + IDENTIFIER + "(?:\\s*[.]\\s*" + IDENTIFIER + ")*)\\s*=\\s*" +
            "\\k<parameter>\\s*\\)\\s*}");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic React 19 source constructs";
    }

    @Override
    public String getDescription() {
        return "Migrate a single unambiguous ReactDOM.render or hydrate root, the exact act test-utils import, " +
               "and assignment ref callbacks whose implicit return is invalid in React 19; comments and literals are excluded.";
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
                source = migrateLegacyRoot(source);
                source = migrateActImport(source);
                source = ReactSourceText.replaceCodeMatches(source, IMPLICIT_REF_ASSIGNMENT, match ->
                        "ref={" + match.group("parameter") + " => { " +
                        match.group("target").replaceAll("\\s+", "") + " = " + match.group("parameter") + "; }}");
                return visited.withText(source);
            }
        };
    }

    private static String migrateLegacyRoot(String source) {
        String defaultAlias = ReactSourceText.firstCodeGroup(source, REACT_DOM_IMPORT, "default");
        String namespaceAlias = ReactSourceText.firstCodeGroup(source, REACT_DOM_IMPORT, "namespace");
        String alias = defaultAlias == null ? namespaceAlias : defaultAlias;
        if (alias == null || ReactSourceText.hasCodeMatch(source, CLIENT_IMPORT)) {
            return source;
        }
        Pattern allLegacyCalls = Pattern.compile("\\b" + Pattern.quote(alias) +
                "\\s*[.]\\s*(?:render|hydrate)\\s*\\(");
        if (ReactSourceText.countCodeMatches(source, allLegacyCalls) != 1) {
            return source;
        }

        String container = "(?:document\\.getElementById\\(\\s*[\"'][^\"'\\r\\n]+[\"']\\s*\\)|" +
                           "document\\.querySelector\\(\\s*[\"'][^\"'\\r\\n]+[\"']\\s*\\)|" +
                           IDENTIFIER + ")";
        Pattern render = Pattern.compile("(?m)^(?<indent>[ \\t]*)" + Pattern.quote(alias) +
                "\\s*[.]\\s*render\\s*\\(\\s*(?<element><[^;\\r\\n]+>)\\s*,\\s*" +
                "(?<container>" + container + ")\\s*\\)\\s*;?[ \\t]*$");
        Pattern hydrate = Pattern.compile("(?m)^(?<indent>[ \\t]*)" + Pattern.quote(alias) +
                "\\s*[.]\\s*hydrate\\s*\\(\\s*(?<element><[^;\\r\\n]+>)\\s*,\\s*" +
                "(?<container>" + container + ")\\s*\\)\\s*;?[ \\t]*$");

        if (ReactSourceText.countCodeMatches(source, render) == 1 &&
            !ReactSourceText.hasCodeMatch(source, Pattern.compile("\\b(?:createRoot|root)\\b"))) {
            String withImport = addClientImport(source, "createRoot");
            String newline = source.contains("\r\n") ? "\r\n" : "\n";
            return ReactSourceText.replaceCodeMatches(withImport, render, match ->
                    match.group("indent") + "const root = createRoot(" + match.group("container") + ");" + newline +
                    match.group("indent") + "root.render(" + match.group("element") + ");");
        }
        if (ReactSourceText.countCodeMatches(source, hydrate) == 1 &&
            !ReactSourceText.hasCodeMatch(source, Pattern.compile("\\bhydrateRoot\\b"))) {
            String withImport = addClientImport(source, "hydrateRoot");
            return ReactSourceText.replaceCodeMatches(withImport, hydrate, match ->
                    match.group("indent") + "hydrateRoot(" + match.group("container") + ", " +
                    match.group("element") + ");");
        }
        return source;
    }

    private static String addClientImport(String source, String api) {
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        return ReactSourceText.replaceCodeMatches(source, REACT_DOM_IMPORT, match ->
                match.group("indent") + "import { " + api + " } from " + match.group("quote") +
                "react-dom/client" + match.group("quote") + ";" + newline + match.group());
    }

    private static String migrateActImport(String source) {
        if (ReactSourceText.hasCodeMatch(source, REACT_ACT_IMPORT) ||
            ReactSourceText.hasCodeMatch(source, ACT_DECLARATION)) {
            return source;
        }
        return ReactSourceText.replaceCodeMatches(source, ACT_IMPORT, match ->
                match.group("indent") + "import { act } from " + match.group("quote") + "react" +
                match.group("quote") + match.group("semi"));
    }
}
