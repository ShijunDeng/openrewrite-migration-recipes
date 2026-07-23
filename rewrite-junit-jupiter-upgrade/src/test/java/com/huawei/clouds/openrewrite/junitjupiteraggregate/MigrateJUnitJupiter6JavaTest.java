package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJUnitJupiter6JavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterAggregateTestApi.sources()))
                .recipe(new MigrateJUnitJupiter6Java());
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
                  import org.junit.jupiter.api.extension.InvocationInterceptor;
                  class Generated implements InvocationInterceptor {
                      public void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext context) {}
                  }
                  """, source -> source.path("build/generated/Generated.java")));
    }

    @Test
    void bodyPreservingGapIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  import org.junit.jupiter.api.extension.InvocationInterceptor;
                  class TimingInterceptor implements InvocationInterceptor {
                      public void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext context) throws Throwable {
                          invocation.proceed();
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  import org.junit.jupiter.api.extension.InvocationInterceptor;

                  class TimingInterceptor implements InvocationInterceptor {
                      public void interceptDynamicTest(Invocation<Void> invocation, DynamicTestInvocationContext invocationContext, ExtensionContext context) throws Throwable {
                          invocation.proceed();
                      }
                  }
                  """));
    }
}
