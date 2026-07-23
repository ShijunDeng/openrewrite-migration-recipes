package com.huawei.clouds.openrewrite.jettyhttp;

final class JettyHttpLegacyApi {
    private JettyHttpLegacyApi() {
    }

    static String[] sources() {
        return new String[] {
                """
                package org.eclipse.jetty.http;
                public interface HttpContent {
                    java.nio.ByteBuffer getIndirectBuffer();
                    java.nio.ByteBuffer getDirectBuffer();
                    java.io.InputStream getInputStream();
                    java.nio.channels.ReadableByteChannel getReadableByteChannel();
                    java.util.Map<CompressedContentFormat, ? extends HttpContent> getPrecompressedContents();
                    void release();
                }
                """,
                """
                package org.eclipse.jetty.http;
                public class ResourceHttpContent implements HttpContent {
                    public ResourceHttpContent(Object resource, String mime) {}
                    public ResourceHttpContent(Object resource, String mime, int size) {}
                    public java.nio.ByteBuffer getIndirectBuffer(){return null;}
                    public java.nio.ByteBuffer getDirectBuffer(){return null;}
                    public java.io.InputStream getInputStream(){return null;}
                    public java.nio.channels.ReadableByteChannel getReadableByteChannel(){return null;}
                    public java.util.Map<CompressedContentFormat,? extends HttpContent> getPrecompressedContents(){return null;}
                    public void release(){}
                }
                """,
                """
                package org.eclipse.jetty.http;
                public class PrecompressedHttpContent extends ResourceHttpContent {
                    public PrecompressedHttpContent(){super(null, null);}
                }
                """,
                "package org.eclipse.jetty.http; public class CompressedContentFormat {}",
                """
                package fixture;
                public abstract class CustomContent implements org.eclipse.jetty.http.HttpContent {
                    public java.nio.ByteBuffer getDirectBuffer(){return null;}
                    public java.nio.ByteBuffer getIndirectBuffer(){return null;}
                }
                """,
                """
                package org.eclipse.jetty.http;
                public class HttpFields {
                    public HttpFields() {}
                    public HttpFields add(String name, String value){return this;}
                    public HttpFields put(String name, String value){return this;}
                    public static Mutable build(){return null;}
                    public static class Mutable extends HttpFields {
                        public HttpFields asImmutable(){return this;}
                    }
                }
                """,
                """
                package org.eclipse.jetty.http;
                public class HttpURI {
                    public HttpURI(String value){}
                    public void setPath(String path){}
                    public static HttpURI build(String value){return null;}
                }
                """,
                """
                package org.eclipse.jetty.http;
                public class HttpCookie {
                    public HttpCookie(String name, String value){}
                    public void setPath(String path){}
                    public static HttpCookie build(String name, String value){return null;}
                }
                """,
                """
                package org.eclipse.jetty.http;
                public class HttpParser {
                    public interface RequestHandler {}
                    public interface ResponseHandler {}
                    public void parseNext(java.nio.ByteBuffer value){}
                }
                """,
                "package org.eclipse.jetty.http; public class HttpGenerator { public void generateRequest(){} }",
                "package org.eclipse.jetty.http; public class HttpCompliance { public static HttpCompliance from(String v){return null;} }",
                "package org.eclipse.jetty.http; public class UriCompliance { public static UriCompliance from(String v){return null;} }",
                "package org.eclipse.jetty.http; public class CookieCompliance { public static CookieCompliance from(String v){return null;} }",
                "package org.eclipse.jetty.http; public class MimeTypes { public String getMimeByExtension(String v){return null;} }",
                "package org.eclipse.jetty.http; public class DateParser { public static long parseDate(String v){return 0;} }",
                "package org.eclipse.jetty.http; public class MultiPartFormInputStream {}",
                "package org.eclipse.jetty.http; public class MultiPartParser {}",
                "package org.eclipse.jetty.http; public class PathMap<T> {}",
                "package org.eclipse.jetty.http; public interface HttpFieldPreEncoder {}",
                """
                package org.eclipse.jetty.server;
                public interface Handler {
                    void handle(String target, Request request, Object servletRequest, Object servletResponse);
                }
                """,
                "package org.eclipse.jetty.server; public class Request { public void setHandled(boolean v){} }",
                "package org.eclipse.jetty.server; public class Response {}"
        };
    }
}
