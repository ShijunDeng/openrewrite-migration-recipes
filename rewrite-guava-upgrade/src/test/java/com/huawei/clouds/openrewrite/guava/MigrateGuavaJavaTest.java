package com.huawei.clouds.openrewrite.guava;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateGuavaJavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("guava"));
    }

    @Test
    void migratesJinjavaCharMatcherUsageFromFixedCommit() {
        rewriteRun(
                spec -> spec.recipe(new MigrateCharMatcherConstants())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import com.google.common.base.CharMatcher;
                        import com.google.common.base.Splitter;

                        class SplitFilter {
                            Splitter splitter(String[] args) {
                                if (args.length > 0) {
                                    return Splitter.on(args[0]);
                                }
                                return Splitter.on(CharMatcher.WHITESPACE);
                            }
                        }
                        """,
                        """
                        import com.google.common.base.CharMatcher;
                        import com.google.common.base.Splitter;

                        class SplitFilter {
                            Splitter splitter(String[] args) {
                                if (args.length > 0) {
                                    return Splitter.on(args[0]);
                                }
                                return Splitter.on(CharMatcher.whitespace());
                            }
                        }
                        """
                )
        );
    }

    @Test
    void migratesAllFourteenConstantsIncludingStaticImports() {
        rewriteRun(
                spec -> spec.recipe(new MigrateCharMatcherConstants()),
                java(
                        """
                        import com.google.common.base.CharMatcher;
                        import static com.google.common.base.CharMatcher.WHITESPACE;

                        class Matchers {
                            Object[] all() {
                                return new Object[] {
                                    WHITESPACE,
                                    CharMatcher.BREAKING_WHITESPACE,
                                    CharMatcher.ASCII,
                                    CharMatcher.DIGIT,
                                    CharMatcher.JAVA_DIGIT,
                                    CharMatcher.JAVA_LETTER,
                                    CharMatcher.JAVA_LETTER_OR_DIGIT,
                                    CharMatcher.JAVA_UPPER_CASE,
                                    CharMatcher.JAVA_LOWER_CASE,
                                    CharMatcher.JAVA_ISO_CONTROL,
                                    CharMatcher.INVISIBLE,
                                    CharMatcher.SINGLE_WIDTH,
                                    CharMatcher.ANY,
                                    CharMatcher.NONE
                                };
                            }
                        }
                        """,
                        """
                        import com.google.common.base.CharMatcher;

                        class Matchers {
                            Object[] all() {
                                return new Object[] {
                                        CharMatcher.whitespace(),
                                        CharMatcher.breakingWhitespace(),
                                        CharMatcher.ascii(),
                                        CharMatcher.digit(),
                                        CharMatcher.javaDigit(),
                                        CharMatcher.javaLetter(),
                                        CharMatcher.javaLetterOrDigit(),
                                        CharMatcher.javaUpperCase(),
                                        CharMatcher.javaLowerCase(),
                                        CharMatcher.javaIsoControl(),
                                        CharMatcher.invisible(),
                                        CharMatcher.singleWidth(),
                                        CharMatcher.any(),
                                        CharMatcher.none()
                                };
                            }
                        }
                        """
                )
        );
    }

    @Test
    void recommendedRecipeMigratesSourceAndBuildTogether() {
        rewriteRun(
                spec -> spec.recipe(UpgradeGuavaTest.environment().activateRecipes(
                                "com.huawei.clouds.openrewrite.guava.MigrateGuavaTo33_5_0Jre"))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import com.google.common.base.CharMatcher;

                        class App {
                            boolean blank(String value) {
                                return CharMatcher.WHITESPACE.matchesAllOf(value);
                            }
                        }
                        """,
                        """
                        import com.google.common.base.CharMatcher;

                        class App {
                            boolean blank(String value) {
                                return CharMatcher.whitespace().matchesAllOf(value);
                            }
                        }
                        """
                ),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.google.guava</groupId>
                              <artifactId>guava</artifactId>
                              <version>21.0</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.google.guava</groupId>
                              <artifactId>guava</artifactId>
                              <version>33.5.0-jre</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void migratesBisqAddCallbackUsageFromFixedCommit() {
        rewriteRun(
                spec -> spec.recipe(new AddGuavaDirectExecutor())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import com.google.common.util.concurrent.FutureCallback;
                        import com.google.common.util.concurrent.Futures;
                        import com.google.common.util.concurrent.ListenableFuture;

                        class TxBroadcaster {
                            void broadcast(ListenableFuture<String> future, FutureCallback<String> callback) {
                                Futures.addCallback(future, callback);
                            }
                        }
                        """,
                        """
                        import com.google.common.util.concurrent.FutureCallback;
                        import com.google.common.util.concurrent.Futures;
                        import com.google.common.util.concurrent.ListenableFuture;
                        import com.google.common.util.concurrent.MoreExecutors;

                        class TxBroadcaster {
                            void broadcast(ListenableFuture<String> future, FutureCallback<String> callback) {
                                Futures.addCallback(future, callback, MoreExecutors.directExecutor());
                            }
                        }
                        """
                )
        );
    }

    @Test
    void migratesApacheGobblinServiceManagerListenerFromFixedCommit() {
        rewriteRun(
                spec -> spec.recipe(new AddGuavaDirectExecutor()),
                java(
                        """
                        import com.google.common.util.concurrent.ServiceManager;

                        class ServiceBasedAppLauncher {
                            void register(ServiceManager serviceManager, ServiceManager.Listener listener) {
                                serviceManager.addListener(listener);
                            }
                        }
                        """,
                        """
                        import com.google.common.util.concurrent.MoreExecutors;
                        import com.google.common.util.concurrent.ServiceManager;

                        class ServiceBasedAppLauncher {
                            void register(ServiceManager serviceManager, ServiceManager.Listener listener) {
                                serviceManager.addListener(listener, MoreExecutors.directExecutor());
                            }
                        }
                        """
                )
        );
    }

    @Test
    void migratesEveryRemovedFuturesOverload() {
        rewriteRun(
                spec -> spec.recipe(new AddGuavaDirectExecutor()),
                java(
                        """
                        import com.google.common.base.Function;
                        import com.google.common.util.concurrent.AsyncFunction;
                        import com.google.common.util.concurrent.FutureCallback;
                        import com.google.common.util.concurrent.Futures;
                        import com.google.common.util.concurrent.ListenableFuture;

                        class LegacyFutures {
                            void calls(ListenableFuture<String> strings,
                                       ListenableFuture<Integer> integers,
                                       Function<String, Integer> transform,
                                       AsyncFunction<String, Integer> transformAsync,
                                       Function<Throwable, Integer> fallback,
                                       AsyncFunction<Throwable, Integer> fallbackAsync,
                                       FutureCallback<String> callback) {
                                Futures.addCallback(strings, callback);
                                Futures.transform(strings, transform);
                                Futures.transformAsync(strings, transformAsync);
                                Futures.catching(integers, Throwable.class, fallback);
                                Futures.catchingAsync(integers, Throwable.class, fallbackAsync);
                            }
                        }
                        """,
                        """
                        import com.google.common.base.Function;
                        import com.google.common.util.concurrent.*;

                        class LegacyFutures {
                            void calls(ListenableFuture<String> strings,
                                       ListenableFuture<Integer> integers,
                                       Function<String, Integer> transform,
                                       AsyncFunction<String, Integer> transformAsync,
                                       Function<Throwable, Integer> fallback,
                                       AsyncFunction<Throwable, Integer> fallbackAsync,
                                       FutureCallback<String> callback) {
                                Futures.addCallback(strings, callback, MoreExecutors.directExecutor());
                                Futures.transform(strings, transform, MoreExecutors.directExecutor());
                                Futures.transformAsync(strings, transformAsync, MoreExecutors.directExecutor());
                                Futures.catching(integers, Throwable.class, fallback, MoreExecutors.directExecutor());
                                Futures.catchingAsync(integers, Throwable.class, fallbackAsync, MoreExecutors.directExecutor());
                            }
                        }
                        """
                )
        );
    }

    @Test
    void migratesStaticImportedFuturesOverload() {
        rewriteRun(
                spec -> spec.recipe(new AddGuavaDirectExecutor()),
                java(
                        """
                        import com.google.common.util.concurrent.FutureCallback;
                        import com.google.common.util.concurrent.ListenableFuture;

                        import static com.google.common.util.concurrent.Futures.addCallback;

                        class StaticFutures {
                            void call(ListenableFuture<String> future, FutureCallback<String> callback) {
                                addCallback(future, callback);
                            }
                        }
                        """,
                        """
                        import com.google.common.util.concurrent.FutureCallback;
                        import com.google.common.util.concurrent.ListenableFuture;
                        import com.google.common.util.concurrent.MoreExecutors;

                        import static com.google.common.util.concurrent.Futures.addCallback;

                        class StaticFutures {
                            void call(ListenableFuture<String> future, FutureCallback<String> callback) {
                                addCallback(future, callback, MoreExecutors.directExecutor());
                            }
                        }
                        """
                )
        );
    }

    @Test
    void leavesExecutorOverloadsAndUnrelatedMethodsUnchanged() {
        rewriteRun(
                spec -> spec.recipe(new AddGuavaDirectExecutor()),
                java(
                        """
                        import com.google.common.util.concurrent.FutureCallback;
                        import com.google.common.util.concurrent.Futures;
                        import com.google.common.util.concurrent.ListenableFuture;
                        import java.util.concurrent.Executor;

                        class AlreadyExplicit {
                            void call(ListenableFuture<String> future, FutureCallback<String> callback, Executor executor) {
                                Futures.addCallback(future, callback, executor);
                                addCallback(future, callback);
                            }
                            void addCallback(Object future, Object callback) {}
                        }
                        """
                )
        );
    }
}
