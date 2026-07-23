package com.huawei.clouds.openrewrite.springsecurityweb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringSecurityWebConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringSecurityWeb6511ConfigurationRisks());
    }

    static Stream<Arguments> exactProperties() {
        return Stream.of(
                Arguments.of("spring.security.filter.dispatcher-types=REQUEST,ASYNC,ERROR\n",
                        FindSpringSecurityWeb6511ConfigurationRisks.FILTER_CHAIN),
                Arguments.of("spring.security.user.password=secret\n",
                        FindSpringSecurityWeb6511ConfigurationRisks.AUTHENTICATION),
                Arguments.of("server.servlet.session.cookie.same-site=lax\n",
                        FindSpringSecurityWeb6511ConfigurationRisks.SESSION_CONTEXT),
                Arguments.of("spring.security.csrf.enabled=false\n",
                        FindSpringSecurityWeb6511ConfigurationRisks.CSRF),
                Arguments.of("spring.web.cors.allowed-origins=https://example.test\n",
                        FindSpringSecurityWeb6511ConfigurationRisks.HEADERS_CORS),
                Arguments.of("server.forward-headers-strategy=framework\n",
                        FindSpringSecurityWeb6511ConfigurationRisks.HEADERS_CORS),
                Arguments.of("spring.security.oauth2.client.registration.github.client-id=id\n",
                        FindSpringSecurityWeb6511ConfigurationRisks.OAUTH_SAML),
                Arguments.of("spring.security.saml2.relyingparty.registration.test.entity-id=sp\n",
                        FindSpringSecurityWeb6511ConfigurationRisks.OAUTH_SAML),
                Arguments.of("logging.level.org.springframework.security=TRACE\n",
                        FindSpringSecurityWeb6511ConfigurationRisks.OBSERVATION));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exactProperties")
    void marksExactPropertyBoundaries(String before, String message) {
        rewriteRun(properties(before, source -> source.path("application.properties")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    @Test
    void marksEquivalentNestedYamlPaths() {
        rewriteRun(yaml("""
                spring:
                  security:
                    filter:
                      dispatcher-types: REQUEST,ASYNC,ERROR
                    user:
                      password: secret
                    oauth2:
                      client:
                        registration:
                          github:
                            client-id: id
                  web:
                    cors:
                      allowed-origins: https://example.test
                server:
                  servlet:
                    session:
                      cookie:
                        same-site: lax
                """, source -> source.path("application.yml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringSecurityWeb6511ConfigurationRisks.FILTER_CHAIN), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511ConfigurationRisks.AUTHENTICATION), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511ConfigurationRisks.OAUTH_SAML), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511ConfigurationRisks.HEADERS_CORS), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511ConfigurationRisks.SESSION_CONTEXT), printed);
        })));
    }

    @Test
    void marksSecurityNamespaceXmlBeansAndWebXmlBoundaries() {
        rewriteRun(
                xml("""
                        <beans xmlns:security="http://www.springframework.org/schema/security">
                          <security:http use-authorization-manager="false">
                            <security:intercept-url pattern="/admin/**" access="ROLE_ADMIN"/>
                            <security:csrf disabled="true"/>
                            <security:session-management session-fixation-protection="migrateSession"/>
                            <security:remember-me key="legacy"/>
                            <security:custom-filter ref="auditFilter" before="AUTHORIZATION_FILTER"/>
                          </security:http>
                          <bean class="org.springframework.security.web.savedrequest.HttpSessionRequestCache">
                            <property name="requestMatcher" ref="matcher"/>
                          </bean>
                        </beans>
                        """, source -> source.path("security-context.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(
                                FindSpringSecurityWeb6511ConfigurationRisks.XML_SECURITY), after.printAll()))),
                xml("""
                        <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">
                          <filter>
                            <filter-name>springSecurityFilterChain</filter-name>
                            <filter-class>org.springframework.web.filter.DelegatingFilterProxy</filter-class>
                          </filter>
                          <filter-mapping>
                            <filter-name>springSecurityFilterChain</filter-name>
                            <url-pattern>/*</url-pattern>
                            <dispatcher>ASYNC</dispatcher>
                          </filter-mapping>
                        </web-app>
                        """, source -> source.path("WEB-INF/web.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(
                                FindSpringSecurityWeb6511ConfigurationRisks.WEB_XML), after.printAll()))));
    }

    @Test
    void ignoresUnrelatedLookalikesPomAndGeneratedConfiguration() {
        rewriteRun(
                properties("""
                        app.note=spring.security.user.password
                        server.port=8080
                        spring.application.name=orders
                        """, source -> source.afterRecipe(after ->
                        assertFalse(after.printAll().contains("~~("), after.printAll()))),
                yaml("""
                        app:
                          note: spring.security.oauth2.client.registration
                        spring:
                          application:
                            name: orders
                        """, source -> source.afterRecipe(after ->
                        assertFalse(after.printAll().contains("~~("), after.printAll()))),
                xml("<beans><http/><bean class=\"example.SecurityFilterChain\"/></beans>",
                        source -> source.path("context.xml").afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~("), after.printAll()))),
                xml("<project><properties><spring.security.user.password>x</spring.security.user.password></properties></project>",
                        source -> source.path("pom.xml")),
                properties("spring.security.user.password=secret\n",
                        source -> source.path("target/classes/application.properties").afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~("), after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties("spring.security.csrf.enabled=false\n",
                        source -> source.path("application.properties").after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(),
                                        FindSpringSecurityWeb6511ConfigurationRisks.CSRF)))));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
