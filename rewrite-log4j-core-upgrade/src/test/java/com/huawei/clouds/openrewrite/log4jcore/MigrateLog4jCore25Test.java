package com.huawei.clouds.openrewrite.log4jcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class MigrateLog4jCore25Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateLog4jCore25())
                .parser(JavaParser.fromJavaVersion().dependsOn(Log4jCoreTestApi.legacySources()));
    }

    @ParameterizedTest(name = "filter builder {0}")
    @MethodSource("filterBuilders")
    void migratesFilterBuilderMethods(String label, String builder, String method) {
        rewriteRun(spec -> spec.typeValidationOptions(TypeValidation.none()), java("""
                import org.apache.logging.log4j.core.Filter;
                import org.apache.logging.log4j.core.config.LoggerConfig;
                class C { void configure(%s builder, Filter candidate) { builder.%s(candidate); } }
                """.formatted(builder, method), """
                import org.apache.logging.log4j.core.Filter;
                import org.apache.logging.log4j.core.config.LoggerConfig;
                class C { void configure(%s builder, Filter candidate) { builder.setFilter(candidate); } }
                """.formatted(builder)));
    }

    static Stream<Arguments> filterBuilders() {
        return Stream.of(
                Arguments.of("logger typo", "LoggerConfig.Builder", "withtFilter"),
                Arguments.of("logger deprecated", "LoggerConfig.Builder", "withFilter"),
                Arguments.of("root typo", "LoggerConfig.RootLogger.Builder", "withtFilter")
        );
    }

    @Test
    void preservesFilterExpressionAndChainReceiver() {
        rewriteRun(java("""
                import org.apache.logging.log4j.core.*;
                import org.apache.logging.log4j.core.config.LoggerConfig;
                class C { Filter next(){return null;} Object configure(LoggerConfig.Builder builder) {
                    return builder.withtFilter(next());
                } }
                """, """
                import org.apache.logging.log4j.core.*;
                import org.apache.logging.log4j.core.config.LoggerConfig;
                class C { Filter next(){return null;} Object configure(LoggerConfig.Builder builder) {
                    return builder.setFilter(next());
                } }
                """));
    }

    @Test
    void migratesCustomMachineryNewBuilderChainFixture() {
        rewriteRun(java("""
                import org.apache.logging.log4j.core.config.LoggerConfig;
                class CMLogger { Object configure(){ return LoggerConfig.newBuilder().withtFilter(null); } }
                """, """
                import org.apache.logging.log4j.core.config.LoggerConfig;
                class CMLogger { Object configure(){ return LoggerConfig.newBuilder().setFilter(null); } }
                """));
    }

    @ParameterizedTest(name = "nolookups token {0}")
    @MethodSource("nolookupsTokens")
    void removesObsoleteNoLookupsFromJavaPatterns(String before, String after) {
        rewriteRun(java("class C { String pattern = \"" + before + "\"; }",
                "class C { String pattern = \"" + after + "\"; }"));
    }

    static Stream<Arguments> nolookupsTokens() {
        return Stream.of(
                Arguments.of("%d %m{nolookups}%n", "%d %m%n"),
                Arguments.of("%msg{nolookups} [%t]", "%msg [%t]"),
                Arguments.of("%message{nolookups}", "%message"),
                Arguments.of("%m{nolookups}{ansi}%n", "%m{ansi}%n"),
                Arguments.of("%m{nolookups} %msg{nolookups} %message{nolookups}", "%m %msg %message")
        );
    }

    @Test
    void removesObsoleteNoLookupsFromAttributedPatternLayoutBuilder() {
        rewriteRun(java("""
                import org.apache.logging.log4j.core.layout.PatternLayout;
                class C { Object layout() { return PatternLayout.newBuilder().withPattern("%d %m{nolookups}%n"); } }
                """, """
                import org.apache.logging.log4j.core.layout.PatternLayout;
                class C { Object layout() { return PatternLayout.newBuilder().withPattern("%d %m%n"); } }
                """));
    }

    @Test
    void removesEclipseSteadyLog4ShellMitigationPatternFromXmlAttribute() {
        rewriteRun(xml(
                "<Configuration><Appenders><Console><PatternLayout pattern=\"%date [%thread] [%highlight{%-5level}] %-30.30c - %m{nolookups}%n\"/>" +
                "</Console></Appenders></Configuration>",
                "<Configuration><Appenders><Console><PatternLayout pattern=\"%date [%thread] [%highlight{%-5level}] %-30.30c - %m%n\"/>" +
                "</Console></Appenders></Configuration>",
                source -> source.path("src/main/resources/log4j2.xml")));
    }

    @Test
    void removesNoLookupsFromXmlElementText() {
        rewriteRun(xml(
                "<Configuration><PatternLayout><Pattern>%msg{nolookups}%n</Pattern></PatternLayout></Configuration>",
                "<Configuration><PatternLayout><Pattern>%msg%n</Pattern></PatternLayout></Configuration>",
                source -> source.path("src/main/resources/log4j2-test.xml")));
    }

    @Test
    void removesNoLookupsFromPropertiesConfiguration() {
        rewriteRun(properties(
                "appender.console.layout.pattern = %d %m{nolookups}%n\n",
                "appender.console.layout.pattern = %d %m%n\n",
                source -> source.path("src/main/resources/log4j2.properties")));
    }

    @Test
    void removesNoLookupsFromPlainYamlAndJsonResources() {
        rewriteRun(
                text("pattern: \"%msg{nolookups}%n\"\n", "pattern: \"%msg%n\"\n",
                        source -> source.path("src/main/resources/log4j2.yaml")),
                text("{\"pattern\":\"%message{nolookups}%n\"}\n", "{\"pattern\":\"%message%n\"}\n",
                        source -> source.path("src/main/resources/log4j2.json")));
    }

    @Test
    void explicitLookupsAndOrdinaryApplicationMethodsAreNoop() {
        rewriteRun(java("""
                class Builder { Builder withtFilter(Object f){return this;} }
                class C { String pattern = "%m{lookups}%n"; Object f(Builder b){ return b.withtFilter(this); } }
                """));
    }

    @Test
    void documentationAndUnrelatedConfigurationFilesAreNoop() {
        rewriteRun(
                text("Document `%m{nolookups}` as a historical mitigation.\n",
                        source -> source.path("README.md")),
                properties("example.pattern = %m{nolookups}\n",
                        source -> source.path("src/main/resources/application.properties")),
                xml("<article><code>%m{nolookups}</code></article>",
                        source -> source.path("src/main/resources/help.xml")));
    }

    @Test
    void documentationInsideJavaAndLog4jFilesIsNoop() {
        rewriteRun(
                java("class C { String documentation = \"Document %m{nolookups} as history\"; }"),
                xml("<Configuration><Properties><Property name=\"help\">Document %m{nolookups}</Property>" +
                                "</Properties></Configuration>",
                        source -> source.path("src/main/resources/log4j2-docs.xml")),
                properties("property.help = Document %m{nolookups}\n",
                        source -> source.path("src/main/resources/log4j2-docs.properties")),
                text("description: \"Document %m{nolookups}\"\n",
                        source -> source.path("src/main/resources/log4j2-docs.yaml")));
    }

    @Test
    void targetSetterIsNoop() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(Log4jCoreTestApi.targetSources())),
                java("""
                        import org.apache.logging.log4j.core.*;
                        import org.apache.logging.log4j.core.config.LoggerConfig;
                        class C { void f(LoggerConfig.Builder b, Filter filter){ b.setFilter(filter); } }
                        """));
    }

    @Test
    void generatedSourcesAndResourcesAreNoop() {
        rewriteRun(
                java("""
                        import org.apache.logging.log4j.core.Filter;
                        import org.apache.logging.log4j.core.config.LoggerConfig;
                        class C {
                            String p="%m{nolookups}";
                            void f(LoggerConfig.Builder builder, Filter filter) {
                                builder.withtFilter(filter);
                            }
                        }
                        """,
                        source -> source.path("target/generated-sources/C.java")),
                xml("<Configuration><PatternLayout pattern=\"%m{nolookups}\"/></Configuration>",
                        source -> source.path("build/generated/log4j2.xml")));
    }

    @Test
    void compositionOrderIsStable() {
        assertEquals(2, new MigrateLog4jCore25().getRecipeList().size());
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.apache.logging.log4j.core.*;
                        import org.apache.logging.log4j.core.config.LoggerConfig;
                        class C { String pattern="%m{nolookups}"; void f(LoggerConfig.Builder b,Filter x){b.withtFilter(x);} }
                        """, """
                        import org.apache.logging.log4j.core.*;
                        import org.apache.logging.log4j.core.config.LoggerConfig;
                        class C { String pattern="%m"; void f(LoggerConfig.Builder b,Filter x){b.setFilter(x);} }
                        """));
    }
}
