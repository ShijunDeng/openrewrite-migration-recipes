package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class OfficialJUnit6PlatformTypesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterAggregateTestApi.sources()))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitjupiteraggregate")
                        .build()
                        .activateRecipes(
                                "com.huawei.clouds.openrewrite.junitjupiteraggregate." +
                                "MigrateOfficialJUnitJupiter6Leaves"));
    }

    @Test
    void migratesImportedConstructor() {
        rewriteRun(java(
                """
                  import org.junit.platform.commons.util.PreconditionViolationException;
                  class Preconditions { RuntimeException failure() { return new PreconditionViolationException("bad"); } }
                  """,
                """
                  import org.junit.platform.commons.PreconditionViolationException;

                  class Preconditions { RuntimeException failure() { return new PreconditionViolationException("bad"); } }
                  """));
    }

    @Test
    void migratesCatchType() {
        rewriteRun(java(
                """
                  import org.junit.platform.commons.util.PreconditionViolationException;
                  class EngineTest { void run() { try { throw new PreconditionViolationException("bad"); } catch (PreconditionViolationException ignored) {} } }
                  """,
                """
                  import org.junit.platform.commons.PreconditionViolationException;

                  class EngineTest { void run() { try { throw new PreconditionViolationException("bad"); } catch (PreconditionViolationException ignored) {} } }
                  """));
    }

    @Test
    void migratesFullyQualifiedType() {
        rewriteRun(java(
                """
                  class EngineTest {
                      RuntimeException failure() {
                          return new org.junit.platform.commons.util.PreconditionViolationException("bad");
                      }
                  }
                """,
                """
                  import org.junit.platform.commons.PreconditionViolationException;

                  class EngineTest {
                      RuntimeException failure() {
                          return new PreconditionViolationException("bad");
                      }
                  }
                  """));
    }

    @Test
    void maintainedPackageIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.platform.commons.PreconditionViolationException;

                  class EngineTest { RuntimeException failure() { return new PreconditionViolationException("bad"); } }
                  """));
    }

    @Test
    void migratesRemovedBlacklistedUtilityType() {
        rewriteRun(java(
                """
                  import org.junit.platform.commons.util.BlacklistedExceptions;
                  class FailureSupport { void rethrow(Throwable failure) { BlacklistedExceptions.rethrowIfBlacklisted(failure); } }
                  """,
                """
                  import org.junit.platform.commons.util.UnrecoverableExceptions;

                  class FailureSupport { void rethrow(Throwable failure) { UnrecoverableExceptions.rethrowIfUnrecoverable(failure); } }
                  """));
    }

    @Test
    void sameSimpleBusinessExceptionIsNoop() {
        rewriteRun(java(
                "class PreconditionViolationException extends RuntimeException {} class Business { RuntimeException x() { return new PreconditionViolationException(); } }"));
    }

    @Test
    void packageMoveIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                  import org.junit.platform.commons.util.PreconditionViolationException;
                  class EngineTest { RuntimeException failure() { return new PreconditionViolationException("bad"); } }
                  """,
                """
                  import org.junit.platform.commons.PreconditionViolationException;

                  class EngineTest { RuntimeException failure() { return new PreconditionViolationException("bad"); } }
                  """));
    }
}
