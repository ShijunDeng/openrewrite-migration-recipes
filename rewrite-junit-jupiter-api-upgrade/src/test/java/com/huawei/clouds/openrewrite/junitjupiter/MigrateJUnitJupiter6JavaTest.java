package com.huawei.clouds.openrewrite.junitjupiter;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class MigrateJUnitJupiter6JavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterTestApi.sources()))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath(
                                "com.huawei.clouds.openrewrite.junitjupiter",
                                "org.openrewrite.java",
                                "org.openrewrite.java.testing.junit6")
                        .build()
                        .activateRecipes(
                                "com.huawei.clouds.openrewrite.junitjupiter.MigrateDeterministicJUnitJupiter6Java"));
    }

    @Test
    void renamesClassStoreOverload() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension { Object value(ExtensionContext.Store store) { return store.getOrComputeIfAbsent(Value.class); } }
                  class Value {}
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension { Object value(ExtensionContext.Store store) { return store.computeIfAbsent(Value.class); } }
                  class Value {}
                  """));
    }

    @Test
    void renamesCreatorOverloadFromApacheHiveShape() {
        rewriteRun(java(
                """
                  import java.util.ArrayList;
                  import java.util.List;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class ServerExtension {
                      Object server(ExtensionContext context) {
                          List<String> servers = (List<String>) context.getStore(ExtensionContext.Namespace.GLOBAL)
                                  .getOrComputeIfAbsent(context.getUniqueId(), id -> new ArrayList<String>());
                          return servers;
                      }
                  }
                  """,
                """
                  import java.util.ArrayList;
                  import java.util.List;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class ServerExtension {
                      Object server(ExtensionContext context) {
                          List<String> servers = (List<String>) context.getStore(ExtensionContext.Namespace.GLOBAL)
                                  .computeIfAbsent(context.getUniqueId(), id -> new ArrayList<String>());
                          return servers;
                      }
                  }
                  """));
    }

    @Test
    void renamesTypedCreatorOverload() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      String value(ExtensionContext.Store store) {
                          return store.getOrComputeIfAbsent("key", key -> key.toString(), String.class);
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      String value(ExtensionContext.Store store) {
                          return store.computeIfAbsent("key", key -> key.toString(), String.class);
                      }
                  }
                  """));
    }

    @Test
    void targetStoreMethodsAreNoop() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension { Object value(ExtensionContext.Store store) { return store.computeIfAbsent(Value.class); } }
                  class Value {}
                  """));
    }

    @Test
    void sameNamedBusinessMethodIsNoop() {
        rewriteRun(java("class Store { Object getOrComputeIfAbsent(Class<?> type) { return null; } }"));
    }

    @Test
    void migratesQualifiedAlphanumericFromOpenJmlShape() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.TestMethodOrder;
                  @TestMethodOrder(org.junit.jupiter.api.MethodOrderer.Alphanumeric.class)
                  class AllTests {}
                  """,
                """
                  import org.junit.jupiter.api.TestMethodOrder;
                  @TestMethodOrder(org.junit.jupiter.api.MethodOrderer.MethodName.class)
                  class AllTests {}
                  """, source -> source.afterRecipe(
                        MigrateJUnitJupiter6JavaTest::assertMethodNameTypeMetadata)));
    }

    @Test
    void migratesNestedTypeImport() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
                  import org.junit.jupiter.api.TestMethodOrder;
                  @TestMethodOrder(Alphanumeric.class)
                  class OrderedTests {}
                  """,
                """
                  import org.junit.jupiter.api.MethodOrderer.MethodName;
                  import org.junit.jupiter.api.TestMethodOrder;
                  @TestMethodOrder(MethodName.class)
                  class OrderedTests {}
                  """, source -> source.afterRecipe(
                        MigrateJUnitJupiter6JavaTest::assertMethodNameTypeMetadata)));
    }

    @Test
    void migratesStaticNestedTypeImport() {
        rewriteRun(java(
                """
                  import static org.junit.jupiter.api.MethodOrderer.Alphanumeric;
                  import org.junit.jupiter.api.TestMethodOrder;
                  @TestMethodOrder(Alphanumeric.class)
                  class OrderedTests {}
                  """,
                  """
                  import static org.junit.jupiter.api.MethodOrderer.MethodName;
                  import org.junit.jupiter.api.TestMethodOrder;
                  @TestMethodOrder(MethodName.class)
                  class OrderedTests {}
                  """, source -> source.afterRecipe(
                        MigrateJUnitJupiter6JavaTest::assertMethodNameTypeMetadata)));
    }

    @Test
    void maintainedOrdererIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.MethodOrderer;
                  import org.junit.jupiter.api.TestMethodOrder;
                  @TestMethodOrder(MethodOrderer.MethodName.class)
                  class OrderedTests {}
                  """));
    }

    @Test
    void preservesOldDynamicInterceptorBodyWithMaintainedSignature() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  import org.junit.jupiter.api.extension.InvocationInterceptor;
                  class TimingInterceptor implements InvocationInterceptor {
                      @Override
                      public void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext context) throws Throwable {
                          long started = System.nanoTime();
                          try { invocation.proceed(); } finally { System.out.println(System.nanoTime() - started); }
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  import org.junit.jupiter.api.extension.InvocationInterceptor;

                  class TimingInterceptor implements InvocationInterceptor {
                      @Override
                      public void interceptDynamicTest(Invocation<Void> invocation, DynamicTestInvocationContext invocationContext, ExtensionContext context) throws Throwable {
                          long started = System.nanoTime();
                          try { invocation.proceed(); } finally { System.out.println(System.nanoTime() - started); }
                      }
                  }
                  """));
    }

    @Test
    void maintainedIntellijDynamicInterceptorShapeIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  import org.junit.jupiter.api.extension.InvocationInterceptor;
                  class CollectInvocationsInterceptor implements InvocationInterceptor {
                      @Override
                      public void interceptDynamicTest(Invocation<Void> invocation,
                              DynamicTestInvocationContext invocationContext, ExtensionContext extensionContext) throws Throwable {
                          invocation.proceed();
                      }
                  }
                  """));
    }

    @Test
    void unrelatedDynamicMethodIsNoop() {
        rewriteRun(java("class Business { void interceptDynamicTest(Object invocation, Object context) {} }"));
    }

    @Test
    void generatedJavaIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Generated { Object value(ExtensionContext.Store store) { return store.getOrComputeIfAbsent(Value.class); } }
                  class Value {}
                  """, source -> source.path("build/generated/Generated.java")));
    }

    @Test
    void allDeterministicChangesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                  import org.junit.jupiter.api.MethodOrderer;
                  import org.junit.jupiter.api.TestMethodOrder;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  @TestMethodOrder(MethodOrderer.Alphanumeric.class)
                  class Extension { Object value(ExtensionContext.Store store) { return store.getOrComputeIfAbsent(Value.class); } }
                  class Value {}
                  """,
                """
                  import org.junit.jupiter.api.MethodOrderer;
                  import org.junit.jupiter.api.TestMethodOrder;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  @TestMethodOrder(MethodOrderer.MethodName.class)
                  class Extension { Object value(ExtensionContext.Store store) { return store.computeIfAbsent(Value.class); } }
                  class Value {}
                  """));
    }

    private static void assertMethodNameTypeMetadata(J.CompilationUnit after) {
        boolean[] typed = {false};
        new JavaIsoVisitor<boolean[]>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, boolean[] p) {
                J.Identifier visited = super.visitIdentifier(identifier, p);
                if ("MethodName".equals(visited.getSimpleName()) && TypeUtils.isOfClassType(
                        visited.getType(), "org.junit.jupiter.api.MethodOrderer.MethodName")) {
                    p[0] = true;
                }
                return visited;
            }
        }.visitNonNull(after, typed);
        assertTrue(typed[0], "The official rename must be followed by valid JUnit 6 type metadata");
    }
}
