package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

import java.util.regex.Pattern;

/** Marks SQL migration files that violate Flyway's default naming convention. */
public final class FindFlywayMigrationFileRisks extends Recipe {
    private static final Pattern VERSIONED = Pattern.compile("^[VU][0-9][0-9A-Za-z_.-]*__.+\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATABLE = Pattern.compile("^R__.+\\.sql$", Pattern.CASE_INSENSITIVE);

    @Override
    public String getDisplayName() {
        return "Find invalid default Flyway SQL migration names";
    }

    @Override
    public String getDescription() {
        return "Mark V, U, and R SQL migration files that do not satisfy the default double-underscore naming convention.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                if (!FlywayVersions.isProjectPath(source.getSourcePath())) return tree;
                String path = source.getSourcePath().toString().replace('\\', '/');
                if (!(path.startsWith("db/migration/") || path.contains("/db/migration/"))) {
                    return tree;
                }
                String name = source.getSourcePath().getFileName().toString();
                if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".sql") || name.isEmpty()) {
                    return tree;
                }
                char prefix = Character.toUpperCase(name.charAt(0));
                boolean candidate = prefix == 'V' || prefix == 'U' || prefix == 'R';
                boolean valid = prefix == 'R' ? REPEATABLE.matcher(name).matches() : VERSIONED.matcher(name).matches();
                return candidate && !valid && source.getMarkers().findFirst(SearchResult.class).isEmpty() ? SearchResult.found(source,
                        "Filename violates Flyway's default migration naming; confirm custom prefixes/separator or rename before enabling validateMigrationNaming") : tree;
            }
        };
    }
}
