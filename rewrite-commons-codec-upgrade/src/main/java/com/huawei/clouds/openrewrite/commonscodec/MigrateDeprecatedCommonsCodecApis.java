package com.huawei.clouds.openrewrite.commonscodec;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ReplaceConstantWithAnotherConstant;

import java.util.List;

/**
 * Binary-compatible entry point retained for callers of the original public
 * recipe class. Every transformation delegates to OpenRewrite core recipes.
 */
@Deprecated(since = "1.0.0", forRemoval = false)
public final class MigrateDeprecatedCommonsCodecApis extends Recipe {
    private static final List<String> CHARSET_FIELDS = List.of(
            "ISO_8859_1", "US_ASCII", "UTF_16",
            "UTF_16BE", "UTF_16LE", "UTF_8");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Apache Commons Codec APIs";
    }

    @Override
    public String getDescription() {
        return "Compatibility wrapper that delegates deterministic method and constant migrations to " +
               "OpenRewrite core recipes; use MigrateCommonsCodecTo1_22_0 for project gating and risk markers.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                new ChangeMethodName(
                        "org.apache.commons.codec.digest.DigestUtils getShaDigest()",
                        "getSha1Digest", false, true),
                new ChangeMethodName(
                        "org.apache.commons.codec.digest.DigestUtils sha(..)",
                        "sha1", false, true),
                new ChangeMethodName(
                        "org.apache.commons.codec.digest.DigestUtils shaHex(..)",
                        "sha1Hex", false, true),
                new ChangeMethodName(
                        "org.apache.commons.codec.binary.Base64 isArrayByteBase64(byte[])",
                        "isBase64", false, true),
                constant("ISO_8859_1"),
                constant("US_ASCII"),
                constant("UTF_16"),
                constant("UTF_16BE"),
                constant("UTF_16LE"),
                constant("UTF_8"));
    }

    private static Recipe constant(String field) {
        if (!CHARSET_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Unsupported charset field: " + field);
        }
        return new ReplaceConstantWithAnotherConstant(
                "org.apache.commons.codec.Charsets." + field,
                "java.nio.charset.StandardCharsets." + field);
    }
}
