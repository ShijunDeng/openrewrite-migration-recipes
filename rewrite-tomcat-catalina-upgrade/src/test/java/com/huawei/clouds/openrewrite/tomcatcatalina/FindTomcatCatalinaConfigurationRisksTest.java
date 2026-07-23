package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.xml.Assertions.xml;

class FindTomcatCatalinaConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindTomcatCatalinaConfigurationRisks());
    }

    @ParameterizedTest(name = "Connector protocol {0}")
    @ValueSource(strings = {"HTTP/1.1", "AJP/1.3", "org.apache.coyote.http11.Http11NioProtocol"})
    void marksMissingParameterLimitOnRealServerConnectors(String protocol) {
        rewriteRun(xml("<Server><Service><Connector port=\"8080\" protocol=\"" + protocol + "\"/></Service></Server>",
                specification -> specification.path("conf/server.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), "reduced Connector maxParameterCount"))));
    }

    @Test
    void explicitParameterLimitIsNotMarked() {
        rewriteRun(xml("<Server><Service><Connector port=\"8080\" protocol=\"HTTP/1.1\" maxParameterCount=\"1000\"/></Service></Server>",
                specification -> specification.path("server.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "tightened URI decoding/normalization");
                    assertFalse(after.printAll().contains("reduced Connector maxParameterCount"));
                })));
    }

    @ParameterizedTest(name = "removed APR connector {0}")
    @ValueSource(strings = {"org.apache.coyote.http11.Http11AprProtocol", "org.apache.coyote.ajp.AjpAprProtocol"})
    void marksRemovedAprProtocols(String protocol) {
        rewriteRun(xml("<Server><Service><Connector port=\"8443\" protocol=\"" + protocol + "\" maxParameterCount=\"1000\"/></Service></Server>",
                specification -> specification.path("server.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), "APR connector was removed"))));
    }

    @ParameterizedTest(name = "URI policy {0}")
    @ValueSource(strings = {"allowBackslash", "encodedSolidusHandling", "encodedReverseSolidusHandling", "relaxedPathChars", "relaxedQueryChars", "useBodyEncodingForURI", "URIEncoding"})
    void marksUriNormalizationControls(String attribute) {
        rewriteRun(xml("<Server><Service><Connector port=\"8080\" maxParameterCount=\"1000\" " + attribute + "=\"test\"/></Service></Server>",
                specification -> specification.path("server.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), "tightened URI decoding/normalization"))));
    }

    @ParameterizedTest(name = "critical component {0}")
    @MethodSource("criticalComponents")
    void marksClusterAndDigestComponents(String label, String source, String marker) {
        rewriteRun(xml(source, specification -> specification.path("conf/server.xml").after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), marker))));
    }

    static Stream<Arguments> criticalComponents() {
        return Stream.of(
                Arguments.of("cluster interceptor", "<Server><Cluster><Interceptor className=\"org.apache.catalina.tribes.group.interceptors.EncryptInterceptor\"/></Cluster></Server>", "stop every cluster node"),
                Arguments.of("digest valve", "<Server><Engine><Host><Valve className=\"org.apache.catalina.authenticator.DigestAuthenticator\"/></Host></Engine></Server>", "supersedes the supplied 10.1.56 security target")
        );
    }

    @Test
    void marksStrongDefaultServletEtagsOnly() {
        String defaultServlet = "<web-app><servlet><servlet-name>default</servlet-name><servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class><init-param><param-name>useStrongETags</param-name><param-value>true</param-value></init-param></servlet></web-app>";
        String businessServlet = "<web-app><servlet><servlet-name>business</servlet-name><servlet-class>example.Business</servlet-class><init-param><param-name>useStrongETags</param-name><param-value>true</param-value></init-param></servlet></web-app>";
        rewriteRun(
                xml(defaultServlet, specification -> specification.path("WEB-INF/web.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), "SHA-256 instead of SHA-1"))),
                xml(businessServlet, specification -> specification.path("business/WEB-INF/web.xml"))
        );
    }

    @Test
    void marksLegacyAndConflictingNewerDescriptors() {
        rewriteRun(
                xml("<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" version=\"5.0\"/>",
                        specification -> specification.path("src/main/webapp/WEB-INF/web.xml").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "predates Servlet 6"))),
                xml("<web-app xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\" version=\"4.0\"/>",
                        specification -> specification.path("WEB-INF/web.xml").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "predates Servlet 6"))),
                xml("<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" version=\"6.1\"/>",
                        specification -> specification.path("web.xml").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "belongs to Tomcat 11")))
        );
    }

    @ParameterizedTest(name = "obsolete listener config {0}")
    @ValueSource(strings = {"awtThreadProtection", "gcDaemonProtection", "ldapPoolProtection", "tokenPollerProtection", "xmlParsingProtection", "forkJoinCommonPoolProtection"})
    void marksObsoleteListenerAttributesWhenFinderRunsAlone(String attribute) {
        rewriteRun(xml("<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" " + attribute + "=\"true\"/></Server>",
                specification -> specification.path("server.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), "has no Tomcat 10.1 setter"))));
    }

    @Test
    void filenamesOwnershipAndLookalikesPreventFalsePositives() {
        rewriteRun(
                xml("<Server><Service><Connector port=\"8080\"/></Service></Server>", specification -> specification.path("example.xml")),
                xml("<Catalog><Connector port=\"8080\"/></Catalog>", specification -> specification.path("server.xml")),
                xml("<Server><Listener className=\"example.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\"/></Server>", specification -> specification.path("lookalike-server.xml")),
                xml("<Server><Service><Connector protocol=\"example.Http11AprProtocol\" maxParameterCount=\"1000\"/></Service></Server>", specification -> specification.path("server.xml").after(actual -> actual).afterRecipe(after -> assertFalse(after.printAll().contains("APR connector was removed")))),
                xml("<Server><Cluster><Interceptor className=\"example.EncryptInterceptor\"/></Cluster><Engine><Host><Valve className=\"example.DigestAuthenticator\"/></Host></Engine></Server>", specification -> specification.path("server.xml")),
                xml("<Server><Cluster><Interceptor className=\"org.apache.catalina.tribes.group.interceptors.EncryptInterceptor\"/></Cluster></Server>", specification -> specification.path("application.xml")),
                xml("<Catalog><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\"/></Catalog>", specification -> specification.path("server.xml"))
        );
    }

    @Test
    void generatedConfigurationIsUnmarked() {
        rewriteRun(xml("<Server><Service><Connector port=\"8080\"/></Service></Server>",
                specification -> specification.path("target/conf/server.xml")));
    }

    @Test
    void marksJavaxTypeReferencesInNonPomXmlOnly() {
        rewriteRun(
                xml("<Context initializer=\"javax.servlet.ServletContainerInitializer\"><type>javax.el.ExpressionFactory</type></Context>",
                        source -> source.path("context.xml").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("XML configuration still names"), after::printAll);
                        })),
                xml("<project><property>javax.servlet.http.HttpSession</property></project>",
                        source -> source.path("pom.xml")),
                xml("<Context initializer=\"example.javax.servletish.Type\"/>",
                        source -> source.path("context.xml"))
        );
    }

    private static void assertContains(String actual, String token) {
        assertTrue(actual.contains(token), () -> "Expected marker containing '" + token + "' in:\n" + actual);
    }
}
