package com.huawei.clouds.openrewrite.tomcatcatalina;

final class TomcatCatalinaTestApi {
    private TomcatCatalinaTestApi() { }

    static String[] sources() {
        return new String[]{
                "package jakarta.servlet; public interface Servlet {}",
                "package jakarta.servlet; public interface ServletRequest { String getRealPath(String path); String getMethod(); }",
                "package jakarta.servlet; public interface ServletContext { void log(String message); void log(Exception exception,String message); void log(String message,Throwable throwable); Servlet getServlet(String name); java.util.Enumeration<Servlet> getServlets(); java.util.Enumeration<String> getServletNames(); }",
                "package jakarta.servlet; public interface SingleThreadModel {}",
                "package jakarta.servlet; public class ServletException extends Exception { public ServletException(String message){super(message);} }",
                "package jakarta.servlet; public class ServletInputStream extends java.io.InputStream { public int read(){return -1;} }",
                "package jakarta.servlet; public class UnavailableException extends ServletException { public UnavailableException(Servlet servlet,String message){super(message);} public UnavailableException(int seconds,Servlet servlet,String message){super(message);} public UnavailableException(String message){super(message);} public UnavailableException(String message,int seconds){super(message);} public Servlet getServlet(){return null;} }",
                "package jakarta.servlet.http; public interface HttpServletRequest extends jakarta.servlet.ServletRequest { boolean isRequestedSessionIdFromUrl(); boolean isRequestedSessionIdFromURL(); StringBuffer getRequestURL(); }",
                "package jakarta.servlet.http; public interface HttpServletResponse { String encodeUrl(String url); String encodeURL(String url); String encodeRedirectUrl(String url); String encodeRedirectURL(String url); void setStatus(int status); void setStatus(int status,String message); }",
                "package jakarta.servlet.http; public interface HttpSessionContext { java.util.Enumeration<String> getIds(); HttpSession getSession(String id); }",
                "package jakarta.servlet.http; public interface HttpSession { Object getValue(String name); Object getAttribute(String name); String[] getValueNames(); java.util.Enumeration<String> getAttributeNames(); void putValue(String name,Object value); void setAttribute(String name,Object value); void removeValue(String name); void removeAttribute(String name); HttpSessionContext getSessionContext(); }",
                "package jakarta.servlet.http; public class HttpUtils { public static StringBuffer getRequestURL(HttpServletRequest request){return null;} public static java.util.Hashtable<String,String[]> parseQueryString(String value){return null;} public static java.util.Hashtable<String,String[]> parsePostData(int length,jakarta.servlet.ServletInputStream input){return null;} }",
                "package jakarta.servlet.http; public class Cookie { public Cookie(String name,String value){} public String getComment(){return null;} public void setComment(String comment){} public int getVersion(){return 0;} public void setVersion(int version){} }",
                "package jakarta.el; public abstract class MethodExpression { public boolean isParmetersProvided(){return false;} public boolean isParametersProvided(){return false;} }",
                "package org.apache.catalina.core; public class JreMemoryLeakPreventionListener { public boolean isAWTThreadProtection(){return false;} public void setAWTThreadProtection(boolean value){} public boolean isGcDaemonProtection(){return false;} public void setGcDaemonProtection(boolean value){} public boolean isLdapPoolProtection(){return false;} public void setLdapPoolProtection(boolean value){} public boolean isTokenPollerProtection(){return false;} public void setTokenPollerProtection(boolean value){} public boolean isXmlParsingProtection(){return false;} public void setXmlParsingProtection(boolean value){} public boolean getForkJoinCommonPoolProtection(){return false;} public void setForkJoinCommonPoolProtection(boolean value){} }",
                "package org.apache.catalina.startup; public class Tomcat { public void start(){} }",
                "package org.apache.coyote.http11; public class Http11AprProtocol { public void setPollTime(int value){} }",
                "package org.apache.tomcat.jni; public class Buffer { public static java.nio.ByteBuffer malloc(int value){return null;} }",
                "package javax.servlet; public interface Servlet {}",
                "package javax.servlet; public interface ServletRequest { String getRealPath(String path); String getMethod(); }",
                "package javax.servlet; public interface ServletContext { void log(String message); void log(Exception exception,String message); void log(String message,Throwable throwable); Servlet getServlet(String name); java.util.Enumeration<Servlet> getServlets(); java.util.Enumeration<String> getServletNames(); }",
                "package javax.servlet; public interface SingleThreadModel {}",
                "package javax.servlet; public class UnavailableException extends Exception { public UnavailableException(Servlet servlet,String message){} public Servlet getServlet(){return null;} }",
                "package javax.servlet.http; public interface HttpServletRequest extends javax.servlet.ServletRequest { boolean isRequestedSessionIdFromUrl(); boolean isRequestedSessionIdFromURL(); }",
                "package javax.servlet.http; public interface HttpServletResponse { String encodeUrl(String url); String encodeURL(String url); String encodeRedirectUrl(String url); String encodeRedirectURL(String url); void setStatus(int status); void setStatus(int status,String message); }",
                "package javax.servlet.http; public interface HttpSessionContext {}",
                "package javax.servlet.http; public interface HttpSession { Object getValue(String name); Object getAttribute(String name); String[] getValueNames(); java.util.Enumeration<String> getAttributeNames(); void putValue(String name,Object value); void setAttribute(String name,Object value); void removeValue(String name); void removeAttribute(String name); HttpSessionContext getSessionContext(); }",
                "package javax.servlet.http; public class HttpUtils { public static Object parseQueryString(String value){return null;} }",
                "package javax.el; public abstract class MethodExpression { public boolean isParmetersProvided(){return false;} public boolean isParametersProvided(){return false;} }"
        };
    }
}
