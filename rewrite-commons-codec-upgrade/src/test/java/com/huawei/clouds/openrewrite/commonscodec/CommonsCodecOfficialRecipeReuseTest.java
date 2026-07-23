package com.huawei.clouds.openrewrite.commonscodec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.ReplaceConstantWithAnotherConstant;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class CommonsCodecOfficialRecipeReuseTest implements RewriteTest {
    private static final String OFFICIAL =
            "org.openrewrite.apache.commons.codec.ApacheBase64ToJavaBase64";
    private static final String SAFE =
            "com.huawei.clouds.openrewrite.commonscodec.ApplyOfficialSafeApacheBase64Migration";
    private static final String DEPENDENCY =
            "com.huawei.clouds.openrewrite.commonscodec.UpgradeSelectedCommonsCodecDependencyInMarkedProject";
    private static final String OFFICIAL_DEPENDENCY =
            "org.openrewrite.java.dependencies.UpgradeDependencyVersion";
    private static final String DETERMINISTIC =
            "com.huawei.clouds.openrewrite.commonscodec.MigrateDeterministicCommonsCodecJava";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.commonscodec.MigrateCommonsCodecTo1_22_0";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath(
                    "com.huawei.clouds.openrewrite.commonscodec",
                    "org.openrewrite.apache.commons.codec",
                    "org.openrewrite.java.dependencies")
            .build();

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(ENVIRONMENT.activateRecipes(RECOMMENDED))
                .parser(JavaParser.fromJavaVersion().dependsOn(CommonsCodecTestApi.sources()));
    }

    @Test
    void discoversPinnedOfficialApacheRecipe() {
        Set<String> names = ENVIRONMENT.listRecipes().stream()
                .map(Recipe::getName).collect(Collectors.toSet());
        assertTrue(names.contains(OFFICIAL));
        Recipe official = ENVIRONMENT.listRecipes().stream()
                .filter(recipe -> OFFICIAL.equals(recipe.getName()))
                .findFirst().orElseThrow();
        assertEquals("2.28.0",
                official.getClass().getPackage().getImplementationVersion());
    }

    @Test
    void safeCompositionDelegatesToTheOfficialRecipe() {
        Recipe safe = unwrap(ENVIRONMENT.activateRecipes(SAFE));
        List<String> children = safe.getRecipeList().stream()
                .map(CommonsCodecOfficialRecipeReuseTest::unwrap)
                .filter(recipe -> !recipe.getClass().getName()
                        .endsWith("PreconditionBellwether"))
                .map(Recipe::getName)
                .toList();
        assertEquals(List.of(OFFICIAL), children);
    }

    @Test
    void dependencyCompositionRunsOfficialRecipeBeforeStrictFallback() {
        Recipe dependency = unwrap(ENVIRONMENT.activateRecipes(DEPENDENCY));
        List<String> children = dependency.getRecipeList().stream()
                .map(CommonsCodecOfficialRecipeReuseTest::unwrap)
                .filter(recipe -> !recipe.getClass().getName()
                        .endsWith("PreconditionBellwether"))
                .map(Recipe::getName)
                .toList();
        assertEquals(List.of(
                OFFICIAL_DEPENDENCY,
                UpgradeSelectedCommonsCodecDependency.class.getName()), children);
        Recipe official = ENVIRONMENT.listRecipes().stream()
                .filter(recipe -> OFFICIAL_DEPENDENCY.equals(recipe.getName()))
                .findFirst().orElseThrow();
        assertEquals("1.59.0",
                official.getClass().getPackage().getImplementationVersion());
    }

    @Test
    void deterministicCompositionUsesOnlyOfficialJavaTransformationLeaves() {
        Recipe deterministic = unwrap(ENVIRONMENT.activateRecipes(DETERMINISTIC));
        List<Recipe> children = deterministic.getRecipeList().stream()
                .map(CommonsCodecOfficialRecipeReuseTest::unwrap)
                .filter(recipe -> !recipe.getClass().getName()
                        .endsWith("PreconditionBellwether"))
                .toList();
        assertEquals(List.of(
                        SAFE,
                        ChangeMethodName.class.getName(),
                        ChangeMethodName.class.getName(),
                        ChangeMethodName.class.getName(),
                        ChangeMethodName.class.getName(),
                        ReplaceConstantWithAnotherConstant.class.getName(),
                        ReplaceConstantWithAnotherConstant.class.getName(),
                        ReplaceConstantWithAnotherConstant.class.getName(),
                        ReplaceConstantWithAnotherConstant.class.getName(),
                        ReplaceConstantWithAnotherConstant.class.getName(),
                        ReplaceConstantWithAnotherConstant.class.getName()),
                children.stream().map(Recipe::getName).toList());

        List<ChangeMethodName> renames = children.stream()
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .toList();
        assertEquals(List.of(
                        "org.apache.commons.codec.digest.DigestUtils getShaDigest()",
                        "org.apache.commons.codec.digest.DigestUtils sha(..)",
                        "org.apache.commons.codec.digest.DigestUtils shaHex(..)",
                        "org.apache.commons.codec.binary.Base64 isArrayByteBase64(byte[])"),
                renames.stream().map(ChangeMethodName::getMethodPattern).toList());
        assertTrue(renames.stream().allMatch(rename ->
                Boolean.FALSE.equals(rename.getMatchOverrides()) &&
                Boolean.TRUE.equals(rename.getIgnoreDefinition())));

        List<ReplaceConstantWithAnotherConstant> constants = children.stream()
                .filter(ReplaceConstantWithAnotherConstant.class::isInstance)
                .map(ReplaceConstantWithAnotherConstant.class::cast)
                .toList();
        assertEquals(List.of(
                        "ISO_8859_1", "US_ASCII", "UTF_16",
                        "UTF_16BE", "UTF_16LE", "UTF_8"),
                constants.stream()
                        .map(ReplaceConstantWithAnotherConstant::
                                getExistingFullyQualifiedConstantName)
                        .map(name -> name.substring(name.lastIndexOf('.') + 1))
                        .toList());
        assertTrue(constants.stream().allMatch(constant ->
                constant.getExistingFullyQualifiedConstantName().startsWith(
                        "org.apache.commons.codec.Charsets.") &&
                constant.getFullyQualifiedConstantName().equals(
                constant.getExistingFullyQualifiedConstantName().replace(
                                "org.apache.commons.codec.Charsets",
                                "java.nio.charset.StandardCharsets"))));
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyPublicRecipeClassDelegatesOnlyToOfficialCoreLeaves() {
        List<Recipe> children =
                new MigrateDeprecatedCommonsCodecApis().getRecipeList();
        assertEquals(10, children.size());
        assertTrue(children.subList(0, 4).stream()
                .allMatch(ChangeMethodName.class::isInstance));
        assertTrue(children.subList(4, 10).stream()
                .allMatch(ReplaceConstantWithAnotherConstant.class::isInstance));
    }

    @Test
    void allRecommendedRecipesValidate() {
        Recipe recipe = ENVIRONMENT.activateRecipes(RECOMMENDED);
        assertTrue(recipe.validateAll().stream()
                        .allMatch(validation -> validation.isValid()),
                () -> recipe.validateAll().toString());
    }

    @Test
    void officialWrapperCannotRunWithoutASelectedProjectMarker() {
        rewriteRun(
                spec -> spec.recipe(ENVIRONMENT.activateRecipes(SAFE)),
                java(
                        "import org.apache.commons.codec.binary.Base64; class T { " +
                        "byte[] x(){return Base64.encodeBase64(new byte[]{1});} }"));
    }

    @ParameterizedTest(name = "official safe encoder {0}")
    @ValueSource(strings = {
            "encodeBase64",
            "encodeBase64String",
            "encodeBase64URLSafe",
            "encodeBase64URLSafeString"
    })
    void officialMigratesOnlyProvenLiteralEncoderInputs(String method) {
        String replacement = switch (method) {
            case "encodeBase64" ->
                    "Base64.getEncoder().encode(new byte[]{1, 2})";
            case "encodeBase64String" ->
                    "Base64.getEncoder().encodeToString(new byte[]{1, 2})";
            case "encodeBase64URLSafe" ->
                    "Base64.getUrlEncoder().withoutPadding().encode(new byte[]{1, 2})";
            default ->
                    "Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{1, 2})";
        };
        String returnType = method.endsWith("String") ? "String" : "byte[]";
        rewriteRun(
                xml(selectedPom(), upgradedPom(),
                        source -> source.path("pom.xml")),
                java(
                "import org.apache.commons.codec.binary.Base64; class T { " +
                returnType + " x(){return Base64." + method +
                "(new byte[]{1,2});} }",
                "import java.util.Base64;\n\nclass T { " +
                returnType + " x(){return " + replacement + ";} }"));
    }

    @Test
    void realApacheCodecVectorUsesOfficialEncoder() {
        // Reduced from apache/commons-codec@81a6295f071df5819893422a397d94bc396f2edd,
        // src/test/java/org/apache/commons/codec/binary/Base64Test.java#L994.
        rewriteRun(
                xml(selectedPom(), upgradedPom(),
                        source -> source.path("pom.xml")),
                java(
                """
                import org.apache.commons.codec.binary.Base64;
                class Vector {
                    byte[] encoded() {
                        return Base64.encodeBase64(new byte[] { 0, 0 });
                    }
                }
                """,
                """
                import java.util.Base64;

                class Vector {
                    byte[] encoded() {
                        return Base64.getEncoder().encode(new byte[]{0, 0});
                    }
                }
                """));
    }

    @Test
    void stringLiteralBytesAreProvenNonNullAndBounded() {
        rewriteRun(
                xml(selectedPom(), upgradedPom(),
                        source -> source.path("pom.xml")),
                java(
                """
                import java.nio.charset.StandardCharsets;
                import org.apache.commons.codec.binary.Base64;
                class T {
                    String encoded() {
                        return Base64.encodeBase64String("codec".getBytes(StandardCharsets.UTF_8));
                    }
                }
                """,
                """
                import java.nio.charset.StandardCharsets;
                import java.util.Base64;

                class T {
                    String encoded() {
                        return Base64.getEncoder().encodeToString("codec".getBytes(StandardCharsets.UTF_8));
                    }
                }
                """));
    }

    @ParameterizedTest(name = "semantic boundary {0}")
    @ValueSource(strings = {
            "byte[] x(byte[] value){return Base64.encodeBase64(value);}",
            "byte[] x(){return Base64.encodeBase64(null);}",
            "byte[] x(){return Base64.decodeBase64(new byte[]{'Z','g'});}",
            "byte[] x(){return Base64.encodeBase64(new byte[]{1}, true);}",
            "byte[] x(){return Base64.encodeBase64Chunked(new byte[]{1});}",
            "byte[] x(){return new Base64().encode(new byte[]{1});}"
    })
    void nullableDecoderChunkedMimeAndInstanceSemanticsAreNoop(String body) {
        rewriteRun(
                xml(selectedPom(), upgradedPom(),
                        source -> source.path("pom.xml")),
                java(
                "import org.apache.commons.codec.binary.Base64; class T { " +
                body + " }"));
    }

    @Test
    void mixedSafeEncodeAndDecoderKeepsTheWholeFileUnchanged() {
        rewriteRun(
                xml(selectedPom(), upgradedPom(),
                        source -> source.path("pom.xml")),
                java("""
                import org.apache.commons.codec.binary.Base64;
                class T {
                    byte[] encode(){return Base64.encodeBase64(new byte[]{1});}
                    byte[] decode(){return Base64.decodeBase64(new byte[]{'Z','g'});}
                }
                """));
    }

    @Test
    void staticImportRemainsUntouched() {
        rewriteRun(
                xml(selectedPom(), upgradedPom(),
                        source -> source.path("pom.xml")),
                java("""
                import static org.apache.commons.codec.binary.Base64.encodeBase64;
                class T {
                    byte[] encode(){return encodeBase64(new byte[]{1});}
                }
                """));
    }

    @ParameterizedTest(name = "unsafe Base64 ownership {0}")
    @MethodSource("unsafeStaticBase64Uses")
    void staticBase64ImportsAndFieldsBlockAnOtherwiseSafeEncoder(
            String label, String imports, String additionalMember) {
        rewriteRun(
                xml(selectedPom(), upgradedPom(),
                        source -> source.path("pom.xml")),
                java(imports + """

                class T {
                    byte[] encode() {
                        return Base64.encodeBase64(new byte[]{1});
                    }
                """ + additionalMember + "}\n"));
    }

    static Stream<Arguments> unsafeStaticBase64Uses() {
        return Stream.of(
                Arguments.of(
                        "used static field import",
                        """
                        import org.apache.commons.codec.binary.Base64;
                        import static org.apache.commons.codec.binary.Base64.MIME_CHUNK_SIZE;
                        """,
                        "    int width() { return MIME_CHUNK_SIZE; }\n"),
                Arguments.of(
                        "unused static field import",
                        """
                        import org.apache.commons.codec.binary.Base64;
                        import static org.apache.commons.codec.binary.Base64.MIME_CHUNK_SIZE;
                        """,
                        ""),
                Arguments.of(
                        "wildcard static import",
                        """
                        import org.apache.commons.codec.binary.Base64;
                        import static org.apache.commons.codec.binary.Base64.*;
                        """,
                        "    int width() { return MIME_CHUNK_SIZE; }\n"),
                Arguments.of(
                        "static method import",
                        """
                        import org.apache.commons.codec.binary.Base64;
                        import static org.apache.commons.codec.binary.Base64.decodeBase64;
                        """,
                        "    byte[] decode() { return decodeBase64(new byte[]{'Z', 'g'}); }\n"),
                Arguments.of(
                        "qualified static field",
                        "import org.apache.commons.codec.binary.Base64;\n",
                        "    int width() { return Base64.MIME_CHUNK_SIZE; }\n"));
    }

    @Test
    void streamingApisRemainUntouched() {
        rewriteRun(
                xml(selectedPom(), upgradedPom(),
                        source -> source.path("pom.xml")),
                java("""
                import java.io.InputStream;
                import org.apache.commons.codec.binary.Base64InputStream;
                class T {
                    Base64InputStream decode(InputStream in) {
                        return new Base64InputStream(in);
                    }
                }
                """));
    }

    @Test
    void officialSafeMigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(selectedPom(), upgradedPom(),
                        source -> source.path("pom.xml")),
                java(
                        "import org.apache.commons.codec.binary.Base64; class T { " +
                        "String x(){return Base64.encodeBase64String(new byte[]{1});} }",
                        "import java.util.Base64;\n\nclass T { " +
                        "String x(){return Base64.getEncoder().encodeToString(new byte[]{1});} }"));
    }

    private static String selectedPom() {
        return pom("1.11");
    }

    private static String upgradedPom() {
        return pom("1.22.0");
    }

    private static String pom(String version) {
        return "<project><modelVersion>4.0.0</modelVersion>" +
               "<groupId>example</groupId><artifactId>app</artifactId>" +
               "<version>1</version><dependencies><dependency>" +
               "<groupId>commons-codec</groupId>" +
               "<artifactId>commons-codec</artifactId>" +
               "<version>" + version +
               "</version></dependency></dependencies></project>";
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) {
            current = delegating.getDelegate();
        }
        return current;
    }
}
