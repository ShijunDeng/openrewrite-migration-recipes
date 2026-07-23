package com.huawei.clouds.openrewrite.orgjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class OrgJsonBuildRisksTest implements RewriteTest {
    @Override public void defaults(RecipeSpec spec) { spec.recipe(new FindOrgJsonBuildRisks()); }

    @Test void marksMavenOwnershipVariantAndOutsideVersion() {
        rewriteRun(xml(UpgradeOrgJsonDependencyTest.project("<dependencies>" + UpgradeOrgJsonDependencyTest.noVersion() +
                UpgradeOrgJsonDependencyTest.dep("${external}") + UpgradeOrgJsonDependencyTest.dep("20241224") +
                UpgradeOrgJsonDependencyTest.dep("20160810", "<classifier>tests</classifier>") + "</dependencies>"), s -> s.path("pom.xml").after(a -> a).afterRecipe(after -> {
            String out=after.printAll(); assertEquals(4,count(out,"<!--~~("),out); assertTrue(out.contains("parent/BOM"),out); assertTrue(out.contains("outside the workbook"),out);
        })));
    }

    @Test void ownedPropertyIsCleanButSharedPropertyIsMarked() {
        rewriteRun(xml(UpgradeOrgJsonDependencyTest.project("<properties><v>20230227</v></properties><dependencies>" + UpgradeOrgJsonDependencyTest.dep("${v}") + "</dependencies>"), s -> s.path("pom.xml")),
                xml(UpgradeOrgJsonDependencyTest.project("<properties><v>20230227</v></properties><dependencies>" + UpgradeOrgJsonDependencyTest.dep("${v}") + "</dependencies><x>${v}</x>"),
                        s -> s.path("other/pom.xml").after(a -> a).afterRecipe(after -> {
                            String out = after.printAll(); assertEquals(1, count(out, "<!--~~("), out); assertTrue(out.contains("shared outside"), out);
                        })));
    }

    @Test void duplicateTargetPropertyIsMarkedAsAmbiguous() {
        rewriteRun(xml(UpgradeOrgJsonDependencyTest.project("<properties><v>20230227</v><v>20230227</v></properties><dependencies>" +
                UpgradeOrgJsonDependencyTest.dep("${v}") + "</dependencies>"), s -> s.path("pom.xml").after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(1, count(out, "<!--~~("), out); assertTrue(out.contains("ambiguously defined"), out);
        })));
    }

    @Test void marksJava7ShadeBundleAndNativePlugins() {
        String plugins = "<properties><maven.compiler.release>7</maven.compiler.release></properties><build><plugins>" +
                "<plugin><artifactId>maven-shade-plugin</artifactId><configuration>org.json</configuration></plugin>" +
                "<plugin><artifactId>bnd-maven-plugin</artifactId><configuration>org.json</configuration></plugin>" +
                "<plugin><artifactId>native-maven-plugin</artifactId><configuration>org.json.JSONObject</configuration></plugin></plugins></build>";
        rewriteRun(xml(UpgradeOrgJsonDependencyTest.project("<dependencies>" + UpgradeOrgJsonDependencyTest.dep("20250107") + "</dependencies>" + plugins), s -> s.path("pom.xml").after(a -> a).afterRecipe(after -> {
            String out=after.printAll(); assertEquals(4,count(out,"<!--~~("),out); assertTrue(out.contains("require Java 8+"),out); assertEquals(3,count(out,"packaging/reflection"),out);
        })));
    }

    @Test void nestedMavenOwnersAndBuildWithoutDependencyStayClean() {
        rewriteRun(xml(UpgradeOrgJsonDependencyTest.project("<properties><maven.compiler.release>7</maven.compiler.release></properties><build><plugins><plugin><artifactId>maven-shade-plugin</artifactId><configuration>org.json</configuration></plugin></plugins></build>"), s -> s.path("pom.xml")),
                xml(UpgradeOrgJsonDependencyTest.project("<build><plugins><plugin><artifactId>x</artifactId><dependencies>" + UpgradeOrgJsonDependencyTest.dep("20241224") + "</dependencies></plugin></plugins></build>"), s -> s.path("pom.xml")),
                xml(UpgradeOrgJsonDependencyTest.project("<company><dependencies>" + UpgradeOrgJsonDependencyTest.dep("20241224") + "</dependencies></company>"), s -> s.path("pom.xml")));
    }

    @Test void mavenBuildPolicyUsesTheSameDependencyOwner() {
        String profileDependency = "<profiles><profile><id>with-json</id><dependencies>" + UpgradeOrgJsonDependencyTest.dep("20250107") +
                "</dependencies></profile><profile><id>sibling</id><properties><maven.compiler.release>7</maven.compiler.release></properties></profile></profiles>";
        rewriteRun(xml(UpgradeOrgJsonDependencyTest.project(profileDependency), s -> s.path("pom.xml")),
                xml(UpgradeOrgJsonDependencyTest.project("<properties><java.version>7</java.version></properties><profiles><profile><id>with-json</id><dependencies>" +
                        UpgradeOrgJsonDependencyTest.dep("20250107") + "</dependencies></profile></profiles>"),
                        s -> s.path("root-policy/pom.xml").after(a -> a).afterRecipe(after -> {
                            String out = after.printAll(); assertEquals(1, count(out, "<!--~~("), out); assertTrue(out.contains("require Java 8+"), out);
                        })),
                xml(UpgradeOrgJsonDependencyTest.project("<properties><java.version>7</java.version><maven.compiler.release>${java.version}</maven.compiler.release></properties><dependencies>" +
                        UpgradeOrgJsonDependencyTest.dep("20250107") + "</dependencies>"),
                        s -> s.path("property-policy/pom.xml").after(a -> a).afterRecipe(after -> {
                            String out = after.printAll(); assertEquals(2, count(out, "<!--~~("), out);
                        })));
    }

    @Test void mavenVariantDoesNotGateUnrelatedJavaPolicy() {
        rewriteRun(xml(UpgradeOrgJsonDependencyTest.project("<properties><maven.compiler.release>7</maven.compiler.release></properties><dependencies>" +
                UpgradeOrgJsonDependencyTest.dep("20250107", "<classifier>tests</classifier>") + "</dependencies>"),
                s -> s.path("pom.xml").after(a -> a).afterRecipe(after -> {
                    String out = after.printAll(); assertEquals(1, count(out, "<!--~~("), out); assertTrue(out.contains("Classifier/type variants"), out);
                })));
    }

    @Test void marksGradleJavaOutsideVariantAndShadow() {
        rewriteRun(buildGradle("""
                sourceCompatibility = '1.7'
                dependencies { implementation 'org.json:json:20250107'; implementation 'org.json:json:20241224'; runtimeOnly 'org.json:json:20160810:tests' }
                shadowJar { relocate 'org.json', 'shadow.json' }
                """, s -> s.path("build.gradle").after(a -> a).afterRecipe(after -> {
            String out=after.printAll(); assertEquals(4,count(out,"/*~~("),out); assertTrue(out.contains("requires Java 8+"),out); assertTrue(out.contains("relocation"),out);
        })));
    }

    @Test void marksKotlinJavaAndOutsideVersion() {
        rewriteRun(buildGradleKts("java { sourceCompatibility = JavaVersion.VERSION_1_7 }\ndependencies { implementation(\"org.json:json:20241224\") }", s -> s.after(a -> a).afterRecipe(after -> assertEquals(2,count(after.printAll(),"/*~~("),after.printAll()))));
    }

    @Test void nestedGradleDslDoesNotEstablishOrInheritScope() {
        rewriteRun(buildGradle("custom { dependencies { implementation 'org.json:json:20241224' } }; sourceCompatibility='1.7'"),
                buildGradle("dependencies { constraints { implementation 'org.json:json:20241224' } }; sourceCompatibility='1.7'"),
                buildGradle("dependencies { implementation 'org.json:json:20250107' }; custom { sourceCompatibility='1.7'; relocate 'org.json','x' }"));
    }

    @Test void generatedParentsAndSupportedJavaStayClean() {
        rewriteRun(xml(UpgradeOrgJsonDependencyTest.pom("20241224"), s -> s.path("target/pom.xml")),
                buildGradle("sourceCompatibility='1.8'\ndependencies { implementation 'org.json:json:20250107' }"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.", "1.2", "1.2a", "1..RC1", "20250107", "3.11.2-RC1"})
    void preservesAcceptedLiteralVersionForms(String version) {
        assertTrue(FindOrgJsonBuildRisks.literalVersion(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,2)", "1+meta", "latest.release", "1_0", "0!"})
    void rejectsNonLiteralVersionForms(String version) {
        assertFalse(FindOrgJsonBuildRisks.literalVersion(version));
    }

    @Test void rejectsLongInvalidVersionWithoutBacktrackingExplosion() {
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> {
                    assertFalse(FindOrgJsonBuildRisks.literalVersion("0".repeat(100_000) + "!"));
                    assertFalse(FindOrgJsonBuildRisks.literalVersion("1" + ".1".repeat(100_000) + "!"));
                });
    }

    private static int count(String text,String needle){int n=0;for(int i=0;(i=text.indexOf(needle,i))>=0;i+=needle.length())n++;return n;}
}
