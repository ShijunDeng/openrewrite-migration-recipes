package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;

/** Mark runtime and protocol configuration changes that need deployment evidence. */
public final class FindTomcatCatalinaConfigurationRisks extends Recipe {
    static final String PARAMETER_LIMIT =
            "Tomcat 10.1.8 reduced Connector maxParameterCount from 10000 to 1000; choose an explicit limit from traffic, " +
            "multipart, abuse-protection and compatibility evidence rather than silently restoring the old default";
    static final String APR =
            "The APR connector was removed in Tomcat 10.1; replace this protocol with NIO/NIO2 and separately validate " +
            "Tomcat Native/OpenSSL, TLS ciphers, sendfile, polling, HTTP/2 and graceful shutdown";
    static final String CLUSTER =
            "Tomcat 10.1.56 changed EncryptInterceptor wire data; stop every cluster node and restart the whole cluster on " +
            "10.1.56 because mixed old/new nodes cannot exchange messages";
    static final String DIGEST =
            "Tomcat 10.1.57, which supersedes the supplied 10.1.56 security target, requires valid RFC 7616 qop for " +
            "DIGEST authentication; before approving the higher target verify client support, configured algorithms, " +
            "credential storage, downgrade policy and authentication failure telemetry";
    static final String ETAG =
            "Strong default-servlet ETags use SHA-256 instead of SHA-1 from Tomcat 10.1.46; invalidate/rebuild caches and " +
            "update golden headers, conditional requests and downstream proxy expectations";
    static final String DESCRIPTOR =
            "This deployment descriptor predates Servlet 6; migrate its Jakarta namespace/schema/version only after " +
            "reviewing removed elements, defaults, fragments, initializers and container integrations";
    static final String NEWER_DESCRIPTOR =
            "This descriptor declares Servlet 6.1, which belongs to Tomcat 11 and is newer than Tomcat 10.1's Servlet 6.0; " +
            "do not lower it automatically—resolve the conflicting target version first";
    static final String OBSOLETE_LISTENER =
            "This JreMemoryLeakPreventionListener attribute has no Tomcat 10.1 setter; remove it because the Java-8 leak " +
            "workaround no longer applies on the required Java 11 baseline";
    static final String URI =
            "Tomcat 10.1 tightened URI decoding/normalization and 10.1.55 rejects NULL bytes; validate encoded slash/backslash, " +
            "path parameters, reverse-proxy normalization, rewrite rules, routing, access logs and rejection tests";
    static final String NAMESPACE_REFERENCE =
            "XML configuration still names a javax Servlet/EL type; identify its reflection, scanner or framework owner and " +
            "removed-type status before changing it to Jakarta";

    private static final Set<String> APR_PROTOCOLS = Set.of(
            "org.apache.coyote.http11.Http11AprProtocol", "org.apache.coyote.ajp.AjpAprProtocol");
    private static final String ENCRYPT_INTERCEPTOR =
            "org.apache.catalina.tribes.group.interceptors.EncryptInterceptor";
    private static final String DIGEST_AUTHENTICATOR =
            "org.apache.catalina.authenticator.DigestAuthenticator";
    private static final Set<String> LEGACY_DESCRIPTOR_VERSIONS = Set.of(
            "2.4", "2.5", "3.0", "3.1", "4.0", "5.0");
    private static final Set<String> LEGACY_DESCRIPTOR_NAMESPACES = Set.of(
            "http://java.sun.com/xml/ns/javaee", "http://xmlns.jcp.org/xml/ns/javaee");

    @Override
    public String getDisplayName() {
        return "Find Tomcat Catalina 10.1 configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks Connector defaults and URI policy, removed APR protocols, EncryptInterceptor rolling-upgrade hazards, " +
               "the higher 10.1.57 DIGEST qop boundary, strong ETags, Servlet 5 descriptors and obsolete listener attributes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Xml.Document document) || !(tree instanceof SourceFile source) ||
                    UpgradeSelectedTomcatCatalinaDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                return new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                        Xml.Tag visited = super.visitTag(tag, ec);
                        String className = attribute(visited, "className");
                        String protocol = attribute(visited, "protocol");

                        if (!"pom.xml".equals(fileName) && visited.getAttributes().stream()
                                .map(Xml.Attribute::getValueAsString)
                                .anyMatch(FindTomcatCatalinaConfigurationRisks::containsJavaxWebType)) {
                            visited = mark(visited, NAMESPACE_REFERENCE);
                        }

                        if ("server.xml".equals(fileName) && "Connector".equals(visited.getName()) &&
                            underServer(getCursor())) {
                            Xml.Tag marked = visited;
                            if (attribute(visited, "maxParameterCount") == null) marked = mark(marked, PARAMETER_LIMIT);
                            if (protocol != null && APR_PROTOCOLS.contains(protocol)) marked = mark(marked, APR);
                            marked = mark(marked, URI);
                            return marked;
                        }
                        if ("server.xml".equals(fileName) && underServer(getCursor())) {
                            if ("Connector".equals(visited.getName()) && className != null &&
                                APR_PROTOCOLS.contains(className)) {
                                return mark(visited, APR);
                            }
                            if ("Interceptor".equals(visited.getName()) && ENCRYPT_INTERCEPTOR.equals(className)) {
                                return mark(visited, CLUSTER);
                            }
                            if ("Valve".equals(visited.getName()) && DIGEST_AUTHENTICATOR.equals(className)) {
                                return mark(visited, DIGEST);
                            }
                            if ("Listener".equals(visited.getName()) &&
                                "org.apache.catalina.core.JreMemoryLeakPreventionListener".equals(className) &&
                                visited.getAttributes().stream().anyMatch(attribute ->
                                        MigrateTomcatCatalina101Configuration.REMOVED_LISTENER_ATTRIBUTES
                                                .contains(attribute.getKeyAsString()))) return mark(visited, OBSOLETE_LISTENER);
                        }

                        if ("web.xml".equals(fileName) && "web-app".equals(visited.getName())) {
                            String descriptorVersion = attribute(visited, "version");
                            String namespace = attribute(visited, "xmlns");
                            if ("6.1".equals(descriptorVersion)) return mark(visited, NEWER_DESCRIPTOR);
                            if (descriptorVersion != null && LEGACY_DESCRIPTOR_VERSIONS.contains(descriptorVersion) ||
                                namespace != null && LEGACY_DESCRIPTOR_NAMESPACES.contains(namespace)) {
                                return mark(visited, DESCRIPTOR);
                            }
                        }
                        if ("web.xml".equals(fileName) && "init-param".equals(visited.getName()) &&
                            "useStrongETags".equals(visited.getChildValue("param-name").orElse(null)) &&
                            "true".equalsIgnoreCase(visited.getChildValue("param-value").orElse("")) &&
                            defaultServlet(getCursor())) {
                            return mark(visited, ETAG);
                        }
                        return visited;
                    }

                    @Override
                    public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                        Xml.CharData visited = super.visitCharData(charData, ec);
                        return !"pom.xml".equals(fileName) && containsJavaxWebType(visited.getText())
                                ? mark(visited, NAMESPACE_REFERENCE) : visited;
                    }
                }.visitNonNull(document, ctx);
            }
        };
    }

    private static String attribute(Xml.Tag tag, String name) {
        return tag.getAttributes().stream().filter(attribute -> name.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
    }

    private static boolean containsJavaxWebType(String value) {
        return value.contains("javax.servlet.") || value.contains("javax.el.");
    }

    private static boolean underServer(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "Server".equals(tag.getName())) return true;
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static boolean defaultServlet(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "servlet".equals(tag.getName())) {
                return "default".equals(tag.getChildValue("servlet-name").orElse(null));
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
