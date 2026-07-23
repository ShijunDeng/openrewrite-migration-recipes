package com.huawei.clouds.openrewrite.commonscli;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class CommonsCliBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindCommonsCliBuildRisks());
    }

    @Test
    void marksJavaBelowEightButNotSupportedBaselines() {
        rewriteRun(xml(
                UpgradeCommonsCliDependencyTest.project("<properties><java.version>1.7</java.version>" +
                        "<maven.compiler.release>8</maven.compiler.release><maven.compiler.target>17</maven.compiler.target></properties>" +
                        "<dependencies>" + UpgradeCommonsCliDependencyTest.dep("1.9.0") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(1, count(printed, "requires Java 8+"), printed);
                    assertTrue(printed.contains("<!--~~("), printed);
                })));
    }

    @Test
    void resolvesRootAndProfilePropertyOwnersWithoutFalseMarks() {
        rewriteRun(xml(
                UpgradeCommonsCliDependencyTest.project("<properties><cli.version>1.9.0</cli.version></properties>" +
                        "<profiles><profile><id>ok</id><dependencies>" +
                        UpgradeCommonsCliDependencyTest.dep("${cli.version}") +
                        "</dependencies></profile><profile><id>override</id><properties><cli.version>1.5.0</cli.version></properties>" +
                        "<dependencies>" + UpgradeCommonsCliDependencyTest.dep("${cli.version}") +
                        "</dependencies></profile></profiles>"), source -> source.path("pom.xml").afterRecipe(after ->
                        assertFalse(after.printAll().contains("/*~~("), after.printAll()))));
    }

    @Test
    void marksExactMavenOwnershipAndVariantBoundaries() {
        rewriteRun(xml(
                UpgradeCommonsCliDependencyTest.project("<dependencies>" +
                        UpgradeCommonsCliDependencyTest.depWithoutVersion() +
                        UpgradeCommonsCliDependencyTest.dep("${external.cli}") +
                        UpgradeCommonsCliDependencyTest.dep("1.8.0") +
                        UpgradeCommonsCliDependencyTest.dep("1.5.0", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(4, count(printed, "<!--~~("), printed);
                    assertTrue(printed.contains("parent/BOM"), printed);
                    assertTrue(printed.contains("externally or ambiguously owned"), printed);
                    assertTrue(printed.contains("outside the workbook"), printed);
                    assertTrue(printed.contains("Classifier/type variants"), printed);
                })));
    }

    @Test
    void marksOldModuleArgumentShadeAndNativeImagePlugins() {
        String shade = "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId>" +
                       "<configuration><relocations><relocation><pattern>org.apache.commons.cli</pattern></relocation></relocations></configuration></plugin>";
        String nativePlugin = "<plugin><groupId>org.graalvm.buildtools</groupId><artifactId>native-maven-plugin</artifactId></plugin>";
        rewriteRun(xml(
                UpgradeCommonsCliDependencyTest.project("<dependencies>" + UpgradeCommonsCliDependencyTest.dep("1.9.0") +
                        "</dependencies><build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                        "<artifactId>maven-compiler-plugin</artifactId><configuration><compilerArgs><arg>--add-modules=commons.cli</arg>" +
                        "</compilerArgs></configuration></plugin>" + shade + nativePlugin + "</plugins></build>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(3, count(printed, "<!--~~("), printed);
                    assertTrue(printed.contains("explicit JPMS module"), printed);
                    assertTrue(printed.contains("shaded/relocated"), printed);
                    assertTrue(printed.contains("Native-image build"), printed);
                })));
    }

    @Test
    void pluginLookalikesOutsideProjectBuildStayClean() {
        rewriteRun(xml(
                UpgradeCommonsCliDependencyTest.project("<reporting><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                        "<artifactId>maven-shade-plugin</artifactId><configuration>org.apache.commons.cli</configuration>" +
                        "</plugin></plugins></reporting>"), source -> source.path("pom.xml")));
    }

    @Test
    void unrelatedMavenProjectHasNoGlobalBuildMarkers() {
        String shade = "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId>" +
                       "<configuration><relocation>org.apache.commons.cli</relocation></configuration></plugin>";
        rewriteRun(xml(UpgradeCommonsCliDependencyTest.project(
                "<properties><java.version>1.7</java.version></properties><build><plugins>" +
                "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>" +
                "<configuration><compilerArgs><arg>--add-modules=commons.cli</arg></compilerArgs></configuration></plugin>" +
                shade + "<plugin><groupId>org.graalvm.buildtools</groupId><artifactId>native-maven-plugin</artifactId></plugin>" +
                "</plugins></build>"), source -> source.path("pom.xml")));
    }

    @Test
    void profileDependencyDoesNotLeakMarkersIntoSiblingProfile() {
        String body = "<profiles>" +
                      "<profile><id>cli</id><properties><java.version>1.7</java.version></properties><dependencies>" +
                      UpgradeCommonsCliDependencyTest.dep("1.9.0") + "</dependencies></profile>" +
                      "<profile><id>sibling</id><properties><java.version>1.6</java.version></properties>" +
                      "<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>" +
                      "<configuration><compilerArgs><arg>--add-modules=commons.cli</arg></compilerArgs></configuration>" +
                      "</plugin></plugins></build></profile></profiles>";
        rewriteRun(xml(UpgradeCommonsCliDependencyTest.project(body),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(1, count(printed, "<!--~~("), printed);
                    assertTrue(printed.contains("<id>cli</id><properties><!--~~("), printed);
                    assertFalse(printed.contains("<id>sibling</id><properties><!--~~("), printed);
                    assertFalse(printed.contains("explicit JPMS module"), printed);
                })));
    }

    @Test
    void profileDependencyMakesRootBuildConfigurationRelevant() {
        rewriteRun(xml(UpgradeCommonsCliDependencyTest.project(
                "<properties><java.version>1.7</java.version></properties>" +
                "<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>" +
                "<configuration><compilerArgs><arg>--add-modules=commons.cli</arg></compilerArgs></configuration>" +
                "</plugin></plugins></build><profiles><profile><id>cli</id><dependencies>" +
                UpgradeCommonsCliDependencyTest.dep("1.9.0") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertEquals(2, count(after.printAll(), "<!--~~("), after.printAll()))));
    }

    @Test
    void rootDependencyIsVisibleToProfileBuildConfiguration() {
        rewriteRun(xml(UpgradeCommonsCliDependencyTest.project(
                "<dependencies>" + UpgradeCommonsCliDependencyTest.dep("1.9.0") + "</dependencies>" +
                "<profiles><profile><id>legacy</id><properties><java.version>1.7</java.version></properties>" +
                "<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>" +
                "<configuration><compilerArgs><arg>--add-modules=commons.cli</arg></compilerArgs></configuration>" +
                "</plugin></plugins></build></profile></profiles>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertEquals(2, count(after.printAll(), "<!--~~("), after.printAll()))));
    }

    @Test
    void marksGradleBaselineOwnersVariantsAndOldModuleName() {
        rewriteRun(buildGradle(
                """
                sourceCompatibility = JavaVersion.VERSION_1_7
                dependencies {
                    implementation 'commons-cli:commons-cli'
                    implementation 'commons-cli:commons-cli:1.8.0'
                    implementation 'commons-cli:commons-cli:1.5.0:tests'
                }
                tasks.withType(JavaCompile).configureEach { options.compilerArgs += ['--add-modules', 'commons.cli'] }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(5, count(printed, "/*~~("), printed);
                    assertTrue(printed.contains("Java 8+ Gradle toolchain"), printed);
                    assertTrue(printed.contains("platform/catalog"), printed);
                    assertTrue(printed.contains("outside the workbook"), printed);
                    assertTrue(printed.contains("Classifier/type/extension"), printed);
                    assertTrue(printed.contains("explicit module org.apache.commons.cli"), printed);
                })));
    }

    @Test
    void marksKotlinToolchainAndDynamicOwner() {
        rewriteRun(buildGradleKts(
                """
                java { toolchain { languageVersion.set(JavaLanguageVersion.of(7)) } }
                dependencies { implementation("commons-cli:commons-cli:$cliVersion") }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(2, count(printed, "/*~~("), printed);
                    assertTrue(printed.contains("externally/dynamically owned"), printed);
                })));
    }

    @Test
    void alignedBuildsAndNestedDslStayClean() {
        rewriteRun(
                buildGradle("sourceCompatibility = JavaVersion.VERSION_17\ndependencies { implementation 'commons-cli:commons-cli:1.9.0' }"),
                buildGradle("subprojects { dependencies { implementation 'commons-cli:commons-cli:1.8.0' } }")
        );
    }

    @Test
    void unrelatedGradleFilesHaveNoToolchainOrModuleMarkers() {
        rewriteRun(
                buildGradle("sourceCompatibility = JavaVersion.VERSION_1_7\n" +
                        "tasks.withType(JavaCompile).configureEach { options.compilerArgs += ['--add-modules', 'commons.cli'] }"),
                buildGradleKts("java { toolchain { languageVersion.set(JavaLanguageVersion.of(7)) } }\n" +
                        "tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add(\"--add-modules=commons.cli\") }")
        );
    }

    @Test
    void ordinaryGradleStringIsNotAModuleConfiguration() {
        rewriteRun(buildGradle("dependencies { implementation 'commons-cli:commons-cli:1.9.0' }\n" +
                "def applicationValue = 'commons.cli'"));
    }

    @Test
    void generatedParentIsIgnored() {
        rewriteRun(xml(UpgradeCommonsCliDependencyTest.pom("1.8.0"), source -> source.path("target/pom.xml")));
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = 0; (i = text.indexOf(needle, i)) >= 0; i += needle.length()) count++;
        return count;
    }
}
