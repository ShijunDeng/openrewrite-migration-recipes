package com.huawei.clouds.openrewrite.reactresizable;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Mark lock and CSS contracts without editing generated dependency state or layout behavior. */
public final class FindReactResizableResourceRisks extends Recipe {
    private static final Set<String> LOCKS = Set.of(
            "package-lock.json", "npm-shrinkwrap.json", "yarn.lock", "pnpm-lock.yaml", "pnpm-lock.yml",
            "bun.lock", "bun.lockb"
    );

    @Override
    public String getDisplayName() {
        return "Find react-resizable lockfile and CSS risks";
    }

    @Override
    public String getDescription() {
        return "Mark lockfiles that retain react-resizable/react-draggable graphs and application CSS that " +
               "overrides the handle placement/hit-target contract.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    !ReactResizableSupport.isProjectPath(source.getSourcePath())) return tree;
                Path path = source.getSourcePath();
                String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
                String content = source.printAll();
                if (LOCKS.contains(name) && (content.contains("react-resizable") || content.contains("react-draggable"))) {
                    if (tree instanceof Json.Document document) {
                        return new JsonIsoVisitor<ExecutionContext>() {
                            @Override
                            public Json.Member visitMember(Json.Member member, ExecutionContext ec) {
                                Json.Member visited = super.visitMember(member, ec);
                                String key = ReactResizableSupport.jsonKey(visited);
                                String value = ReactResizableSupport.jsonString(visited);
                                return dependencyText(key) || dependencyText(value)
                                        ? mark(visited, lockMessage()) : visited;
                            }
                        }.visitNonNull(document, ctx);
                    }
                    if (tree instanceof Yaml.Documents documents) {
                        return new YamlIsoVisitor<ExecutionContext>() {
                            @Override
                            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ec) {
                                Yaml.Scalar visited = super.visitScalar(scalar, ec);
                                return visited.getValue().contains("react-resizable") ||
                                       visited.getValue().contains("react-draggable")
                                        ? mark(visited, lockMessage()) : visited;
                            }
                        }.visitNonNull(documents, ctx);
                    }
                    return mark(tree, lockMessage());
                }
                if (name.matches(".*\\.(?:css|scss|sass|less)$") &&
                    content.matches("(?s).*\\.react-resizable(?:-handle(?:-[nsew]{1,2})?)?\\b.*")) {
                    return mark(tree, "Application CSS overrides react-resizable geometry; compare against target css/styles.css and regression-test every enabled handle's visibility, cursor, stacking, transforms, hit area, RTL, zoom, and keyboard/focus accessibility");
                }
                return tree;
            }
        };
    }

    private static String lockMessage() {
        return "Regenerate this lockfile with one package manager after the manifest upgrade; verify react-resizable 3.1.3, react-draggable ^4.5.0, integrity/resolution metadata, workspace hoisting, overrides, and absence of unintended 1.x duplicates";
    }

    private static boolean dependencyText(String value) {
        return value != null && (value.contains("react-resizable") || value.contains("react-draggable"));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
