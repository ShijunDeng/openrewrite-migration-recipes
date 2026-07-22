package com.huawei.clouds.openrewrite.fastjson;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.dependencies.AddDependency;

import java.util.List;

/**
 * Migrates the commonly used Fastjson 1.x APIs to Jackson 2.x.
 */
public final class MigrateFastjsonToJackson extends Recipe {
    static final String JACKSON_VERSION = "2.22.x";

    @Override
    public String getDisplayName() {
        return "Migrate Fastjson 1.x to Jackson";
    }

    @Override
    public String getDescription() {
        return "Migrate common Fastjson 1.x serialization, deserialization, container, and annotation APIs " +
               "to Jackson, add jackson-databind, and remove Fastjson when no Fastjson types remain.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                new AddDependency(
                        "com.fasterxml.jackson.core",
                        "jackson-databind",
                        JACKSON_VERSION,
                        null,
                        "com.alibaba.fastjson..*",
                        null,
                        "com.fasterxml.jackson*",
                        null,
                        null,
                        null,
                        true,
                        null,
                        null,
                        true
                ),
                new GenerateJacksonJsonHelper(),
                new MigrateJsonFieldAnnotation(),
                new MigrateFastjsonApi(),
                new ChangeType(
                        "com.alibaba.fastjson.TypeReference",
                        "com.fasterxml.jackson.core.type.TypeReference",
                        false
                ),
                new ChangeType(
                        "com.alibaba.fastjson.JSONObject",
                        "com.fasterxml.jackson.databind.node.ObjectNode",
                        false
                ),
                new ChangeType(
                        "com.alibaba.fastjson.JSONArray",
                        "com.fasterxml.jackson.databind.node.ArrayNode",
                        false
                ),
                new RemoveFastjsonDependency()
        );
    }
}
