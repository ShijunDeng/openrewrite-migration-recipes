package com.huawei.clouds.openrewrite.uuid;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rewrite legacy one-function uuid ESM subpaths to v13 root named exports. */
public final class MigrateLegacyUuidImports extends Recipe {
    private static final Pattern DEFAULT_SUBPATH_IMPORT = Pattern.compile(
            "\\bimport(?<space1>[ \\t]+)(?<local>[A-Za-z_$][\\w$]*)(?<space2>[ \\t]+)" +
            "from(?<space3>[ \\t]*)(?<quote>[\"'])uuid/(?<function>v[1345])(?:[.]js)?\\k<quote>");
    private static final Pattern DEFAULT_SUBPATH_EXPORT = Pattern.compile(
            "\\bexport(?<space1>[ \\t]+)\\{(?<space2>[ \\t]*)default(?<space3>[ \\t]+)as" +
            "(?<space4>[ \\t]+)(?<local>[A-Za-z_$][\\w$]*)(?<space5>[ \\t]*)}" +
            "(?<space6>[ \\t]+)from(?<space7>[ \\t]*)(?<quote>[\"'])uuid/" +
            "(?<function>v[1345])(?:[.]js)?\\k<quote>");

    @Override
    public String getDisplayName() {
        return "Migrate legacy uuid ESM subpath imports";
    }

    @Override
    public String getDescription() {
        return "Convert unambiguous default imports and re-exports of uuid/v1, v3, v4, or v5 to root named " +
               "exports while preserving aliases, quotes, spacing, comments, strings, templates, and regex literals.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!UuidSourceText.isSupported(visited)) {
                    return visited;
                }
                String migrated = UuidSourceText.replaceCodeMatches(
                        visited.getText(), DEFAULT_SUBPATH_IMPORT, MigrateLegacyUuidImports::replaceImport);
                migrated = UuidSourceText.replaceCodeMatches(
                        migrated, DEFAULT_SUBPATH_EXPORT, MigrateLegacyUuidImports::replaceExport);
                return visited.withText(migrated);
            }
        };
    }

    private static String replaceImport(Matcher matcher) {
        String function = matcher.group("function");
        String local = matcher.group("local");
        String binding = function.equals(local) ? function : function + " as " + local;
        return "import" + matcher.group("space1") + "{ " + binding + " }" + matcher.group("space2") +
               "from" + matcher.group("space3") + matcher.group("quote") + "uuid" + matcher.group("quote");
    }

    private static String replaceExport(Matcher matcher) {
        return "export" + matcher.group("space1") + "{" + matcher.group("space2") +
               matcher.group("function") + matcher.group("space3") + "as" + matcher.group("space4") +
               matcher.group("local") + matcher.group("space5") + "}" + matcher.group("space6") +
               "from" + matcher.group("space7") + matcher.group("quote") + "uuid" + matcher.group("quote");
    }
}
