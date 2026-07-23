package com.huawei.clouds.openrewrite.log4jcore;

import org.openrewrite.Recipe;

import java.util.List;

/** Deterministic source and configuration migrations for Log4j Core 2.25. */
public final class MigrateLog4jCore25 extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Apache Log4j Core 2.25 APIs and patterns";
    }

    @Override
    public String getDescription() {
        return "Use supported LoggerConfig filter setters and remove the obsolete nolookups Pattern Layout option without guessing runtime policy.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(new MigrateLoggerConfigFilterBuilders(), new MigrateRemovedNoLookupsOption());
    }
}
