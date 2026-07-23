package com.huawei.clouds.openrewrite.commonspool2;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class CommonsPool2BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindCommonsPool2BuildRisks());
    }

    @Test
    void marksMavenOwnershipVariantAndWhitelistBoundaries() {
        rewriteRun(xml(UpgradeCommonsPool2DependencyTest.project("<dependencies>" +
                        UpgradeCommonsPool2DependencyTest.depWithoutVersion() +
                        UpgradeCommonsPool2DependencyTest.dep("${external.pool}") +
                        UpgradeCommonsPool2DependencyTest.dep("2.12.1") +
                        UpgradeCommonsPool2DependencyTest.dep("2.6.0", "<classifier>tests</classifier>") + "</dependencies>"),
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
    void resolvesOwnedRootAndProfilePropertiesWithoutFalseMarkers() {
        rewriteRun(xml(UpgradeCommonsPool2DependencyTest.project("<properties><pool.version>2.8.1</pool.version></properties>" +
                "<dependencies>" + UpgradeCommonsPool2DependencyTest.dep("${pool.version}") + "</dependencies>" +
                "<profiles><profile><id>it</id><properties><pool.version>2.11.1</pool.version></properties>" +
                "<dependencies>" + UpgradeCommonsPool2DependencyTest.dep("${pool.version}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void duplicateVersionPropertyIsMarkedAsAmbiguousOwnership() {
        rewriteRun(xml(UpgradeCommonsPool2DependencyTest.project(
                "<properties><pool.version>2.13.1</pool.version><pool.version>2.13.1</pool.version></properties>" +
                "<dependencies>" + UpgradeCommonsPool2DependencyTest.dep("${pool.version}") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("externally or ambiguously owned"), after::printAll))));
    }

    @Test
    void marksJava7ShadeAndBundleOnlyWhenDependencyIsVisible() {
        String build = "<properties><maven.compiler.release>7</maven.compiler.release></properties><build><plugins>" +
                "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId><configuration><relocation>org.apache.commons.pool2</relocation></configuration></plugin>" +
                "<plugin><groupId>biz.aQute.bnd</groupId><artifactId>bnd-maven-plugin</artifactId><configuration><Export-Package>org.apache.commons.pool2.*</Export-Package></configuration></plugin>" +
                "</plugins></build>";
        rewriteRun(xml(UpgradeCommonsPool2DependencyTest.project("<dependencies>" +
                        UpgradeCommonsPool2DependencyTest.dep("2.13.1") + "</dependencies>" + build),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertEquals(3, count(out, "<!--~~("), out);
                    assertTrue(out.contains("requires Java 8+"), out);
                    assertEquals(2, count(out, "preserve Automatic-Module-Name"), out);
                })));
    }

    @Test
    void marksCompilerPluginSourceButNotUnrelatedSourceElements() {
        rewriteRun(xml(UpgradeCommonsPool2DependencyTest.project("<dependencies>" + UpgradeCommonsPool2DependencyTest.dep("2.9.0") +
                        "</dependencies><source>7</source><build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                        "<artifactId>maven-compiler-plugin</artifactId><configuration><source>1.7</source><target>8</target></configuration>" +
                        "</plugin></plugins></build>"), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertEquals(1, count(out, "requires Java 8+"), out);
                })));
    }

    @Test
    void marksJavaVersionPropertyButNotGenericSourceProperty() {
        rewriteRun(xml(UpgradeCommonsPool2DependencyTest.project(
                "<properties><java.version>7</java.version><source>7</source></properties><dependencies>" +
                UpgradeCommonsPool2DependencyTest.dep("2.13.1") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertEquals(1, count(after.printAll(), "requires Java 8+"), after.printAll()))));
    }

    @Test
    void sameMavenBuildWithoutDependencyAndNestedOwnersAreNoop() {
        rewriteRun(
                xml(UpgradeCommonsPool2DependencyTest.project("<properties><maven.compiler.release>7</maven.compiler.release></properties>" +
                        "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId><configuration>org.apache.commons.pool2</configuration></plugin></plugins></build>"),
                        source -> source.path("pom.xml")),
                xml(UpgradeCommonsPool2DependencyTest.project("<build><plugins><plugin><artifactId>x</artifactId><dependencies>" +
                        UpgradeCommonsPool2DependencyTest.dep("2.12.1") + "</dependencies></plugin></plugins></build>"), source -> source.path("pom.xml")),
                xml(UpgradeCommonsPool2DependencyTest.project("<company><dependencies>" +
                        UpgradeCommonsPool2DependencyTest.dep("2.12.1") + "</dependencies></company>"), source -> source.path("pom.xml")),
                xml(UpgradeCommonsPool2DependencyTest.project("<dependencies>" + UpgradeCommonsPool2DependencyTest.dep("2.13.1") +
                        "</dependencies><company><build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>" +
                        "<configuration><source>7</source></configuration></plugin><plugin><artifactId>maven-shade-plugin</artifactId>" +
                        "<configuration>org.apache.commons.pool2</configuration></plugin></plugins></build></company>"), source -> source.path("pom.xml")));
    }

    @Test
    void profileDependencyDoesNotLeakIntoSiblingBuild() {
        String body = "<profiles><profile><id>pool</id><dependencies>" + UpgradeCommonsPool2DependencyTest.dep("2.13.1") +
                      "</dependencies></profile><profile><id>sibling</id><properties><maven.compiler.release>7</maven.compiler.release></properties>" +
                      "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId><configuration>org.apache.commons.pool2</configuration></plugin></plugins></build>" +
                      "</profile></profiles>";
        rewriteRun(xml(UpgradeCommonsPool2DependencyTest.project(body), source -> source.path("pom.xml")));
    }

    @Test
    void rootBuildIsRelevantToProfileDependency() {
        String body = "<properties><maven.compiler.target>1.7</maven.compiler.target></properties>" +
                      "<profiles><profile><id>pool</id><dependencies>" + UpgradeCommonsPool2DependencyTest.dep("2.13.1") +
                      "</dependencies></profile></profiles>";
        rewriteRun(xml(UpgradeCommonsPool2DependencyTest.project(body), source -> source.path("pom.xml").after(actual -> actual)
                .afterRecipe(after -> assertEquals(1, count(after.printAll(), "requires Java 8+"), after.printAll()))));
    }

    @Test
    void marksGradleJava7ShadeOwnersAndVariants() {
        rewriteRun(buildGradle("""
                sourceCompatibility = '1.7'
                dependencies {
                    implementation 'org.apache.commons:commons-pool2:2.13.1'
                    implementation 'org.apache.commons:commons-pool2:2.12.1'
                    implementation 'org.apache.commons:commons-pool2:2.6.0:tests'
                    runtimeOnly([group: 'org.apache.commons', name: 'commons-pool2'])
                }
                shadowJar { relocate 'org.apache.commons.pool2', 'shadow.pool2' }
                """, source -> source.path("build.gradle").after(actual -> actual).afterRecipe(after -> {
            String out = after.printAll();
            assertEquals(5, count(out, "/*~~("), out);
            assertTrue(out.contains("requires Java 8+"), out);
            assertTrue(out.contains("package relocation"), out);
            assertTrue(out.contains("outside the workbook"), out);
            assertTrue(out.contains("Classifier/type variants"), out);
            assertTrue(out.contains("versionless"), out);
        })));
    }

    @Test
    void marksKotlinCompatibilityAndOutsideOwner() {
        rewriteRun(buildGradleKts("""
                java { sourceCompatibility = JavaVersion.VERSION_1_7 }
                dependencies { implementation("org.apache.commons:commons-pool2:2.12.1") }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String out = after.printAll();
            assertEquals(2, count(out, "/*~~("), out);
            assertTrue(out.contains("requires Java 8+"), out);
            assertTrue(out.contains("outside the workbook"), out);
        })));
    }

    @Test
    void nestedGradleDslDoesNotEstablishDependencyVisibility() {
        rewriteRun(
                buildGradle("custom { dependencies { implementation 'org.apache.commons:commons-pool2:2.12.1' } }; sourceCompatibility = '1.7'"),
                buildGradle("dependencies { constraints { implementation 'org.apache.commons:commons-pool2:2.12.1' } }; sourceCompatibility = '1.7'"),
                buildGradle("project(':app') { dependencies { implementation 'org.apache.commons:commons-pool2:2.12.1' } }; sourceCompatibility = '1.7'"));
    }

    @Test
    void nestedGradleJavaAndRelocationDslStayCleanWithRootDependency() {
        rewriteRun(buildGradle("""
                dependencies { implementation 'org.apache.commons:commons-pool2:2.13.1' }
                custom { sourceCompatibility = '1.7'; relocate 'org.apache.commons.pool2', 'shadow.pool2' }
                tasks { shadowJar { relocate 'org.apache.commons.pool2', 'shadow.pool2' } }
                """));
    }

    @Test
    void marksDirectGroovyMapOwnerAndVariantForms() {
        rewriteRun(buildGradle("""
                dependencies {
                    implementation group: 'org.apache.commons', name: 'commons-pool2', version: '2.12.1'
                    runtimeOnly group: 'org.apache.commons', name: 'commons-pool2', version: '2.6.0', classifier: 'tests'
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String out = after.printAll();
            assertEquals(2, count(out, "/*~~("), out);
            assertTrue(out.contains("outside the workbook"), out);
            assertTrue(out.contains("Classifier/type variants"), out);
        })));
    }

    @Test
    void supportedJavaAndGeneratedParentsStayClean() {
        rewriteRun(
                buildGradle("sourceCompatibility = '1.8'\ndependencies { implementation 'org.apache.commons:commons-pool2:2.13.1' }"),
                buildGradle("sourceCompatibility = JavaVersion.VERSION_17\ndependencies { implementation 'org.apache.commons:commons-pool2:2.13.1' }"),
                xml(UpgradeCommonsPool2DependencyTest.pom("2.12.1"), source -> source.path("target/pom.xml")));
    }

    @Test
    void twoCyclesDoNotDuplicateMarkers() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeCommonsPool2DependencyTest.pom("2.12.1"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> assertEquals(1, count(after.printAll(), "<!--~~("), after.printAll()))));
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = 0; (i = text.indexOf(needle, i)) >= 0; i += needle.length()) count++;
        return count;
    }
}
