package com.huawei.clouds.openrewrite.jcloverslf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class JclOverSlf4jJavaRiskTest implements RewriteTest {
    @Test
    void marksFactoryDiscoveryButNotPublicGetLog() {
        rewriteRun(spec -> javaSpec(spec),
                java("""
                        import org.apache.commons.logging.Log;
                        import org.apache.commons.logging.LogFactory;
                        class App {
                            Log log = LogFactory.getLog(App.class);
                            Object factory = LogFactory.getFactory();
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(unit -> {
                    String printed = unit.printAll();
                    assertTrue(printed.contains("always returns SLF4JLogFactory"));
                    assertTrue(printed.contains("Log log = LogFactory.getLog(App.class);"));
                })));
    }

    @Test
    void marksReleaseAndReleaseAllLifecycleCalls() {
        rewriteRun(spec -> javaSpec(spec),
                java("""
                        import org.apache.commons.logging.LogFactory;
                        class Reload {
                            void stop(ClassLoader loader) {
                                LogFactory.release(loader);
                                LogFactory.releaseAll();
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(unit ->
                        assertTrue(unit.printAll().contains("does not maintain LogFactory instances")))));
    }

    @Test
    void marksDiscoverySystemPropertiesAndServices() {
        rewriteRun(spec -> javaSpec(spec),
                java("""
                        class Discovery {
                            void configure() {
                                System.setProperty("org.apache.commons.logging.LogFactory", "example.CustomFactory");
                                System.setProperty("org.apache.commons.logging.diagnostics.dest", "STDOUT");
                                String service = "META-INF/services/org.apache.commons.logging.LogFactory";
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(unit ->
                        assertTrue(unit.printAll().contains("custom-factory discovery are ignored")))));
    }

    @Test
    void marksUnsupportedCommonsLoggingImplementationImports() {
        rewriteRun(spec -> javaSpec(spec),
                java("""
                        import org.apache.commons.logging.impl.LogFactoryImpl;
                        import org.apache.commons.logging.impl.Jdk14Logger;
                        class Legacy {
                            Object factory = new LogFactoryImpl();
                            Object logger = new Jdk14Logger("app");
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(unit ->
                        assertTrue(unit.printAll().contains("not supplied by jcl-over-slf4j")))));
    }

    @Test
    void leavesSupportedSimpleLogAndSlf4jBridgeTypesAlone() {
        rewriteRun(spec -> javaSpec(spec),
                java("""
                        import org.apache.commons.logging.impl.SimpleLog;
                        import org.apache.commons.logging.impl.SLF4JLogFactory;
                        class Supported {
                            Object simple = new SimpleLog("app");
                            Object factory = new SLF4JLogFactory();
                        }
                        """));
    }

    @Test
    void marksStaticBinderImportsAndReflectiveNames() {
        rewriteRun(spec -> javaSpec(spec),
                java("""
                        import org.slf4j.impl.StaticLoggerBinder;
                        class Binding {
                            Object binder = StaticLoggerBinder.getSingleton();
                            String marker = "org.slf4j.impl.StaticMarkerBinder";
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(unit ->
                        assertTrue(unit.printAll().contains("no longer honors org.slf4j.impl.Static")))));
    }

    @Test
    void marksExplicitSlf4jProviderProperty() {
        rewriteRun(spec -> javaSpec(spec),
                java("""
                        class Provider {
                            void select() {
                                System.setProperty("slf4j.provider", "example.Provider");
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(unit ->
                        assertTrue(unit.printAll().contains("bypasses ServiceLoader")))));
    }

    @Test
    void marksCustomLogFactorySubclass() {
        rewriteRun(spec -> javaSpec(spec),
                java("""
                        import org.apache.commons.logging.LogFactory;
                        class CustomFactory extends LogFactory {}
                        """, source -> source.after(actual -> actual).afterRecipe(unit ->
                        assertTrue(unit.printAll().contains("Custom LogFactory subclasses")))));
    }

    @Test
    void leavesUnrelatedSameNamesAndStringsAlone() {
        rewriteRun(spec -> javaSpec(spec),
                java("""
                        class Local {
                            static class LogFactory {
                                static Object getFactory() { return null; }
                            }
                            Object value = LogFactory.getFactory();
                            String property = "example.LogFactory";
                        }
                        """));
    }

    @Test
    void excludesGeneratedAndInstalledJava() {
        rewriteRun(spec -> javaSpec(spec),
                java("class Generated { String binder = \"org.slf4j.impl.StaticLoggerBinder\"; }",
                        source -> source.path("generated/Generated.java")),
                java("class Installed { String config = \"commons-logging.properties\"; }",
                        source -> source.path("install/Installed.java")));
    }

    @Test
    void javaMarkersAreIdempotent() {
        rewriteRun(spec -> javaSpec(spec).cycles(2).expectedCyclesThatMakeChanges(1),
                java("class App { String binder = \"org.slf4j.impl.StaticLoggerBinder\"; }",
                        source -> source.after(actual -> actual)));
    }

    private static org.openrewrite.test.RecipeSpec javaSpec(org.openrewrite.test.RecipeSpec spec) {
        return spec.recipe(new FindJclOverSlf4jJavaRisks())
                .typeValidationOptions(TypeValidation.none())
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        "package org.apache.commons.logging; public interface Log {}",
                        "package org.apache.commons.logging; public class LogFactory { public static Log getLog(Class<?> c){return null;} public static LogFactory getFactory(){return null;} public static void release(ClassLoader c){} public static void releaseAll(){} }",
                        "package org.apache.commons.logging.impl; public class LogFactoryImpl {}",
                        "package org.apache.commons.logging.impl; public class Jdk14Logger { public Jdk14Logger(String n){} }",
                        "package org.apache.commons.logging.impl; public class SimpleLog { public SimpleLog(String n){} }",
                        "package org.apache.commons.logging.impl; public class SLF4JLogFactory {}",
                        "package org.slf4j.impl; public class StaticLoggerBinder { public static StaticLoggerBinder getSingleton(){return null;} }"));
    }
}
