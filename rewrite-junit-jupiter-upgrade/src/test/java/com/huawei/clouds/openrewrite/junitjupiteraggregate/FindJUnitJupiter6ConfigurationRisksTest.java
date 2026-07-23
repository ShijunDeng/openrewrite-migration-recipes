package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class FindJUnitJupiter6ConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJUnitJupiter6ConfigurationRisks());
    }

    @ParameterizedTest(name = "properties key {0}")
    @MethodSource("configurationKeys")
    void marksExactPropertiesEntry(String key, String message) {
        rewriteRun(properties(key + "=value\n", "~~(" + message + ")~~>" + key + "=value\n"));
    }

    static Stream<Arguments> configurationKeys() {
        return Stream.of(
                Arguments.of("junit.jupiter.tempdir.scope", FindJUnitJupiter6ConfigurationRisks.TEMPDIR),
                Arguments.of("junit.jupiter.params.arguments.conversion.locale.format", FindJUnitJupiter6ConfigurationRisks.LOCALE),
                Arguments.of("junit.jupiter.execution.parallel.mode.default", FindJUnitJupiter6ConfigurationRisks.ENUM),
                Arguments.of("junit.jupiter.execution.parallel.mode.classes.default", FindJUnitJupiter6ConfigurationRisks.ENUM),
                Arguments.of("junit.jupiter.execution.timeout.mode", FindJUnitJupiter6ConfigurationRisks.ENUM),
                Arguments.of("junit.jupiter.execution.timeout.thread.mode.default", FindJUnitJupiter6ConfigurationRisks.ENUM),
                Arguments.of("junit.jupiter.extensions.testinstantiation.extensioncontextscope.default", FindJUnitJupiter6ConfigurationRisks.ENUM),
                Arguments.of("junit.jupiter.tempdir.cleanup.mode.default", FindJUnitJupiter6ConfigurationRisks.ENUM),
                Arguments.of("junit.jupiter.testinstance.lifecycle.default", FindJUnitJupiter6ConfigurationRisks.ENUM),
                Arguments.of("junit.platform.reflection.search.useLegacySemantics", FindJUnitJupiter6ConfigurationRisks.LEGACY_REFLECTION),
                Arguments.of("junit.platform.discovery.issue.failure.phase", FindJUnitJupiter6ConfigurationRisks.PLATFORM_ENUM),
                Arguments.of("junit.platform.discovery.issue.severity.critical", FindJUnitJupiter6ConfigurationRisks.PLATFORM_ENUM)
        );
    }

    @ParameterizedTest(name = "unrelated properties {0}")
    @MethodSource("unrelatedKeys")
    void unrelatedPropertiesAreNoop(String key) {
        rewriteRun(properties(key + "=value\n"));
    }

    static Stream<String> unrelatedKeys() {
        return Stream.of("junit.jupiter.tempdir.cleanup.mode", "junit.platform.output.capture.stdout",
                "company.junit.jupiter.tempdir.scope", "junit.jupiter.execution.parallel.enabled");
    }

    @org.junit.jupiter.api.Test
    void marksNestedYamlEntry() {
        rewriteRun(yaml(
                """
                  junit:
                    jupiter:
                      tempdir:
                        scope: per_context
                  """,
                """
                  junit:
                    jupiter:
                      tempdir:
                        ~~(JUnit 6 removed junit.jupiter.tempdir.scope; choose the supported TempDir lifecycle explicitly and verify cleanup, sharing, and parallel-test isolation before deleting this key)~~>scope: per_context
                  """));
    }

    @org.junit.jupiter.api.Test
    void marksDottedYamlEntry() {
        rewriteRun(yaml(
                "junit.jupiter.params.arguments.conversion.locale.format: iso_639\n",
                "~~(JUnit 6 removed junit.jupiter.params.arguments.conversion.locale.format and always uses IETF BCP 47 via Locale.forLanguageTag; update locale data and assertions before deleting this key)~~>junit.jupiter.params.arguments.conversion.locale.format: iso_639\n"));
    }

    @org.junit.jupiter.api.Test
    void marksMavenSystemPropertyTag() {
        rewriteRun(xml(
                "<configuration><junit.jupiter.tempdir.scope>per_context</junit.jupiter.tempdir.scope></configuration>",
                "<configuration><!--~~(JUnit 6 removed junit.jupiter.tempdir.scope; choose the supported TempDir lifecycle explicitly and verify cleanup, sharing, and parallel-test isolation before deleting this key)~~>--><junit.jupiter.tempdir.scope>per_context</junit.jupiter.tempdir.scope></configuration>",
                source -> source.path("pom.xml")));
    }

    @org.junit.jupiter.api.Test
    void identicalBusinessXmlTagOutsidePomIsNoop() {
        rewriteRun(xml(
                "<configuration><junit.jupiter.tempdir.scope>business</junit.jupiter.tempdir.scope></configuration>",
                source -> source.path("src/main/resources/company.xml")));
    }

    @org.junit.jupiter.api.Test
    void generatedConfigurationIsNoop() {
        rewriteRun(properties("junit.jupiter.tempdir.scope=per_context\n",
                source -> source.path("target/generated/junit-platform.properties")));
    }
}
