package com.huawei.clouds.openrewrite.jakartael;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindJakartaEl6SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJakartaEl6SourceRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(JakartaElTestApi.legacySources()));
    }

    @Test
    void marksRemovedFeatureDescriptorOverrideFromRealResolverPatterns() {
        rewriteRun(java(resolver(""), source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6SourceRisks.FEATURE_DESCRIPTORS))));
    }

    @Test
    void marksRemovedFeatureDescriptorInvocation() {
        rewriteRun(java("""
                import javax.el.*;
                class Tooling { Object features(ELResolver resolver, ELContext context) {
                    return resolver.getFeatureDescriptors(context, null);
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6SourceRisks.FEATURE_DESCRIPTORS))));
    }

    @ParameterizedTest(name = "removed constant {0}")
    @ValueSource(strings = {"TYPE", "RESOLVABLE_AT_DESIGN_TIME"})
    void marksRemovedResolverConstants(String constant) {
        rewriteRun(java("import javax.el.ELResolver; class Tooling { String key = ELResolver." + constant + "; }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJakartaEl6SourceRisks.FEATURE_DESCRIPTORS))));
    }

    @Test
    void marksStaticImportedRemovedConstant() {
        rewriteRun(java("import static javax.el.ELResolver.TYPE; class Tooling { String key = TYPE; }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJakartaEl6SourceRisks.FEATURE_DESCRIPTORS))));
    }

    @Test
    void marksLegacyObjectConversionOverrideFromFlowablePattern() {
        rewriteRun(java(resolver("""
                @Override public Object convertToType(ELContext context, Object value, Class<?> targetType) {
                    if (targetType == String.class) { context.setPropertyResolved(true); return String.valueOf(value); }
                    return null;
                }
                """), source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6SourceRisks.CONVERT_TO_TYPE))));
    }

    @ParameterizedTest(name = "array length expression {0}")
    @ValueSource(strings = {"${items.length}", "#{items['length']}", "${items[\"length\"]}"})
    void marksExpressionFactoryArrayLengthBehavior(String expression) {
        rewriteRun(java("""
                import javax.el.*;
                class Expressions { ValueExpression parse(ExpressionFactory f, ELContext c) {
                    return f.createValueExpression(c, "%s", Object.class);
                } }
                """.formatted(expression.replace("\\", "\\\\").replace("\"", "\\\"")),
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJakartaEl6SourceRisks.ARRAY_LENGTH))));
    }

    @Test
    void marksElProcessorArrayLengthBehavior() {
        rewriteRun(java("import javax.el.ELProcessor; class C { Object f(ELProcessor p){ return p.eval(\"${array.length}\"); } }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJakartaEl6SourceRisks.ARRAY_LENGTH))));
    }

    @Test
    void marksDefaultRecordResolverActivation() {
        rewriteRun(java("""
                import javax.el.*;
                class Contexts { StandardELContext create(ExpressionFactory factory) {
                    return new StandardELContext(factory);
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6SourceRisks.RECORD))));
    }

    @ParameterizedTest(name = "module-sensitive import API {0}")
    @ValueSource(strings = {"importClass", "importPackage", "importStatic", "resolveClass", "resolveStatic"})
    void marksModuleSensitiveImportHandlerCalls(String method) {
        rewriteRun(java("""
                import javax.el.ImportHandler;
                class Imports { Object configure(ImportHandler handler) {
                    handler.%s("com.example.Model");
                    return null;
                } }
                """.formatted(method), source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6SourceRisks.MODULE_ACCESS))));
    }

    @Test
    void marksExpressionFactoryDiscovery() {
        rewriteRun(java("import javax.el.ExpressionFactory; class Bootstrap { Object f = ExpressionFactory.newInstance(); }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJakartaEl6SourceRisks.FACTORY))));
    }

    @Test
    void marksSerializedExpressionWriteBoundary() {
        rewriteRun(java("""
                import java.io.*;
                import javax.el.ValueExpression;
                class Cache { void save(ObjectOutputStream out, ValueExpression expression) throws Exception {
                    out.writeObject(expression);
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6SourceRisks.SERIALIZATION))));
    }

    @Test
    void marksSerializedExpressionReadBoundary() {
        rewriteRun(java("""
                import java.io.*;
                import javax.el.ValueExpression;
                class Cache { ValueExpression load(ObjectInputStream in) throws Exception {
                    return (ValueExpression) in.readObject();
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6SourceRisks.SERIALIZATION))));
    }

    @Test
    void ordinaryTargetApisAreNoop() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(JakartaElTestApi.targetSources())),
                java("""
                import jakarta.el.*;
                class Normal { Object value(ValueExpression expression, ELContext context) {
                    return expression.getValue(context);
                } }
                """, source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void unrelatedLengthLiteralAndApplicationMethodsAreNoop() {
        rewriteRun(java("class C { String sql = \"select array.length from values\"; void importClass(String n){} }",
                source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void generatedSourcesAreNoop() {
        rewriteRun(java("import javax.el.ExpressionFactory; class C { Object f = ExpressionFactory.newInstance(); }",
                source -> source.path("build/generated/C.java").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("import javax.el.ExpressionFactory; class C { Object f = ExpressionFactory.newInstance(); }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindJakartaEl6SourceRisks.FACTORY, 1))));
    }

    private static String resolver(String extra) {
        return """
                import javax.el.*;
                class CustomResolver extends ELResolver {
                    public Object getValue(ELContext c,Object b,Object p){return null;}
                    public Class<?> getType(ELContext c,Object b,Object p){return null;}
                    public void setValue(ELContext c,Object b,Object p,Object v){}
                    public boolean isReadOnly(ELContext c,Object b,Object p){return false;}
                    public Class<?> getCommonPropertyType(ELContext c,Object b){return null;}
                    public java.util.Iterator<java.beans.FeatureDescriptor> getFeatureDescriptors(ELContext c,Object b){return null;}
                    %s
                }
                """.formatted(extra);
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~("), actual);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
