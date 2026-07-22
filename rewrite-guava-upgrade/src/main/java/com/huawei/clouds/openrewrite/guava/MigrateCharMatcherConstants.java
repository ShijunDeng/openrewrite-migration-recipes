package com.huawei.clouds.openrewrite.guava;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeStaticFieldToMethod;

import java.util.List;

/** Replaces the CharMatcher constants removed in Guava 26 with their factory methods. */
public final class MigrateCharMatcherConstants extends Recipe {
    private static final String CHAR_MATCHER = "com.google.common.base.CharMatcher";

    @Override
    public String getDisplayName() {
        return "Migrate removed Guava CharMatcher constants";
    }

    @Override
    public String getDescription() {
        return "Replace the fourteen deprecated CharMatcher static fields removed in Guava 26 with their " +
               "behaviorally equivalent zero-argument factory methods, including static imports.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                change("WHITESPACE", "whitespace"),
                change("BREAKING_WHITESPACE", "breakingWhitespace"),
                change("ASCII", "ascii"),
                change("DIGIT", "digit"),
                change("JAVA_DIGIT", "javaDigit"),
                change("JAVA_LETTER", "javaLetter"),
                change("JAVA_LETTER_OR_DIGIT", "javaLetterOrDigit"),
                change("JAVA_UPPER_CASE", "javaUpperCase"),
                change("JAVA_LOWER_CASE", "javaLowerCase"),
                change("JAVA_ISO_CONTROL", "javaIsoControl"),
                change("INVISIBLE", "invisible"),
                change("SINGLE_WIDTH", "singleWidth"),
                change("ANY", "any"),
                change("NONE", "none")
        );
    }

    private static Recipe change(String field, String method) {
        return new ChangeStaticFieldToMethod(CHAR_MATCHER, field, null, null, method);
    }
}
