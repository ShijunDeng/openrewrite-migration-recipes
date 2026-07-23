package com.huawei.clouds.openrewrite.springexpression;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;
import static org.openrewrite.xml.Assertions.xml;

class SpringExpressionConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringExpressionConfigurationRisks());
    }

    @Test
    void marksValidInvalidAndCompilerGlobalProperties() {
        rewriteRun(properties(
                """
                spring.expression.maxOperations=25000
                spring.expression.compiler.mode=MIXED
                """,
                source -> source.path("spring.properties").after(actual -> actual).afterRecipe(after -> {
                    String output = after.printAll();
                    assertContains(output, FindSpringExpressionConfigurationRisks.LIMIT);
                    assertContains(output, FindSpringExpressionConfigurationRisks.COMPILER);
                })));

        rewriteRun(properties(
                """
                spring.expression.maxOperations=0
                """,
                source -> source.path("invalid.properties").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(),
                                FindSpringExpressionConfigurationRisks.INVALID_LIMIT))));

        rewriteRun(properties(
                """
                spring.expression.maxOperations=${SPEL_LIMIT}
                spring.expression.compiler.mode=aggressive
                """,
                source -> source.path("dynamic.properties").after(actual -> actual).afterRecipe(after ->
                {
                    String output = after.printAll();
                    assertContains(output, FindSpringExpressionConfigurationRisks.INVALID_LIMIT);
                    assertContains(output, FindSpringExpressionConfigurationRisks.INVALID_COMPILER);
                })));
    }

    @Test
    void marksNestedYamlGlobalSettings() {
        rewriteRun(yaml(
                """
                spring:
                  expression:
                    maxOperations: 15000
                    compiler:
                      mode: IMMEDIATE
                """,
                source -> source.path("application.yml").after(actual -> actual).afterRecipe(after -> {
                    String output = after.printAll();
                    assertContains(output, FindSpringExpressionConfigurationRisks.LIMIT);
                    assertContains(output, FindSpringExpressionConfigurationRisks.COMPILER);
                })));
    }

    @Test
    void marksExecutableAndJakartaExpressionsInStructuredConfiguration() {
        rewriteRun(
                properties(
                        """
                        route.predicate=#{request.method().equals('GET')}
                        legacy.type=#{T(javax.servlet.http.HttpServletRequest)}
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String output = after.printAll();
                            assertContains(output, FindSpringExpressionConfigurationRisks.EMBEDDED);
                            assertContains(output, FindSpringExpressionConfigurationRisks.JAKARTA);
                        })),
                yaml(
                        """
                        route:
                          key: "#{@router.select(target)}"
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(),
                                        FindSpringExpressionConfigurationRisks.EMBEDDED))),
                xml(
                        """
                        <beans>
                          <bean id="route" value="#{@router.select(target)}"/>
                          <value>#{new javax.naming.InitialContext()}</value>
                        </beans>
                        """,
                        source -> source.path("application-context.xml")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String output = after.printAll();
                                    assertContains(output, FindSpringExpressionConfigurationRisks.EMBEDDED);
                                    assertContains(output, FindSpringExpressionConfigurationRisks.JAKARTA);
                                }))
        );
    }

    @Test
    void marksJvmArgumentsInPomDockerAndOptionsFiles() {
        rewriteRun(
                xml(
                        """
                        <project><build><plugins><plugin><configuration>
                          <argLine>-Dspring.expression.maxOperations=0</argLine>
                        </configuration></plugin></plugins></build></project>
                        """,
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(),
                                        FindSpringExpressionConfigurationRisks.INVALID_LIMIT))),
                text(
                        """
                        ENV JAVA_TOOL_OPTIONS="-Dspring.expression.maxOperations=20000 -Dspring.expression.compiler.mode=MIXED"
                        """,
                        source -> source.path("Dockerfile").after(actual -> actual).afterRecipe(after -> {
                            String output = after.printAll();
                            assertContains(output, FindSpringExpressionConfigurationRisks.LIMIT);
                            assertContains(output, FindSpringExpressionConfigurationRisks.COMPILER);
                        })),
                text(
                        "-Dspring.expression.compiler.mode=unknown\n",
                        source -> source.path("jvm.options").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(),
                                        FindSpringExpressionConfigurationRisks.INVALID_COMPILER)))
        );
    }

    @Test
    void ignoresNonExecutablePlaceholdersSimilarKeysPomAndGeneratedFiles() {
        rewriteRun(
                properties("""
                        spring.expression.max-operations=10000
                        spring.expression.maxOperations.backup=10000
                        display=#{user.name}
                        """),
                yaml("""
                        message: "#{user.name}"
                        spring.expression.max-operations: 10000
                        """),
                xml(
                        """
                        <project><properties>
                          <expression>#{T(javax.servlet.Servlet)}</expression>
                        </properties></project>
                        """,
                        source -> source.path("pom.xml")),
                properties(
                        "spring.expression.maxOperations=0",
                        source -> source.path("target/generated/spring.properties"))
        );
    }

    @Test
    void configurationMarkersAreTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties(
                        "spring.expression.maxOperations=10000",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertOneOccurrence(after.printAll(),
                                        FindSpringExpressionConfigurationRisks.LIMIT))));
    }

    private static void assertContains(String output, String expected) {
        assertTrue(output.contains(expected), () -> "Missing <" + expected + "> in:\n" + output);
    }

    private static void assertOneOccurrence(String output, String expected) {
        int count = 0;
        for (int at = 0; (at = output.indexOf(expected, at)) >= 0; at += expected.length()) count++;
        int found = count;
        assertTrue(found == 1, () -> "Expected one occurrence but found " + found + " in:\n" + output);
    }
}
