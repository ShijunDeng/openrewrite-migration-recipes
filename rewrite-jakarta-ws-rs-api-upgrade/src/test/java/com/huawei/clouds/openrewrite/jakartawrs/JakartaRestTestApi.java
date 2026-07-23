package com.huawei.clouds.openrewrite.jakartawrs;

final class JakartaRestTestApi {
    private JakartaRestTestApi() { }

    static String[] sources() {
        return new String[]{
                "package javax.ws.rs; public @interface GET { }",
                "package javax.ws.rs; public @interface Path { String value(); }",
                "package javax.ws.rs.core; public class Link { public static class Builder{} public static Builder fromUri(java.net.URI u){return null;} }",
                "package javax.ws.rs.core; public @interface Context { }",
                "package javax.ws.rs.sse; public interface SseEventSink { boolean isClosed(); java.util.concurrent.CompletionStage<?> send(Object e); void close(); }",
                "package jakarta.ws.rs; public @interface GET { }",
                "package jakarta.ws.rs; public @interface Path { String value(); }",
                "package jakarta.ws.rs.core; public @interface Context { }",
                "package jakarta.ws.rs.core; public class Response { public static Object created(java.net.URI uri) { return null; } }",
                """
                package jakarta.ws.rs.core;
                public class Link {
                    public static class Builder { }
                    public static Builder fromUri(java.net.URI uri) { return null; }
                    public static class JaxbLink { public JaxbLink(java.net.URI uri){} }
                    public static class JaxbAdapter { }
                }
                """,
                "package jakarta.ws.rs.sse; public interface SseEventSink extends AutoCloseable { boolean isClosed(); java.util.concurrent.CompletionStage<?> send(Object e); void close(); }",
                "package jakarta.ws.rs.sse; public interface SseBroadcaster extends AutoCloseable { void register(SseEventSink s); java.util.concurrent.CompletionStage<?> broadcast(Object e); void close(); }",
                "package jakarta.ws.rs.ext; public abstract class RuntimeDelegate { protected RuntimeDelegate(){} }",
                "package jakarta.ws.rs.ext; public interface ExceptionMapper<T extends Throwable> { Object toResponse(T e); }",
                "package jakarta.ws.rs.ext; public interface MessageBodyReader<T> { }",
                "package jakarta.ws.rs.ext; public interface MessageBodyWriter<T> { }",
                "package jakarta.ws.rs.ext; public interface ContextResolver<T> { T getContext(Class<?> type); }",
                "package jakarta.ws.rs.client; public interface WebTarget { WebTarget register(Class<?> c); }",
                "package jakarta.ws.rs.client; public interface Client extends AutoCloseable { WebTarget target(String u); void close(); }",
                "package jakarta.ws.rs.client; public final class ClientBuilder { public static Client newClient(){return null;} public static ClientBuilder newBuilder(){return null;} public Client build(){return null;} }",
                "package jakarta.annotation; public @interface ManagedBean { String value() default \"\"; }",
                "package javax.annotation; public @interface ManagedBean { String value() default \"\"; }",
                "package javax.servlet; public interface Servlet { }",
                "package jakarta.xml.bind.annotation.adapters; public abstract class XmlAdapter<A,B> { public abstract B unmarshal(A a) throws Exception; public abstract A marshal(B b) throws Exception; }"
        };
    }
}
