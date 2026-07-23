package com.huawei.clouds.openrewrite.trinojdbc;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class TrinoJdbcBuildRisksTest implements RewriteTest {
    @Override public void defaults(RecipeSpec spec) { spec.recipe(new FindTrinoJdbcBuildRisks()); }

    @Test void marksMavenOwnershipVariantAndOutsideVersion() {
        rewriteRun(xml(UpgradeTrinoJdbcDependencyTest.project("<dependencies>" +
                UpgradeTrinoJdbcDependencyTest.noVersion() +
                UpgradeTrinoJdbcDependencyTest.dep("${external}") +
                UpgradeTrinoJdbcDependencyTest.dep("452") +
                UpgradeTrinoJdbcDependencyTest.dep("418", "<classifier>tests</classifier>") +
                UpgradeTrinoJdbcDependencyTest.dep("418", "<type>zip</type>") +
                "</dependencies>"), s -> s.path("pom.xml").after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(5, count(out, "<!--~~("), out);
            assertTrue(out.contains("parent/BOM"), out); assertTrue(out.contains("outside the workbook"), out); assertTrue(out.contains("Classifier/type"), out);
        })));
    }

    @Test void ownedPropertyIsCleanButSharedPropertyIsMarked() {
        rewriteRun(
                xml(UpgradeTrinoJdbcDependencyTest.project("<properties><trino.version>418</trino.version></properties><dependencies>" + UpgradeTrinoJdbcDependencyTest.dep("${trino.version}") + "</dependencies>"), s -> s.path("pom.xml")),
                xml(UpgradeTrinoJdbcDependencyTest.project("<properties><trino.version>418</trino.version></properties><dependencies>" + UpgradeTrinoJdbcDependencyTest.dep("${trino.version}") + "</dependencies><name>${trino.version}</name>"),
                        s -> s.path("shared/pom.xml").after(a -> a).afterRecipe(after -> {
                            String out = after.printAll(); assertEquals(1, count(out, "<!--~~("), out); assertTrue(out.contains("shared outside"), out);
                        })));
    }

    @Test void duplicateAndCompositePropertiesAreMarked() {
        rewriteRun(
                xml(UpgradeTrinoJdbcDependencyTest.project("<properties><v>418</v><v>418</v></properties><dependencies>" + UpgradeTrinoJdbcDependencyTest.dep("${v}") + "</dependencies>"),
                        s -> s.path("pom.xml").after(a -> a).afterRecipe(after -> assertTrue(after.printAll().contains("ambiguously defined"), after.printAll()))),
                xml(UpgradeTrinoJdbcDependencyTest.project("<properties><major>4</major><minor>18</minor></properties><dependencies>" + UpgradeTrinoJdbcDependencyTest.dep("${major}${minor}") + "</dependencies>"),
                        s -> s.path("composite/pom.xml").after(a -> a).afterRecipe(after -> assertTrue(after.printAll().contains("composite"), after.printAll()))));
    }

    @Test void marksJava7AndShadedOrNativeDriverPackaging() {
        String plugins = "<properties><maven.compiler.release>7</maven.compiler.release></properties><build><plugins>" +
                "<plugin><artifactId>maven-shade-plugin</artifactId><configuration>io.trino.jdbc</configuration></plugin>" +
                "<plugin><artifactId>native-maven-plugin</artifactId><configuration>trino-jdbc</configuration></plugin></plugins></build>";
        rewriteRun(xml(UpgradeTrinoJdbcDependencyTest.project("<dependencies>" + UpgradeTrinoJdbcDependencyTest.dep("453") + "</dependencies>" + plugins),
                s -> s.path("pom.xml").after(a -> a).afterRecipe(after -> {
                    String out = after.printAll(); assertEquals(3, count(out, "<!--~~("), out); assertTrue(out.contains("Java 8 bytecode"), out); assertEquals(2, count(out, "already a shaded driver"), out);
                })));
    }

    @Test void resolvesIndirectMavenJavaPropertiesInProjectAndCompilerPlugin() {
        String property = "<properties><baseline>7</baseline><maven.compiler.release>${baseline}</maven.compiler.release></properties>";
        String plugin = "<properties><baseline>1.7</baseline></properties><build><plugins><plugin>" +
                "<artifactId>maven-compiler-plugin</artifactId><configuration><release>${baseline}</release></configuration>" +
                "</plugin></plugins></build>";
        rewriteRun(
                xml(UpgradeTrinoJdbcDependencyTest.project("<dependencies>" + UpgradeTrinoJdbcDependencyTest.dep("453") +
                        "</dependencies>" + property), s -> s.path("pom.xml").after(a -> a).afterRecipe(after ->
                        assertEquals(1, count(after.printAll(), "<!--~~("), after.printAll()))),
                xml(UpgradeTrinoJdbcDependencyTest.project("<dependencies>" + UpgradeTrinoJdbcDependencyTest.dep("453") +
                        "</dependencies>" + plugin), s -> s.path("plugin/pom.xml").after(a -> a).afterRecipe(after ->
                        assertEquals(1, count(after.printAll(), "<!--~~("), after.printAll()))));
    }

    @Test void similarlyNamedMavenPluginIsNotARecognizedPackagingBoundary() {
        String plugin = "<build><plugins><plugin><artifactId>company-shade-helper</artifactId>" +
                "<configuration>io.trino.jdbc</configuration></plugin></plugins></build>";
        rewriteRun(xml(UpgradeTrinoJdbcDependencyTest.project("<dependencies>" +
                UpgradeTrinoJdbcDependencyTest.dep("453") + "</dependencies>" + plugin), s -> s.path("pom.xml")));
    }

    @Test void buildPolicyUsesSameMavenOwnerScope() {
        String sibling = "<profiles><profile><id>trino</id><dependencies>" + UpgradeTrinoJdbcDependencyTest.dep("453") +
                "</dependencies></profile><profile><id>sibling</id><properties><maven.compiler.release>7</maven.compiler.release></properties></profile></profiles>";
        String root = "<properties><java.version>7</java.version></properties><profiles><profile><id>trino</id><dependencies>" +
                UpgradeTrinoJdbcDependencyTest.dep("453") + "</dependencies></profile></profiles>";
        rewriteRun(
                xml(UpgradeTrinoJdbcDependencyTest.project(sibling), s -> s.path("pom.xml")),
                xml(UpgradeTrinoJdbcDependencyTest.project(root), s -> s.path("root/pom.xml").after(a -> a).afterRecipe(after -> assertEquals(1, count(after.printAll(), "<!--~~("), after.printAll()))));
    }

    @Test void variantDoesNotGateUnrelatedJavaPolicy() {
        rewriteRun(xml(UpgradeTrinoJdbcDependencyTest.project("<properties><maven.compiler.release>7</maven.compiler.release></properties><dependencies>" +
                UpgradeTrinoJdbcDependencyTest.dep("453", "<classifier>tests</classifier>") + "</dependencies>"),
                s -> s.path("pom.xml").after(a -> a).afterRecipe(after -> {
                    String out = after.printAll(); assertEquals(1, count(out, "<!--~~("), out); assertTrue(out.contains("Classifier/type"), out);
                })));
    }

    @Test void marksGradleJavaOutsideDynamicVariantVersionlessAndShadow() {
        rewriteRun(buildGradle("""
                sourceCompatibility = '1.7'
                dependencies {
                    implementation 'io.trino:trino-jdbc:453'
                    implementation 'io.trino:trino-jdbc:452'
                    runtimeOnly 'io.trino:trino-jdbc'
                    testImplementation 'io.trino:trino-jdbc:4+'
                    compileOnly 'io.trino:trino-jdbc:418:tests'
                }
                shadowJar { relocate 'io.trino.jdbc', 'shadow.trino' }
                """, s -> s.path("build.gradle").after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(6, count(out, "/*~~("), out); assertTrue(out.contains("requires Java 8+"), out); assertTrue(out.contains("java.sql.Driver service"), out);
        })));
    }

    @Test void marksGradleMapVariantAndOutsideVersion() {
        rewriteRun(buildGradle("""
                dependencies {
                    implementation group: 'io.trino', name: 'trino-jdbc', version: '452'
                    runtimeOnly([group: 'io.trino', name: 'trino-jdbc', version: '418', classifier: 'tests'])
                }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(2, count(out, "/*~~("), out); assertTrue(out.contains("outside the workbook"), out); assertTrue(out.contains("Classifier/type"), out);
        })));
    }

    @Test void marksKotlinJavaAndOutsideVersion() {
        rewriteRun(buildGradleKts("java { sourceCompatibility = JavaVersion.VERSION_1_7 }\ndependencies { implementation(\"io.trino:trino-jdbc:452\") }",
                s -> s.after(a -> a).afterRecipe(after -> assertEquals(2, count(after.printAll(), "/*~~("), after.printAll()))));
    }

    @Test void dynamicStandardGradleOwnersGateCompanionAuditsButDynamicVariantsDoNot() {
        rewriteRun(
                buildGradle("""
                        def trinoVersion = '453'
                        sourceCompatibility = '1.7'
                        dependencies { implementation "io.trino:trino-jdbc:$trinoVersion" }
                        shadowJar { relocate 'io.trino.jdbc', 'shadow.trino' }
                        """, s -> s.path("build.gradle").after(a -> a).afterRecipe(after -> {
                    String out = after.printAll();
                    assertEquals(3, count(out, "/*~~("), out);
                    assertTrue(out.contains("externally/dynamically owned"), out);
                    assertTrue(out.contains("requires Java 8+"), out);
                })),
                buildGradleKts("""
                        val trinoVersion = "453"
                        sourceCompatibility = JavaVersion.VERSION_1_7
                        dependencies { implementation("io.trino:trino-jdbc:$trinoVersion") }
                        """, s -> s.path("build.gradle.kts").after(a -> a).afterRecipe(after ->
                        assertEquals(2, count(after.printAll(), "/*~~("), after.printAll()))),
                buildGradle("""
                        def trinoVersion = '418'
                        sourceCompatibility = '1.7'
                        dependencies { implementation "io.trino:trino-jdbc:$trinoVersion:tests" }
                        """, s -> s.path("variant.gradle").after(a -> a).afterRecipe(after -> {
                    String out = after.printAll();
                    assertEquals(1, count(out, "/*~~("), out);
                    assertTrue(out.contains("Classifier/type variants"), out);
                })));
    }

    @Test void nestedGradleDslDoesNotEstablishOrInheritScope() {
        rewriteRun(
                buildGradle("custom { dependencies { implementation 'io.trino:trino-jdbc:452' } }; sourceCompatibility='1.7'"),
                buildGradle("dependencies { constraints { implementation 'io.trino:trino-jdbc:452' } }; sourceCompatibility='1.7'"),
                buildGradle("dependencies { implementation 'io.trino:trino-jdbc:453' }; custom { sourceCompatibility='1.7'; relocate 'io.trino.jdbc','x' }"));
    }

    @Test void unrelatedBuildGeneratedParentsAndSupportedJavaStayClean() {
        rewriteRun(
                xml(UpgradeTrinoJdbcDependencyTest.project("<properties><maven.compiler.release>7</maven.compiler.release></properties><build><plugins><plugin><artifactId>maven-shade-plugin</artifactId><configuration>io.trino.jdbc</configuration></plugin></plugins></build>"), s -> s.path("pom.xml")),
                xml(UpgradeTrinoJdbcDependencyTest.pom("452"), s -> s.path("target/pom.xml")),
                buildGradle("sourceCompatibility='1.8'\ndependencies { implementation 'io.trino:trino-jdbc:453' }"));
    }

    private static int count(String text, String needle) {
        int n = 0; for (int i = 0; (i = text.indexOf(needle, i)) >= 0; i += needle.length()) n++; return n;
    }
}
