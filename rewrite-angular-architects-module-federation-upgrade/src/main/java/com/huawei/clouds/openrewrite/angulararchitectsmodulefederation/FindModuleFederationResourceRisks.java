package com.huawei.clouds.openrewrite.angulararchitectsmodulefederation;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark lockfiles and deployment resources that are deliberately not rewritten. */
public final class FindModuleFederationResourceRisks extends Recipe {
    private static final Set<String> LOCKS = Set.of(
            "package-lock.json", "npm-shrinkwrap.json", "yarn.lock", "pnpm-lock.yaml", "pnpm-lock.yml", "bun.lock", "bun.lockb"
    );
    private static final Pattern PACKAGE_TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9_.-])" + Pattern.quote(ModuleFederationSupport.PACKAGE) + "(?![A-Za-z0-9_.-])");
    private static final Pattern DEPLOYMENT_TOKEN = Pattern.compile(
            "(?:remoteEntry(?:[.][A-Za-z0-9_-]+)?[.]js|remoteEntry|mf[.]manifest(?:[.]json)?|module-federation[.]manifest(?:[.]json)?)");

    @Override
    public String getDisplayName() {
        return "Find Module Federation lockfile and deployment risks";
    }

    @Override
    public String getDescription() {
        return "Mark selected lockfiles, CI/container/proxy/service-worker deployment resources, and remote entry cache/CORS/CSP ownership without rewriting generated artifacts.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !ModuleFederationSupport.projectPath(source.getSourcePath())) {
                    return tree;
                }
                String text = source.printAll();
                if (!relevant(source.getSourcePath(), text)) return tree;
                String message = message(source.getSourcePath());
                boolean lock = lock(source.getSourcePath());
                if (tree instanceof Json.Document document) {
                    return markJson(document, ctx, message, lock);
                }
                if (tree instanceof Yaml.Documents documents) {
                    return markYaml(documents, ctx, message, lock);
                }
                if (tree instanceof PlainText plainText) return markPlainText(plainText, message, lock);
                return tree;
            }
        };
    }

    private static Json.Document markJson(Json.Document document, ExecutionContext ctx, String message, boolean lock) {
        JsonIsoVisitor<ExecutionContext> visitor = new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext executionContext) {
                Json.Member visited = super.visitMember(member, executionContext);
                String key = ModuleFederationSupport.key(visited);
                String value = ModuleFederationSupport.string(visited);
                if (owned(key, lock)) return ModuleFederationSupport.mark(visited, message);
                if (owned(value, lock) && visited.getValue() instanceof Json.Literal literal) {
                    return visited.withValue(ModuleFederationSupport.mark(literal, message));
                }
                return visited;
            }
        };
        return visitor.visitDocument(document, ctx);
    }

    private static Yaml.Documents markYaml(Yaml.Documents documents, ExecutionContext ctx,
                                           String message, boolean lock) {
        YamlIsoVisitor<ExecutionContext> visitor = new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext executionContext) {
                Yaml.Scalar visited = super.visitScalar(scalar, executionContext);
                return owned(visited.getValue(), lock) ? ModuleFederationSupport.mark(visited, message) : visited;
            }
        };
        return visitor.visitDocuments(documents, ctx);
    }

    private static PlainText markPlainText(PlainText text, String message, boolean lock) {
        Pattern pattern = lock ? PACKAGE_TOKEN : DEPLOYMENT_TOKEN;
        Matcher matcher = pattern.matcher(text.getText());
        List<Match> matches = new ArrayList<>();
        while (matcher.find()) matches.add(new Match(matcher.start(), matcher.end()));
        matches.sort(Comparator.comparingInt(Match::start));
        if (matches.isEmpty()) return text;
        String source = text.getText();
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : matches) {
            if (cursor < match.start) {
                snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                        source.substring(cursor, match.start)));
            }
            snippets.add(SearchResult.found(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start, match.end)), message));
            cursor = match.end;
        }
        if (cursor < source.length()) {
            snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        }
        return text.withText("").withSnippets(snippets);
    }

    private static boolean owned(String value, boolean lock) {
        if (value == null) return false;
        return (lock ? PACKAGE_TOKEN : DEPLOYMENT_TOKEN).matcher(value).find();
    }

    private static boolean lock(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return LOCKS.contains(name);
    }

    private static boolean relevant(Path path, String text) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (LOCKS.contains(name)) return ModuleFederationSupport.containsPackageReference(text);
        boolean deployment = name.startsWith("dockerfile") || name.contains("nginx") || name.contains("apache") ||
                             name.contains("service-worker") || name.equals("ngsw-config.json") ||
                             name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".html");
        return deployment && (text.contains("remoteEntry") || text.contains("mf.manifest") ||
                              ModuleFederationSupport.containsPackageReference(text));
    }

    private static String message(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (LOCKS.contains(name)) {
            return "Regenerate exactly this selected lockfile after all Angular 20, federation runtime, enhanced/runtime-core and ngx-build-plus owners are aligned; verify no stale 15/16 runtime remains";
        }
        return "Review this deployment resource for remoteEntry/manifest output location, immutable versus no-cache policy, MIME type, CORS/CSP/SRI, base path, health checks and rollback compatibility";
    }

    private record Match(int start, int end) {
    }
}
