package com.huawei.clouds.openrewrite.chalk;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks exact non-manifest build owners for Chalk installation and Node runtime selection. */
public final class FindChalk5BuildRisks extends Recipe {
    private static final Pattern INSTALL = Pattern.compile(
            "(?is)^\\s*(?:run\\s+)?(?:(?:[^#]*?)(?:&&|;|\\|\\|)\\s*)?(?:npm\\s+(?:i|install)|yarn\\s+add|pnpm\\s+add)\\b[^#\\r\\n]*\\bchalk(?:@\\S+)?(?:\\s|$).*");
    private static final Pattern NODE_IMAGE = Pattern.compile("(?i)^\\s*FROM\\s+(?:--platform=\\S+\\s+)?node:(\\d+)(?:[.](\\d+))?[^\\s]*(?:\\s+AS\\s+\\S+)?\\s*$");
    private static final Pattern ANY_NODE_IMAGE = Pattern.compile(
            "(?i)^\\s*FROM\\s+(?:--platform=\\S+\\s+)?node:([^\\s]+)(?:\\s+AS\\s+\\S+)?\\s*$");

    @Override
    public String getDisplayName() {
        return "Find Chalk 5 build ownership risks";
    }

    @Override
    public String getDescription() {
        return "Marks non-package.json package-manager commands and unsupported Docker/.nvmrc/.node-version runtime owners line by line.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof PlainText text) || !ChalkSupport.isProjectPath(text.getSourcePath())) return tree;
                String name = text.getSourcePath().getFileName() == null ? "" :
                        text.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
                if (!(name.equals("dockerfile") || name.startsWith("dockerfile.") || name.endsWith(".dockerfile") ||
                      name.endsWith(".sh") || name.equals(".nvmrc") || name.equals(".node-version"))) return tree;
                return markLines(text, name);
            }
        };
    }

    private static PlainText markLines(PlainText text, String name) {
        List<PlainText.Snippet> input = text.getSnippets();
        if (input.isEmpty()) {
            input = new ArrayList<>();
            Matcher matcher = Pattern.compile(".*(?:\\r?\\n|$)").matcher(text.getText());
            while (matcher.find() && !matcher.group().isEmpty()) {
                input.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, matcher.group()));
            }
        }
        boolean changed = false;
        List<PlainText.Snippet> output = new ArrayList<>(input.size());
        for (PlainText.Snippet snippet : input) {
            String line = snippet.getText();
            String trimmed = line.trim();
            String message = null;
            if (!trimmed.startsWith("#") && INSTALL.matcher(line).matches()) {
                message = "Chalk is installed outside package.json; move this exact registry/range/protocol owner into the intended dependency section and regenerate the correct npm/yarn/pnpm lockfile";
            }
            Matcher image = NODE_IMAGE.matcher(trimmed);
            if (message == null && image.matches()) {
                int major = Integer.parseInt(image.group(1));
                int minor = image.group(2) == null ? 0 : Integer.parseInt(image.group(2));
                if (unsupportedNode(major, minor)) {
                    message = FindChalk5ManifestRisks.NODE_MESSAGE;
                }
            }
            if (message == null && ANY_NODE_IMAGE.matcher(trimmed).matches() && !image.matches()) {
                message = "This dynamic Node image tag cannot prove Chalk 5.6.2's ^12.17.0 || ^14.13 || >=16 runtime contract; pin and test an allowed maintained runtime across build and production stages";
            }
            if (message == null && !trimmed.startsWith("#") &&
                    (name.equals(".nvmrc") || name.equals(".node-version")) && oldNode(trimmed)) {
                message = FindChalk5ManifestRisks.NODE_MESSAGE;
            }
            if (message != null && snippet.getMarkers().findFirst(SearchResult.class).isEmpty()) {
                output.add(SearchResult.found(snippet, message));
                changed = true;
            } else output.add(snippet);
        }
        return changed ? text.withText("").withSnippets(output) : text;
    }

    private static boolean oldNode(String value) {
        Matcher matcher = Pattern.compile("v?(\\d+)(?:[.](\\d+))?.*").matcher(value);
        if (!matcher.matches()) return !value.isEmpty();
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return unsupportedNode(major, minor);
    }

    private static boolean unsupportedNode(int major, int minor) {
        return !(major >= 16 || major == 14 && minor >= 13 || major == 12 && minor >= 17);
    }
}
