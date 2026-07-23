package com.huawei.clouds.openrewrite.guava;

import org.openrewrite.java.JavaParser;

/**
 * Minimal Guava 21 API surface used by migration tests.
 *
 * <p>Keeping these attributed source stubs local avoids putting a vulnerable historical Guava binary on the
 * test runtime classpath while still proving that recipes match Guava owners rather than same-named application
 * methods.</p>
 */
final class Guava21Parser {
    private Guava21Parser() {
    }

    static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package com.google.common.base;
                public abstract class CharMatcher {
                    public static final CharMatcher WHITESPACE = null;
                    public static final CharMatcher BREAKING_WHITESPACE = null;
                    public static final CharMatcher ASCII = null;
                    public static final CharMatcher DIGIT = null;
                    public static final CharMatcher JAVA_DIGIT = null;
                    public static final CharMatcher JAVA_LETTER = null;
                    public static final CharMatcher JAVA_LETTER_OR_DIGIT = null;
                    public static final CharMatcher JAVA_UPPER_CASE = null;
                    public static final CharMatcher JAVA_LOWER_CASE = null;
                    public static final CharMatcher JAVA_ISO_CONTROL = null;
                    public static final CharMatcher INVISIBLE = null;
                    public static final CharMatcher SINGLE_WIDTH = null;
                    public static final CharMatcher ANY = null;
                    public static final CharMatcher NONE = null;
                    public static CharMatcher whitespace() { return null; }
                    public static CharMatcher breakingWhitespace() { return null; }
                    public static CharMatcher ascii() { return null; }
                    public static CharMatcher digit() { return null; }
                    public static CharMatcher javaDigit() { return null; }
                    public static CharMatcher javaLetter() { return null; }
                    public static CharMatcher javaLetterOrDigit() { return null; }
                    public static CharMatcher javaUpperCase() { return null; }
                    public static CharMatcher javaLowerCase() { return null; }
                    public static CharMatcher javaIsoControl() { return null; }
                    public static CharMatcher invisible() { return null; }
                    public static CharMatcher singleWidth() { return null; }
                    public static CharMatcher any() { return null; }
                    public static CharMatcher none() { return null; }
                    public abstract boolean matchesAllOf(CharSequence sequence);
                }
                """,
                """
                package com.google.common.base;
                public final class Splitter {
                    public static Splitter on(String separator) { return null; }
                    public static Splitter on(CharMatcher separator) { return null; }
                }
                """,
                """
                package com.google.common.base;
                public final class Strings {
                    public static String repeat(String string, int count) { return null; }
                }
                """,
                """
                package com.google.common.base;
                public interface Function<F, T> { T apply(F input); }
                """,
                """
                package com.google.common.base;
                public final class Predicates {
                    public static <T> java.util.function.Predicate<T> assignableFrom(Class<?> type) { return null; }
                }
                """,
                """
                package com.google.common.hash;
                public interface HashFunction {}
                """,
                """
                package com.google.common.hash;
                public final class Hashing {
                    public static HashFunction murmur3_32() { return null; }
                }
                """,
                """
                package com.google.common.io;
                public final class Files {
                    public static java.io.File createTempDir() { return null; }
                    public static Object fileTreeTraverser() { return null; }
                }
                """,
                """
                package com.google.common.io;
                public final class MoreFiles {
                    public static Object directoryTreeTraverser() { return null; }
                }
                """,
                """
                package com.google.common.util.concurrent;
                public interface ListenableFuture<V> extends java.util.concurrent.Future<V> {}
                """,
                """
                package com.google.common.util.concurrent;
                public interface FutureCallback<V> {
                    void onSuccess(V result);
                    void onFailure(Throwable failure);
                }
                """,
                """
                package com.google.common.util.concurrent;
                public interface AsyncFunction<I, O> {
                    ListenableFuture<O> apply(I input) throws Exception;
                }
                """,
                """
                package com.google.common.util.concurrent;
                import com.google.common.base.Function;
                import java.util.concurrent.Executor;
                public final class Futures {
                    public static <V> void addCallback(ListenableFuture<V> future, FutureCallback<? super V> callback) {}
                    public static <V> void addCallback(ListenableFuture<V> future, FutureCallback<? super V> callback, Executor executor) {}
                    public static <I, O> ListenableFuture<O> transform(ListenableFuture<I> input, Function<? super I, ? extends O> function) { return null; }
                    public static <I, O> ListenableFuture<O> transform(ListenableFuture<I> input, Function<? super I, ? extends O> function, Executor executor) { return null; }
                    public static <I, O> ListenableFuture<O> transformAsync(ListenableFuture<I> input, AsyncFunction<? super I, ? extends O> function) { return null; }
                    public static <I, O> ListenableFuture<O> transformAsync(ListenableFuture<I> input, AsyncFunction<? super I, ? extends O> function, Executor executor) { return null; }
                    public static <V, X extends Throwable> ListenableFuture<V> catching(ListenableFuture<? extends V> input, Class<X> type, Function<? super X, ? extends V> fallback) { return null; }
                    public static <V, X extends Throwable> ListenableFuture<V> catching(ListenableFuture<? extends V> input, Class<X> type, Function<? super X, ? extends V> fallback, Executor executor) { return null; }
                    public static <V, X extends Throwable> ListenableFuture<V> catchingAsync(ListenableFuture<? extends V> input, Class<X> type, AsyncFunction<? super X, ? extends V> fallback) { return null; }
                    public static <V, X extends Throwable> ListenableFuture<V> catchingAsync(ListenableFuture<? extends V> input, Class<X> type, AsyncFunction<? super X, ? extends V> fallback, Executor executor) { return null; }
                    public static <V> ListenableFuture<V> dereference(ListenableFuture<? extends ListenableFuture<? extends V>> nested) { return null; }
                }
                """,
                """
                package com.google.common.util.concurrent;
                public final class MoreExecutors {
                    public static java.util.concurrent.Executor directExecutor() { return null; }
                }
                """,
                """
                package com.google.common.util.concurrent;
                public final class ServiceManager {
                    public abstract static class Listener {}
                    public void addListener(Listener listener) {}
                    public void addListener(Listener listener, java.util.concurrent.Executor executor) {}
                }
                """,
                """
                package com.google.common.util.concurrent;
                public interface CheckedFuture<V, X extends Exception> extends ListenableFuture<V> {}
                """,
                """
                package com.google.common.collect;
                public abstract class BinaryTreeTraverser<T> {}
                """,
                """
                package com.google.common.graph;
                public interface Graph<N> {}
                """,
                """
                package com.google.common.graph;
                public final class Graphs {
                    public static boolean equivalent(Graph<?> first, Graph<?> second) { return false; }
                }
                """,
                """
                package com.google.common.net;
                public final class HostAndPort {
                    public static HostAndPort fromString(String value) { return null; }
                }
                """
        );
    }
}
