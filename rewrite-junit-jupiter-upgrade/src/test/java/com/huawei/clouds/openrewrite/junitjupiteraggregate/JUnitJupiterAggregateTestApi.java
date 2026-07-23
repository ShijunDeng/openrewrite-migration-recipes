package com.huawei.clouds.openrewrite.junitjupiteraggregate;

final class JUnitJupiterAggregateTestApi {
    private JUnitJupiterAggregateTestApi() { }

    static String[] sources() {
        return new String[] {
                "package org.junit.jupiter.api; public interface MethodOrderer { " +
                "class MethodName implements MethodOrderer {} @Deprecated class Alphanumeric extends MethodName {} }",
                "package org.junit.jupiter.api; import java.lang.annotation.*; " +
                "@Target(ElementType.TYPE) public @interface TestMethodOrder { Class<? extends MethodOrderer> value(); }",
                "package org.junit.jupiter.api; import java.lang.annotation.*; " +
                "@Target(ElementType.TYPE) public @interface Nested {}",
                "package org.junit.jupiter.api.extension; import java.util.function.Function; " +
                "public interface ExtensionContext { Store getStore(Namespace namespace); String getUniqueId(); " +
                "final class Namespace { public static final Namespace GLOBAL = new Namespace(); } " +
                "interface Store { " +
                "default <V> V getOrComputeIfAbsent(Class<V> type) { return null; } " +
                "default <K,V> Object getOrComputeIfAbsent(K key, Function<? super K,? extends V> creator) { return null; } " +
                "default <K,V> V getOrComputeIfAbsent(K key, Function<? super K,? extends V> creator, Class<V> type) { return null; } " +
                "default <V> V computeIfAbsent(Class<V> type) { return null; } " +
                "default <K,V> Object computeIfAbsent(K key, Function<? super K,? extends V> creator) { return null; } " +
                "default <K,V> V computeIfAbsent(K key, Function<? super K,? extends V> creator, Class<V> type) { return null; } } }",
                "package org.junit.platform.engine.support.store; import java.util.function.Function; " +
                "public class NamespacedHierarchicalStore<N> { " +
                "public <K,V> V getOrComputeIfAbsent(N namespace, K key, Function<? super K,? extends V> creator) { return null; } " +
                "public <K,V> V computeIfAbsent(N namespace, K key, Function<? super K,? extends V> creator) { return null; } }",
                "package org.junit.platform.commons.util; public class PreconditionViolationException extends RuntimeException { " +
                "public PreconditionViolationException(String message) { super(message); } }",
                "package org.junit.platform.commons; public class PreconditionViolationException extends RuntimeException { " +
                "public PreconditionViolationException(String message) { super(message); } }",
                "package org.junit.platform.commons.util; public class BlacklistedExceptions { " +
                "public static void rethrowIfBlacklisted(Throwable throwable){} }",
                "package org.junit.platform.commons.util; public class UnrecoverableExceptions { " +
                "public static void rethrowIfBlacklisted(Throwable throwable){} " +
                "public static void rethrowIfUnrecoverable(Throwable throwable){} }",
                "package org.junit.platform.commons.function; public class Try<T> { " +
                "public java.util.Optional<T> toOptional(){return java.util.Optional.empty();} }",
                "package org.junit.platform.commons.support; public class ReflectionSupport { " +
                "public static java.util.Optional<Class<?>> loadClass(String name){return java.util.Optional.empty();} " +
                "public static org.junit.platform.commons.function.Try<Class<?>> tryToLoadClass(String name){return null;} }",
                "package org.junit.platform.commons.util; public class ReflectionUtils { " +
                "public static java.util.Optional<Object> readFieldValue(java.lang.reflect.Field field, Object target){return java.util.Optional.empty();} " +
                "public static java.lang.reflect.Method getMethod(Class<?> type, String name){return null;} }",
                "package org.junit.platform.engine; public interface ConfigurationParameters { " +
                "int size(); java.util.Set<String> keySet(); }",
                "package org.junit.platform.engine.discovery; public class MethodSelector { " +
                "public String getMethodParameterTypes(){return null;} public String getParameterTypeNames(){return null;} }",
                "package org.junit.platform.engine.discovery; public class NestedMethodSelector { " +
                "public String getMethodParameterTypes(){return null;} public String getParameterTypeNames(){return null;} }",
                "package org.junit.platform.launcher.core; public class LauncherDiscoveryRequestBuilder { " +
                "public LauncherDiscoveryRequestBuilder(){} public static LauncherDiscoveryRequestBuilder request(){return null;} }",
                "package org.junit.platform.engine.reporting; public class ReportEntry { public ReportEntry(){} " +
                "public static ReportEntry from(java.util.Map<String,String> values){return null;} }",
                "package org.junit.platform.engine.support.filter; public class ClasspathScanningSupport {}",
                "package org.junit.platform.engine.support.hierarchical; public class SingleTestExecutor {}",
                "package org.junit.platform.launcher.listeners; public class LegacyReportingUtils {}",
                "package org.junit.platform.suite.api; import java.lang.annotation.*; " +
                "@Target(ElementType.TYPE) public @interface UseTechnicalNames {}",
                "package org.junit.platform.engine; public class UniqueId { public static UniqueId parse(String id){return null;} }",
                "package org.junit.platform.launcher; public class TestIdentifier {}",
                "package org.junit.platform.launcher; public class TestPlan { public void add(TestIdentifier id){} " +
                "public java.util.Set<TestIdentifier> getChildren(String id){return null;} " +
                "public java.util.Set<TestIdentifier> getChildren(org.junit.platform.engine.UniqueId id){return null;} " +
                "public TestIdentifier getTestIdentifier(String id){return null;} " +
                "public TestIdentifier getTestIdentifier(org.junit.platform.engine.UniqueId id){return null;} }",
                "package org.junit.platform.engine; public interface EngineDiscoveryRequest {}",
                "package org.junit.platform.engine; public interface TestEngine {}",
                "package org.junit.platform.launcher; public interface LauncherDiscoveryRequest " +
                "extends org.junit.platform.engine.EngineDiscoveryRequest {}",
                "package org.junit.platform.testkit.engine; " +
                "import org.junit.platform.engine.EngineDiscoveryRequest; import org.junit.platform.engine.TestEngine; " +
                "import org.junit.platform.launcher.LauncherDiscoveryRequest; public class EngineTestKit { " +
                "public static Object execute(String engine, EngineDiscoveryRequest request){return null;} " +
                "public static Object execute(TestEngine engine, EngineDiscoveryRequest request){return null;} " +
                "public static Object execute(String engine, LauncherDiscoveryRequest request){return null;} " +
                "public static Object execute(TestEngine engine, LauncherDiscoveryRequest request){return null;} }",
                "package org.junit.platform.console; public class ConsoleLauncher { " +
                "public static void main(String... args){} public static Object execute(String... args){return null;} }",
                "package org.junit.jupiter.api.io; public @interface TempDir { String SCOPE_PROPERTY_NAME = \"scope\"; }",
                "package org.junit.jupiter.engine; public class Constants { " +
                "public static final String TEMP_DIR_SCOPE_PROPERTY_NAME = \"scope\"; " +
                "public static final String DEFAULT_DISPLAY_NAME_GENERATOR_PROPERTY_NAME = \"display\"; }",
                "package org.junit.jupiter.api; public class Constants { " +
                "public static final String TEMP_DIR_SCOPE_PROPERTY_NAME = \"scope\"; " +
                "public static final String DEFAULT_DISPLAY_NAME_GENERATOR_PROPERTY_NAME = \"display\"; }",
                "package org.junit.jupiter.params; public @interface ParameterizedTest { " +
                "String DISPLAY_NAME_PLACEHOLDER = \"{displayName}\"; String INDEX_PLACEHOLDER = \"{index}\"; " +
                "String ARGUMENTS_PLACEHOLDER = \"{arguments}\"; String ARGUMENTS_WITH_NAMES_PLACEHOLDER = \"{argumentsWithNames}\"; " +
                "String ARGUMENT_SET_NAME_PLACEHOLDER = \"{argumentSetName}\"; " +
                "String ARGUMENT_SET_NAME_OR_ARGUMENTS_WITH_NAMES_PLACEHOLDER = \"{argumentSetNameOrArgumentsWithNames}\"; " +
                "String DEFAULT_DISPLAY_NAME = \"[{index}] {argumentSetNameOrArgumentsWithNames}\"; }",
                "package org.junit.jupiter.params; public final class ParameterizedInvocationConstants { " +
                "public static final String DISPLAY_NAME_PLACEHOLDER = \"{displayName}\"; " +
                "public static final String INDEX_PLACEHOLDER = \"{index}\"; " +
                "public static final String ARGUMENTS_PLACEHOLDER = \"{arguments}\"; " +
                "public static final String ARGUMENTS_WITH_NAMES_PLACEHOLDER = \"{argumentsWithNames}\"; " +
                "public static final String ARGUMENT_SET_NAME_PLACEHOLDER = \"{argumentSetName}\"; " +
                "public static final String ARGUMENT_SET_NAME_OR_ARGUMENTS_WITH_NAMES_PLACEHOLDER = \"{argumentSetNameOrArgumentsWithNames}\"; " +
                "public static final String DEFAULT_DISPLAY_NAME = \"[{index}] {argumentSetNameOrArgumentsWithNames}\"; }",
                "package org.junit.platform.engine.reporting; public interface OutputDirectoryProvider {}",
                "package org.junit.platform.engine; public interface OutputDirectoryCreator {}",
                "package org.junit.platform.commons.support; public interface Resource {}",
                "package org.junit.platform.commons.io; public interface Resource {}",
                "package org.junit.jupiter.api.extension; public interface MediaType {}",
                "package org.junit.jupiter.api; public interface MediaType {}",
                "package org.junit.jupiter.params.support; public interface ParameterInfo {}",
                "package org.junit.jupiter.params; public interface ParameterInfo {}",
                "package org.junit.platform.engine.discovery; public class DiscoverySelectors { " +
                "public static Object selectClasspathResource(java.util.Set<String> resources){return null;} " +
                "public static Object selectClasspathResourceByName(java.util.Set<String> resources){return null;} }",
                "package org.junit.platform.engine.discovery; public class ClasspathResourceSelector { " +
                "public java.util.Set<String> getClasspathResources(){return null;} " +
                "public java.util.Set<String> getResources(){return null;} }",
                "package org.junit.platform.testkit.engine; public class Executions { " +
                "public Executions started(){return this;} public Executions finished(){return this;} }",
                "package org.junit.jupiter.api.extension; public interface DynamicTestInvocationContext {}",
                "package org.junit.jupiter.api.extension; public interface InvocationInterceptor { " +
                "interface Invocation<T> { T proceed() throws Throwable; } " +
                "default void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext context) throws Throwable {} " +
                "default void interceptDynamicTest(Invocation<Void> invocation, DynamicTestInvocationContext invocationContext, " +
                "ExtensionContext context) throws Throwable {} }",
                "package org.junit.jupiter.api.condition; public enum JRE { JAVA_8, JAVA_9, JAVA_10, JAVA_11, " +
                "JAVA_12, JAVA_13, JAVA_14, JAVA_15, JAVA_16, JAVA_17, JAVA_18, JAVA_21, OTHER }",
                "package org.junit.jupiter.api.condition; import java.lang.annotation.*; " +
                "@Target({ElementType.TYPE,ElementType.METHOD}) public @interface EnabledOnJre { JRE[] value() default {}; int[] versions() default {}; String disabledReason() default \"\"; }",
                "package org.junit.jupiter.api.condition; import java.lang.annotation.*; " +
                "@Target({ElementType.TYPE,ElementType.METHOD}) public @interface DisabledOnJre { JRE[] value() default {}; int[] versions() default {}; }",
                "package org.junit.jupiter.api.condition; import java.lang.annotation.*; " +
                "@Target({ElementType.TYPE,ElementType.METHOD}) public @interface EnabledForJreRange { " +
                "JRE min() default JRE.JAVA_8; JRE max() default JRE.OTHER; int minVersion() default -1; int maxVersion() default -1; }",
                "package org.junit.jupiter.api.condition; import java.lang.annotation.*; " +
                "@Target({ElementType.TYPE,ElementType.METHOD}) public @interface DisabledForJreRange { " +
                "JRE min() default JRE.JAVA_8; JRE max() default JRE.OTHER; int minVersion() default -1; int maxVersion() default -1; }",
                "package org.junit.jupiter.params.provider; import java.lang.annotation.*; " +
                "@Target({ElementType.METHOD,ElementType.ANNOTATION_TYPE}) public @interface CsvSource { " +
                "String[] value() default {}; boolean useHeadersInDisplayName() default false; " +
                "boolean ignoreLeadingAndTrailingWhitespace() default true; String[] nullValues() default {}; " +
                "char delimiter() default ','; String delimiterString() default \"\"; char commentCharacter() default '#'; }",
                "package org.junit.jupiter.params.provider; import java.lang.annotation.*; " +
                "@Target({ElementType.METHOD,ElementType.ANNOTATION_TYPE}) public @interface CsvFileSource { " +
                "String[] resources() default {}; String lineSeparator() default \"\\n\"; " +
                "boolean useHeadersInDisplayName() default false; boolean ignoreLeadingAndTrailingWhitespace() default true; " +
                "char delimiter() default ','; String delimiterString() default \"\"; char commentCharacter() default '#'; }"
        };
    }
}
