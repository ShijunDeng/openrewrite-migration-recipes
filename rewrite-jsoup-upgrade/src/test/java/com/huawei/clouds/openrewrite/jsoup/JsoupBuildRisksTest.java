package com.huawei.clouds.openrewrite.jsoup;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class JsoupBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJsoupBuildRisks());
    }

    @Test
    void marksMavenOwnershipAndVariantBoundaries() {
        rewriteRun(xml(UpgradeJsoupDependencyTest.project("<dependencies>" +
                        UpgradeJsoupDependencyTest.depWithoutVersion() +
                        UpgradeJsoupDependencyTest.dep("${external.jsoup}") +
                        UpgradeJsoupDependencyTest.dep("1.20.1") +
                        UpgradeJsoupDependencyTest.dep("1.14.2", "<classifier>tests</classifier>") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertEquals(4, count(out, "<!--~~("), out);
                    assertTrue(out.contains("parent/BOM"), out);
                    assertTrue(out.contains("externally or ambiguously owned"), out);
                    assertTrue(out.contains("outside the workbook"), out);
                    assertTrue(out.contains("Classifier/type variants"), out);
                })));
    }

    @Test
    void marksAndroidTransportAndShadeOnlyWhenDependencyIsVisible() {
        String plugins = "<build><plugins>" +
                "<plugin><groupId>com.android.tools.build</groupId><artifactId>gradle</artifactId><configuration><minSdkVersion>19</minSdkVersion></configuration></plugin>" +
                "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><configuration><argLine>-Djsoup.useHttpClient=false</argLine></configuration></plugin>" +
                "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId><configuration><relocation>org.jsoup</relocation></configuration></plugin>" +
                "</plugins></build>";
        rewriteRun(xml(UpgradeJsoupDependencyTest.project("<dependencies>" + UpgradeJsoupDependencyTest.dep("1.21.1") +
                        "</dependencies>" + plugins), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertEquals(3, count(out, "<!--~~("), out);
                    assertTrue(out.contains("Android API level 21+"), out);
                    assertTrue(out.contains("transport selection"), out);
                    assertTrue(out.contains("multi-release JAR"), out);
                })));
    }

    @Test
    void sameMavenBuildWithoutJsoupDependencyIsNoop() {
        rewriteRun(xml(UpgradeJsoupDependencyTest.project(
                "<build><plugins><plugin><groupId>com.android.tools.build</groupId><artifactId>gradle</artifactId>" +
                "<configuration><minSdkVersion>19</minSdkVersion></configuration></plugin>" +
                "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId>" +
                "<configuration>org.jsoup</configuration></plugin></plugins></build>"), source -> source.path("pom.xml")));
    }

    @Test
    void profileDependencyDoesNotLeakIntoSiblingBuild() {
        String body = "<profiles><profile><id>jsoup</id><dependencies>" + UpgradeJsoupDependencyTest.dep("1.21.1") +
                      "</dependencies></profile><profile><id>sibling</id><build><plugins>" +
                      "<plugin><groupId>com.android.tools.build</groupId><artifactId>gradle</artifactId><configuration><minSdkVersion>19</minSdkVersion></configuration></plugin>" +
                      "</plugins></build></profile></profiles>";
        rewriteRun(xml(UpgradeJsoupDependencyTest.project(body), source -> source.path("pom.xml")));
    }

    @Test
    void rootConfigurationIsRelevantToProfileDependency() {
        String body = "<build><plugins><plugin><groupId>com.android.tools.build</groupId><artifactId>gradle</artifactId>" +
                      "<configuration><minSdkVersion>19</minSdkVersion></configuration></plugin></plugins></build>" +
                      "<profiles><profile><id>jsoup</id><dependencies>" + UpgradeJsoupDependencyTest.dep("1.21.1") +
                      "</dependencies></profile></profiles>";
        rewriteRun(xml(UpgradeJsoupDependencyTest.project(body), source -> source.path("pom.xml").after(actual -> actual)
                .afterRecipe(after -> assertEquals(1, count(after.printAll(), "Android API level 21+"), after.printAll()))));
    }

    @Test
    void marksGradleAndroidTransportShadeAndOwners() {
        rewriteRun(buildGradle(
                """
                android { defaultConfig { minSdkVersion 19 } }
                dependencies {
                    implementation 'org.jsoup:jsoup:1.21.1'
                    implementation 'org.jsoup:jsoup:1.20.1'
                    implementation 'org.jsoup:jsoup:1.14.2:tests'
                }
                tasks.withType(Test).configureEach { jvmArgs '-Djsoup.useHttpClient=false' }
                shadowJar { relocate 'org.jsoup', 'shadow.jsoup' }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertEquals(5, count(out, "/*~~("), out);
                    assertTrue(out.contains("Android API level 21+"), out);
                    assertTrue(out.contains("transport selection"), out);
                    assertTrue(out.contains("Shadow relocation"), out);
                    assertTrue(out.contains("outside the workbook"), out);
                    assertTrue(out.contains("Classifier/type variants"), out);
                })));
    }

    @Test
    void marksKotlinMinSdkButSupportedAndUnrelatedFilesStayClean() {
        rewriteRun(
                buildGradleKts("android { defaultConfig { minSdk = 20 } }\ndependencies { implementation(\"org.jsoup:jsoup:1.21.1\") }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("Android API level 21+")))),
                buildGradle("android { defaultConfig { minSdkVersion 19 } }"),
                buildGradle("android { defaultConfig { minSdkVersion 21 } }\ndependencies { implementation 'org.jsoup:jsoup:1.21.1' }")
        );
    }

    @Test
    void nestedDslAndGeneratedParentsStayClean() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'org.jsoup:jsoup:1.20.1' } }"),
                xml(UpgradeJsoupDependencyTest.pom("1.20.1"), source -> source.path("target/pom.xml")));
    }

    @Test
    void rootDependencyDoesNotOwnSiblingGradleProjectConfiguration() {
        rewriteRun(buildGradle("""
                dependencies { implementation 'org.jsoup:jsoup:1.21.1' }
                subprojects {
                    android { defaultConfig { minSdkVersion 19 } }
                    tasks.withType(Test).configureEach { jvmArgs '-Djsoup.useHttpClient=false' }
                    shadowJar { relocate 'org.jsoup', 'shadow.jsoup' }
                }
                project(':legacy') { android { defaultConfig { minSdkVersion 18 } } }
                """));
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = 0; (i = text.indexOf(needle, i)) >= 0; i += needle.length()) count++;
        return count;
    }
}
