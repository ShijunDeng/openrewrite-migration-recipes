package com.huawei.clouds.openrewrite.jettyhttp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class JettyHttpSourceMigrationTest implements RewriteTest {
    private static final String RELOCATION =
            "com.huawei.clouds.openrewrite.jettyhttp.MigrateJettyHttp12TypeRelocations";
    private static final String BUFFER_ACCESS =
            "com.huawei.clouds.openrewrite.jettyhttp.MigrateJettyHttp12ContentBufferAccess";
    private static final String RISKS =
            "com.huawei.clouds.openrewrite.jettyhttp.FindJettyHttp12SourceRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(JettyHttpTestSupport.recipe(RISKS))
                .parser(JavaParser.fromJavaVersion().dependsOn(JettyHttpLegacyApi.sources()));
    }

    @Test
    void officialCoreLeavesRelocateAllThreePublicContentTypes() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RELOCATION)),
                java("""
                        import org.eclipse.jetty.http.HttpContent;
                        import org.eclipse.jetty.http.ResourceHttpContent;
                        import org.eclipse.jetty.http.PrecompressedHttpContent;
                        class ContentTypes {
                            HttpContent content;
                            ResourceHttpContent resource;
                            PrecompressedHttpContent compressed;
                        }
                        """,
                        """
                        import org.eclipse.jetty.http.content.HttpContent;
                        import org.eclipse.jetty.http.content.PreCompressedHttpContent;
                        import org.eclipse.jetty.http.content.ResourceHttpContent;

                        class ContentTypes {
                            HttpContent content;
                            ResourceHttpContent resource;
                            PreCompressedHttpContent compressed;
                        }
                        """));
    }

    @Test
    void officialCoreLeavesCollapseBothLegacyBufferAccessors() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(BUFFER_ACCESS)),
                java("""
                        import org.eclipse.jetty.http.HttpContent;
                        class BufferAccess {
                            java.nio.ByteBuffer direct(HttpContent content) {
                                return content.getDirectBuffer();
                            }
                            java.nio.ByteBuffer indirect(HttpContent content) {
                                return content.getIndirectBuffer();
                            }
                        }
                        """,
                        """
                        import org.eclipse.jetty.http.HttpContent;
                        class BufferAccess {
                            java.nio.ByteBuffer direct(HttpContent content) {
                                return content.getByteBuffer();
                            }
                            java.nio.ByteBuffer indirect(HttpContent content) {
                                return content.getByteBuffer();
                            }
                        }
                        """));
    }

    @Test
    void competingBufferDefinitionsStayForManualConsolidation() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(BUFFER_ACCESS)),
                java("""
                        import org.eclipse.jetty.http.HttpContent;
                        abstract class CustomContent implements HttpContent {
                            public java.nio.ByteBuffer getDirectBuffer() { return null; }
                            public java.nio.ByteBuffer getIndirectBuffer() { return null; }
                        }
                        """));
    }

    @Test
    void bufferCallsOnOverridesMigrate() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(BUFFER_ACCESS)),
                java("""
                        import fixture.CustomContent;
                        class CustomContentUse {
                            java.nio.ByteBuffer read(CustomContent content) {
                                return content.getDirectBuffer();
                            }
                        }
                        """,
                        """
                        import fixture.CustomContent;
                        class CustomContentUse {
                            java.nio.ByteBuffer read(CustomContent content) {
                                return content.getByteBuffer();
                            }
                        }
                        """));
    }

    @Test
    void generatedSourcesAreExcludedFromOfficialRelocations() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RELOCATION)),
                java("""
                        import org.eclipse.jetty.http.HttpContent;
                        class Generated { HttpContent content; }
                        """, source -> source.path("target/generated-sources/Generated.java")));
    }

    @ParameterizedTest(name = "source risk {0}")
    @MethodSource("sourceRisks")
    void marksAttributedIncompatibilityFamilies(String label, String source, String marker) {
        rewriteRun(java(source, spec -> spec.path(label + ".java")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(marker), after.printAll()))));
    }

    static Stream<Arguments> sourceRisks() {
        return Stream.of(
                Arguments.of("removed-multipart", """
                        import org.eclipse.jetty.http.MultiPartFormInputStream;
                        class RemovedMultipart { MultiPartFormInputStream parser; }
                        """, FindJettyHttpSourceRisks.REMOVED),
                Arguments.of("removed-pathmap", """
                        import org.eclipse.jetty.http.PathMap;
                        class RemovedPathMap { PathMap<String> paths; }
                        """, FindJettyHttpSourceRisks.REMOVED),
                Arguments.of("content-buffer", """
                        import org.eclipse.jetty.http.HttpContent;
                        class ContentBuffer { java.nio.ByteBuffer bytes(HttpContent c){ return c.getDirectBuffer(); } }
                        """, FindJettyHttpSourceRisks.CONTENT),
                Arguments.of("mutable-headers", """
                        import org.eclipse.jetty.http.HttpFields;
                        class Headers { HttpFields headers(){ return new HttpFields().put("X", "v"); } }
                        """, FindJettyHttpSourceRisks.HEADERS),
                Arguments.of("mutable-uri", """
                        import org.eclipse.jetty.http.HttpURI;
                        class Uri { void path(){ new HttpURI("/").setPath("/a"); } }
                        """, FindJettyHttpSourceRisks.URI),
                Arguments.of("mutable-cookie", """
                        import org.eclipse.jetty.http.HttpCookie;
                        class Cookie { void cookie(){ new HttpCookie("n", "v").setPath("/"); } }
                        """, FindJettyHttpSourceRisks.COOKIE),
                Arguments.of("custom-parser", """
                        import java.nio.ByteBuffer;
                        import org.eclipse.jetty.http.HttpParser;
                        class Parser { void parse(HttpParser p, ByteBuffer b){ p.parseNext(b); } }
                        """, FindJettyHttpSourceRisks.PARSER),
                Arguments.of("compliance", """
                        import org.eclipse.jetty.http.HttpCompliance;
                        class Compliance { Object mode(){ return HttpCompliance.from("RFC7230,NO_AMBIGUOUS_PATH"); } }
                        """, FindJettyHttpSourceRisks.COMPLIANCE),
                Arguments.of("mime", """
                        import org.eclipse.jetty.http.MimeTypes;
                        class Mime { String type(MimeTypes m){ return m.getMimeByExtension("file.js"); } }
                        """, FindJettyHttpSourceRisks.MIME_DATE),
                Arguments.of("handler", """
                        import org.eclipse.jetty.server.Handler;
                        import org.eclipse.jetty.server.Request;
                        class OldHandler implements Handler {
                            public void handle(String target, Request request, Object req, Object res) {
                                request.setHandled(true);
                            }
                        }
                        """, FindJettyHttpSourceRisks.HANDLER),
                Arguments.of("spi", """
                        import org.eclipse.jetty.http.HttpFieldPreEncoder;
                        class Encoder implements HttpFieldPreEncoder {}
                        """, FindJettyHttpSourceRisks.SPI_MODULE));
    }

    @Test
    void sameNamedBusinessTypesAndStringCommentsAreNotMarked() {
        rewriteRun(java("""
                class HttpFields {
                    HttpFields put(String name, String value) { return this; }
                }
                class LocalUse {
                    HttpFields headers = new HttpFields().put("x", "y");
                    String note = "MultiPartParser is a business label";
                }
                """));
    }

    @Test
    void contentBufferRenameDoesNotTouchSameNamedBusinessMethod() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(BUFFER_ACCESS)),
                java("""
                        class BusinessContent {
                            java.nio.ByteBuffer getDirectBuffer() { return null; }
                        }
                        class BusinessUse {
                            java.nio.ByteBuffer read(BusinessContent content) {
                                return content.getDirectBuffer();
                            }
                        }
                        """));
    }

    @Test
    void fullRelocationThenRiskRecipeKeepsMethodReviewVisible() {
        var full = JettyHttpTestSupport.recipe(
                "com.huawei.clouds.openrewrite.jettyhttp.MigrateJettyHttpTo12_0_34");
        rewriteRun(spec -> spec.recipe(full),
                xml(JettyHttpTestSupport.pom("11.0.20"),
                        source -> source.path("pom.xml").after(actual -> actual)),
                java("""
                import org.eclipse.jetty.http.HttpContent;
                class ContentAccess {
                    java.nio.ByteBuffer data(HttpContent content) {
                        return content.getDirectBuffer();
                    }
                }
                """, source -> source.path("src/main/java/ContentAccess.java")
                        .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("org.eclipse.jetty.http.content.HttpContent"), printed);
                    assertTrue(printed.contains(FindJettyHttpSourceRisks.CONTENT), printed);
                    assertTrue(printed.contains("getByteBuffer()"), printed);
                    assertFalse(printed.contains("getDirectBuffer()"), printed);
                })));
    }

    @Test
    void sourceMarkersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.eclipse.jetty.http.HttpURI;
                        class UriUse { HttpURI uri = new HttpURI("/"); }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertEquals(2, JettyHttpTestSupport.occurrences(
                                after.printAll(), FindJettyHttpSourceRisks.URI)))));
    }

    @Test
    void generatedSourceRisksRemainUnmarked() {
        rewriteRun(java("""
                import org.eclipse.jetty.http.HttpURI;
                class Generated { HttpURI uri = new HttpURI("/"); }
                """, source -> source.path("build/generated/Generated.java").afterRecipe(after -> {
                    String printed = after.printAll();
                    assertFalse(printed.contains(FindJettyHttpSourceRisks.URI), printed);
                })));
    }
}
