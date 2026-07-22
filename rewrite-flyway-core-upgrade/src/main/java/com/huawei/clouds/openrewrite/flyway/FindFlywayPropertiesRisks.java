package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.Set;

/** Marks behavior-sensitive Flyway properties. */
public final class FindFlywayPropertiesRisks extends Recipe {
    private static final Set<String> LEGACY_IGNORE = Set.of(
            "flyway.ignoreMissingMigrations", "flyway.ignoreIgnoredMigrations",
            "flyway.ignorePendingMigrations", "flyway.ignoreFutureMigrations",
            "spring.flyway.ignore-missing-migrations", "spring.flyway.ignore-ignored-migrations",
            "spring.flyway.ignore-pending-migrations", "spring.flyway.ignore-future-migrations"
    );
    private static final Set<String> REMOVED_CHECK_CONNECTION = Set.of(
            "flyway.check.url", "flyway.check.user", "flyway.check.username", "flyway.check.password"
    );

    @Override
    public String getDisplayName() {
        return "Find behavior-sensitive Flyway properties";
    }

    @Override
    public String getDescription() {
        return "Mark removed validation flags, deprecated automatic clean, destructive defaults, removed check credentials, and filesystem-only Java migration discovery.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Properties.File file) || !(tree instanceof SourceFile source) ||
                    !FlywayVersions.isProjectPath(source.getSourcePath())) return tree;
                return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                Properties.Entry e = super.visitEntry(entry, p);
                String key = e.getKey();
                String value = e.getValue().getText().trim();
                if (LEGACY_IGNORE.contains(key)) {
                    return mark(e, "Removed ignore*Migrations booleans must be merged into ignoreMigrationPatterns while preserving the *:future default intentionally");
                }
                if (REMOVED_CHECK_CONNECTION.contains(key)) {
                    return mark(e, "Removed check connection setting; choose a named environment or the intended standard connection without copying secrets into source");
                }
                if (("flyway.cleanOnValidationError".equals(key) || "spring.flyway.clean-on-validation-error".equals(key))) {
                    return mark(e, "cleanOnValidationError is deprecated and can clean the wrong TOML environment; replace it with an explicitly approved validate-then-clean workflow");
                }
                if (("flyway.cleanDisabled".equals(key) || "spring.flyway.clean-disabled".equals(key)) && "false".equalsIgnoreCase(value)) {
                    return mark(e, "clean is explicitly enabled; verify this cannot reach permanent or production schemas");
                }
                if (isTrue(key, value, "flyway.baselineOnMigrate", "spring.flyway.baseline-on-migrate")) {
                    return mark(e, "baselineOnMigrate can accept a non-empty schema without migration history; verify the baseline version and target database");
                }
                if (isTrue(key, value, "flyway.outOfOrder", "spring.flyway.out-of-order")) {
                    return mark(e, "outOfOrder changes migration ordering; compare info/validate results against a production snapshot");
                }
                if (("flyway.locations".equals(key) || "spring.flyway.locations".equals(key)) &&
                    value.contains("filesystem:") && !value.contains("classpath:")) {
                    return mark(e, "filesystem locations discover SQL migrations only; add an explicit classpath location if Java migrations must still be discovered");
                }
                return e;
            }
                }.visitNonNull(file, ctx);
            }
        };
    }

    private static boolean isTrue(String key, String value, String flyway, String spring) {
        return (flyway.equals(key) || spring.equals(key)) && "true".equalsIgnoreCase(value);
    }

    private static Properties.Entry mark(Properties.Entry entry, String message) {
        return entry.getMarkers().findFirst(SearchResult.class).isPresent() ? entry : SearchResult.found(entry, message);
    }
}
