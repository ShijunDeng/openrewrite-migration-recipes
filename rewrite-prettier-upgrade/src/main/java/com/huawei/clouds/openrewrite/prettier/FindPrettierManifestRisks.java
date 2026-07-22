package com.huawei.clouds.openrewrite.prettier;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark package ownership, Node baseline, plugins, types, and removed CLI flags. */
public final class FindPrettierManifestRisks extends Recipe {
    private static final Pattern LEADING_MAJOR = Pattern.compile("(?:>=|[~^]?)(\\d+)(?:[.]\\d+)?(?:[.]\\d+)?(?:[.]x)?");
    private static final String OWNER =
            "This direct Prettier declaration is not the exact workbook target after strict AUTO; resolve ranges, protocols, forks, catalogs, aliases, and unlisted versions deliberately";
    private static final String NODE =
            "Prettier 3.6.2's published package requires Node >=14; align local, CI, editor, hook, container, and package-manager runtimes before rollout";
    private static final String CLI =
            "This Prettier command uses a removed/renamed 3.x CLI or physical-bin option; replace --loglevel with --log-level, remove plugin search flags in favor of explicit --plugin, and use the published bin entry";

    @Override
    public String getDisplayName() {
        return "Find Prettier 3.6.2 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unresolved Prettier owners, overrides, Node baselines, obsolete types, plugin alignment, and removed CLI options in package.json.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean active;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean previous = active;
                active = PrettierSupport.packageJson(document.getSourcePath()) &&
                         PrettierSupport.containsPackageReference(document.printAll());
                Json.Document visited = active ? super.visitDocument(document, ctx) : document;
                active = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!active) return visited;
                String key = PrettierSupport.key(visited);
                String value = PrettierSupport.string(visited);
                String section = PrettierSupport.directSection(getCursor());
                if (PrettierSupport.PACKAGE.equals(key)) {
                    if (PrettierSupport.overrideOwner(getCursor())) {
                        return PrettierSupport.mark(visited,
                                "This override/resolution independently owns Prettier; align the actual package-manager graph and regenerate exactly one selected lockfile");
                    }
                    if (PrettierSupport.SECTIONS.contains(section) && !PrettierSupport.target(value)) {
                        return PrettierSupport.mark(visited, OWNER);
                    }
                }
                if ("engines".equals(section) && "node".equals(key) && !clearlyNode14OrNewer(value)) {
                    return PrettierSupport.mark(visited, NODE);
                }
                if ("scripts".equals(section) && removedCli(value)) return PrettierSupport.mark(visited, CLI);
                if (PrettierSupport.SECTIONS.contains(section) && "@types/prettier".equals(key)) {
                    return PrettierSupport.mark(visited,
                            "Prettier 3 bundles its own TypeScript declarations; remove or intentionally pin @types/prettier only after validating compiler/moduleResolution consumers");
                }
                if (PrettierSupport.SECTIONS.contains(section) &&
                    (key.startsWith("prettier-plugin-") || key.contains("/prettier-plugin-"))) {
                    return PrettierSupport.mark(visited,
                            "Validate this Prettier plugin against the 3.x async parser/embed contract, ESM loading, explicit plugin paths, and output snapshots");
                }
                return visited;
            }
        };
    }

    private static boolean clearlyNode14OrNewer(String declaration) {
        if (declaration == null || declaration.contains("||") || declaration.contains("<") && !declaration.startsWith(">=")) {
            return false;
        }
        Matcher matcher = LEADING_MAJOR.matcher(declaration.trim());
        return matcher.matches() && Integer.parseInt(matcher.group(1)) >= 14;
    }

    private static boolean removedCli(String command) {
        if (command == null || !PrettierSupport.containsPackageReference(command)) return false;
        return command.matches("(?s).*(?:--loglevel(?:=|\\s)|--plugin-search-dir(?:=|\\s)|--no-plugin-search\\b|bin-prettier[.]js).*" );
    }
}
