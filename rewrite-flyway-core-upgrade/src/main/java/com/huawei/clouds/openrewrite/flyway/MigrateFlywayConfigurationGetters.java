package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;

import java.util.List;

/** Migrates deterministic Configuration boolean getters only in maintained project sources. */
public final class MigrateFlywayConfigurationGetters extends Recipe {
    @Override
    public String getDisplayName() { return "Migrate Flyway Configuration boolean getters"; }

    @Override
    public String getDescription() {
        return "Rename the four removed Configuration get-style boolean getters while excluding generated and install trees.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectOnly(new ChangeMethodName("org.flywaydb.core.api.configuration.Configuration getFailOnMissingTarget()", "isFailOnMissingTarget", null, null)),
                projectOnly(new ChangeMethodName("org.flywaydb.core.api.configuration.Configuration getDetectEncoding()", "isDetectEncoding", null, null)),
                projectOnly(new ChangeMethodName("org.flywaydb.core.api.configuration.Configuration getCreateSchemas()", "isCreateSchemas", null, null)),
                projectOnly(new ChangeMethodName("org.flywaydb.core.api.configuration.Configuration getFailOnMissingLocations()", "isFailOnMissingLocations", null, null))
        );
    }

    private static Recipe projectOnly(Recipe delegate) {
        return new Recipe() {
            @Override public String getDisplayName() { return delegate.getDisplayName(); }
            @Override public String getDescription() { return delegate.getDescription(); }
            @Override public TreeVisitor<?, ExecutionContext> getVisitor() {
                return Preconditions.check(MigrateFlywayJavaApi.projectSource(), delegate.getVisitor());
            }
        };
    }
}
