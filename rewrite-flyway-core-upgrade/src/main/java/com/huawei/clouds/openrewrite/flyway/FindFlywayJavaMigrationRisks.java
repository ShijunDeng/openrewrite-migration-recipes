package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks Flyway Java and SPI changes that need application intent. */
public final class FindFlywayJavaMigrationRisks extends Recipe {
    private static final String FLYWAY = "org.flywaydb.core.Flyway";
    private static final Set<String> LEGACY_JAVA_MIGRATIONS = Set.of(
            "org.flywaydb.core.api.migration.jdbc.JdbcMigration",
            "org.flywaydb.core.api.migration.spring.SpringJdbcMigration",
            "org.flywaydb.core.api.migration.jdbc.BaseJdbcMigration",
            "org.flywaydb.core.api.migration.spring.BaseSpringJdbcMigration"
    );

    @Override
    public String getDisplayName() {
        return "Find behavior-sensitive Flyway Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark mutable Flyway configuration, legacy Java migration interfaces, evolving SPI types, destructive operations, and wildcard locations for review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(MigrateFlywayJavaApi.projectSource(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (implementsAny(c, LEGACY_JAVA_MIGRATIONS)) {
                    return mark(c, "Legacy Jdbc/SpringJdbc migration must move to BaseJavaMigration and migrate(Context); adapt Connection/JdbcTemplate access explicitly");
                }
                if (implementsType(c, "org.flywaydb.core.api.migration.JavaMigration") &&
                    !TypeUtils.isAssignableTo("org.flywaydb.core.api.migration.BaseJavaMigration", c.getType())) {
                    return mark(c, "Direct JavaMigration implementations track an evolving interface; prefer BaseJavaMigration and verify getResolvedMigration/transaction behavior");
                }
                return c;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (TypeUtils.isOfClassType(n.getType(), FLYWAY) &&
                    (n.getArguments().isEmpty() || n.getArguments().stream().allMatch(J.Empty.class::isInstance))) {
                    return mark(n, "Mutable new Flyway() construction was removed; collect all setters into Flyway.configure() before load()");
                }
                if (TypeUtils.isOfClassType(n.getType(), "org.flywaydb.core.api.Location") &&
                    n.getArguments().size() == 1 && n.getArguments().get(0) instanceof J.Literal literal &&
                    literal.getValue() instanceof String value && (value.contains("*") || value.contains("?"))) {
                    return mark(n, "Wildcard Location construction requires fromWildcardPath parser semantics; do not replace it with fromPath mechanically");
                }
                return n;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (methodOn(m, FLYWAY) && (m.getSimpleName().startsWith("set") ||
                    "configure".equals(m.getSimpleName()) && !m.getMethodType().hasFlags(Flag.Static))) {
                    return mark(m, "Mutable Flyway configuration was removed; preserve setter order and values in one FluentConfiguration chain before load()");
                }
                if (methodOn(m, FLYWAY) && "clean".equals(m.getSimpleName())) {
                    return mark(m, "Flyway clean destroys objects in configured schemas; verify environment, cleanDisabled, credentials, and explicit approval");
                }
                if (methodOn(m, FLYWAY) && "repair".equals(m.getSimpleName())) {
                    return mark(m, "Flyway repair mutates schema-history checksums and states; compare info/validate output and use the same locations as migrate");
                }
                if (isLegacyIgnoreMethod(m)) {
                    return mark(m, "Legacy ignore*Migrations booleans were replaced by combined ignoreMigrationPatterns; merge every flag and the *:future default intentionally");
                }
                return m;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                if (getCursor().getParentTreeCursor().getValue() instanceof J.VariableDeclarations.NamedVariable variable &&
                    variable.getName().getId().equals(identifier.getId())) return i;
                if (getCursor().getParentTreeCursor().getValue() instanceof J.MethodInvocation invocation &&
                    invocation.getName().getId().equals(identifier.getId())) return i;
                if (TypeUtils.isOfClassType(i.getType(), "org.flywaydb.core.extensibility.ApiExtension")) {
                    return mark(i, "ApiExtension was replaced by ConfigurationExtension and is now obtained from PluginRegister; migrate the access path, not only the type name");
                }
                if (TypeUtils.isOfClassType(i.getType(), "org.flywaydb.core.api.ErrorCode")) {
                    return mark(i, "ErrorCode changed from enum to interface; built-in constants moved to CoreErrorCode while custom plugins may implement ErrorCode");
                }
                return i;
            }
        });
    }

    private static boolean methodOn(J.MethodInvocation method, String owner) {
        return method.getMethodType() != null && TypeUtils.isAssignableTo(owner, method.getMethodType().getDeclaringType());
    }

    private static boolean isLegacyIgnoreMethod(J.MethodInvocation method) {
        return method.getMethodType() != null &&
               TypeUtils.isAssignableTo("org.flywaydb.core.api.configuration.FluentConfiguration", method.getMethodType().getDeclaringType()) &&
               Set.of("ignoreMissingMigrations", "ignoreIgnoredMigrations", "ignorePendingMigrations", "ignoreFutureMigrations")
                       .contains(method.getSimpleName());
    }

    private static boolean implementsAny(J.ClassDeclaration c, Set<String> types) {
        return types.stream().anyMatch(type -> implementsType(c, type));
    }

    private static boolean implementsType(J.ClassDeclaration c, String type) {
        return c.getImplements() != null && c.getImplements().stream()
                .anyMatch(implemented -> TypeUtils.isAssignableTo(type, implemented.getType()));
    }

    private static <T extends org.openrewrite.Tree> T mark(T tree, String message) {
        return tree.getMarkers().findFirst(SearchResult.class).isPresent() ? tree : SearchResult.found(tree, message);
    }
}
