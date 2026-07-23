package com.huawei.clouds.openrewrite.orgjson;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class OrgJsonJavaRisksTest implements RewriteTest {
    @Override public void defaults(RecipeSpec spec) { spec.recipe(new FindOrgJsonJavaRisks()).parser(JavaParser.fromJavaVersion().classpath("json")); }

    @Test void marksTextMapBeanTokenerAndPointerConstruction() {
        rewriteRun(java("""
                import java.util.Map;
                import org.json.*;
                class Parse { void x(String text, Map<String,Object> map, Object bean) {
                    new JSONObject(text); new JSONArray(text); new JSONObject(map); new JSONObject(bean);
                    new JSONTokener(text); new JSONPointer("/a~1b");
                } }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(6, count(out, "/*~~("), out);
            assertTrue(out.contains("duplicate keys"), out); assertTrue(out.contains("bean reflection"), out); assertTrue(out.contains("Pointer token escaping"), out);
        })));
    }

    @Test void marksNumericCoercionGraphMutationAndSimilarity() {
        rewriteRun(java("""
                import org.json.*;
                class Data { void x(JSONObject o, JSONArray a) { o.getLong("n"); o.optDouble("n"); a.getBigDecimal(0); o.put("x", Double.NaN); a.put(o); o.similar(a); } }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(6, count(out, "/*~~("), out);
            assertTrue(out.contains("numeric coercion"), out); assertTrue(out.contains("graph mutation"), out); assertTrue(out.contains("similar()"), out);
        })));
    }

    @Test void marksPointerXmlTokenerAndSerializationBoundaries() {
        rewriteRun(java("""
                import org.json.*;
                class Boundary { void x(String input, JSONObject o, JSONTokener t) { o.query("/x"); o.optQuery("/y"); XML.toJSONObject(input); t.nextValue(); o.toString(2); } }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(5, count(out, "/*~~("), out);
            assertTrue(out.contains("XML conversion"), out); assertTrue(out.contains("Low-level tokener"), out); assertTrue(out.contains("Serialization details"), out);
        })));
    }

    @Test void marksHaeBangXmlFixtureFromFixedCommit() {
        // HaeBangProject/HAEBANG@087c93b667006612803721c4d9c27da31f081d98 MapService.java
        rewriteRun(java("""
                import org.json.*;
                class MapServiceFixture { void convert(String xml) { JSONObject json = XML.toJSONObject(xml); JSONObject copy = new JSONObject(json.toString()); JSONArray items = copy.getJSONObject("body").getJSONArray("item"); } }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertTrue(out.contains("XML conversion"), out); assertTrue(out.contains("JSON text parsing"), out); assertTrue(out.contains("Serialization details"), out);
        })));
    }

    @Test void marksCustomJsonStringAndImplementationSubclass() {
        rewriteRun(java("""
                import org.json.*;
                class Value implements JSONString { public String toJSONString() { return "{}"; } }
                class CustomObject extends JSONObject {}
                """, s -> s.after(a -> a).afterRecipe(after -> assertEquals(2, count(after.printAll(), "/*~~("), after.printAll()))));
    }

    @Test void sameNamedApplicationApisAndSimpleTypedReadsStayClean() {
        rewriteRun(java("""
                import org.json.JSONObject;
                class Local { long getLong(String x){return 0;} void put(String x,Object y){} }
                class A { void x(Local l, JSONObject o) { l.getLong("x"); l.put("x", 1); o.getString("s"); o.optString("s"); } }
                """));
    }

    @Test void generatedParentNoopAndLeafIdempotent() {
        rewriteRun(s -> s.cycles(2).expectedCyclesThatMakeChanges(1),
                java("import org.json.JSONObject; class G { Object x = new JSONObject(\"{}\"); }", x -> x.path("target/generated/G.java")),
                java("import org.json.JSONObject; class install { Object x = new JSONObject(\"{}\"); }", x -> x.path("install.java").after(a -> a).afterRecipe(after -> assertEquals(1, count(after.printAll(), "/*~~("), after.printAll()))));
    }

    private static int count(String text, String needle) { int n=0; for(int i=0;(i=text.indexOf(needle,i))>=0;i+=needle.length()) n++; return n; }
}
