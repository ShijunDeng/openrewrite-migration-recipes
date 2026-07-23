package com.huawei.clouds.openrewrite.jakartawrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindJakartaRest4SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJakartaRest4SourceRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(JakartaRestTestApi.sources()));
    }

    @ParameterizedTest(name = "source risk {0}")
    @MethodSource("risks")
    void marksPreciseRisk(String label, String source, String fragment) {
        rewriteRun(java(source, spec -> spec.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(fragment), after::printAll))));
    }

    static Stream<Arguments> risks() {
        return Stream.of(
                Arguments.of("JaxbLink import", "import jakarta.ws.rs.core.Link.JaxbLink; class T { JaxbLink l; }", "application-owned JAXB DTO"),
                Arguments.of("JaxbAdapter import", "import jakarta.ws.rs.core.Link.JaxbAdapter; class T { JaxbAdapter a; }", "URI plus link parameters"),
                Arguments.of("JAXB dependency", "import jakarta.xml.bind.annotation.adapters.XmlAdapter; abstract class T extends XmlAdapter<String,String> { }", "no longer depends on"),
                Arguments.of("managed bean annotation", "import jakarta.annotation.ManagedBean; @ManagedBean class Resource { }", "removed ManagedBean integration"),
                Arguments.of("context injection", "import jakarta.ws.rs.core.Context; class Resource { @Context Object request; }", "future removal of @Context"),
                Arguments.of("SSE direct close", "import jakarta.ws.rs.sse.SseEventSink; class R { void close(SseEventSink sink){ sink.close(); } }", "declares IOException"),
                Arguments.of("SSE implementation", "import jakarta.ws.rs.sse.SseEventSink; abstract class Sink implements SseEventSink { public void close(){} }", "disconnect races"),
                Arguments.of("SSE try resource", "import jakarta.ws.rs.sse.SseEventSink; class R { void x(SseEventSink sink){ try (sink) { } } }", "propagation/catch policy"),
                Arguments.of("SSE broadcaster", "import jakarta.ws.rs.sse.*; class R { void x(SseBroadcaster b,SseEventSink s){ b.register(s); b.close(); } }", "backpressure"),
                Arguments.of("RuntimeDelegate subclass", "import jakarta.ws.rs.ext.RuntimeDelegate; abstract class CustomDelegate extends RuntimeDelegate { }", "bootstrap and EntityPart"),
                Arguments.of("ExceptionMapper", "import jakarta.ws.rs.ext.ExceptionMapper; class Mapper implements ExceptionMapper<RuntimeException> { public Object toResponse(RuntimeException e){return null;} }", "default-mapper"),
                Arguments.of("MessageBodyReader", "import jakarta.ws.rs.ext.MessageBodyReader; class Reader implements MessageBodyReader<String> { }", "zero-length entities"),
                Arguments.of("Client lifecycle", "import jakarta.ws.rs.client.*; class R { void x(){ Client c=ClientBuilder.newClient(); c.target(\"https://example\"); c.close(); } }", "AutoCloseable ownership"),
                Arguments.of("relative Location semantics", "import jakarta.ws.rs.core.Response; class R { Object x(){ return Response.created(java.net.URI.create(\"orders/1\")); } }", "against the base URI"),
                Arguments.of("mixed servlet namespace", "import javax.servlet.Servlet; import jakarta.ws.rs.GET; class R { Servlet s; @GET void x(){} }", "mixed javax/jakarta runtime")
        );
    }

    @Test
    void sameNamedApplicationApisAreNoop() {
        rewriteRun(java("class SseEventSink { void close(){} } class R { void x(SseEventSink s){ s.close(); } }"));
    }

    @Test
    void generatedParentIsNoop() {
        rewriteRun(java("import jakarta.ws.rs.sse.SseEventSink; class R { void x(SseEventSink s){s.close();} }",
                spec -> spec.path("generated-code/R.java")));
    }
}
