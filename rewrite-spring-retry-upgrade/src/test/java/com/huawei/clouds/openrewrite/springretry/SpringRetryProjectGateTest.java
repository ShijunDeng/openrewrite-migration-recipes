package com.huawei.clouds.openrewrite.springretry;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringRetryProjectGateTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springretry.MigrateSpringRetryTo2_0_13";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(SpringRetryTestSupport.recipe(RECIPE))
                .parser(SpringRetryTestSupport.parser());
    }

    @Test
    void migratesOnlyTheExactSelectedProjectInAMixedSourceSet() {
        rewriteRun(
                xml(SpringRetryTestSupport.pom("1.3.4"),
                        SpringRetryTestSupport.pom("2.0.13"),
                        source -> source.path("selected/pom.xml")),
                retryable("selected/src/main/java/Selected.java", true, true),
                xml(SpringRetryTestSupport.pom("2.0.13"),
                        source -> source.path("target/pom.xml")),
                retryable("target/src/main/java/Target.java", false, false),
                xml(SpringRetryTestSupport.project(
                                "<properties><java.version>8</java.version></properties><dependencies>" +
                                SpringRetryTestSupport.dependency("3.0.0", "") +
                                "<dependency><groupId>org.springframework</groupId>" +
                                "<artifactId>spring-context</artifactId><version>5.3.39</version></dependency>" +
                                "</dependencies>"),
                        source -> source.path("future/pom.xml")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains(SpringRetrySupport.TARGET_CONFLICT), printed);
                                    assertFalse(printed.contains(FindSpringRetry2013BuildRisks.JAVA_BASELINE),
                                            printed);
                                    assertFalse(printed.contains(FindSpringRetry2013BuildRisks.SPRING_BASELINE),
                                            printed);
                                })),
                retryable("future/src/main/java/Future.java", false, false),
                xml(SpringRetryTestSupport.pom("1.3.3"),
                        source -> source.path("outside/pom.xml")
                                .after(actual -> actual)
                                .afterRecipe(after -> assertTrue(
                                        after.printAll().contains(
                                                FindSpringRetry2013BuildRisks.OUTSIDE),
                                        after.printAll()))),
                retryable("outside/src/main/java/Outside.java", false, false),
                xml(SpringRetryTestSupport.project(""),
                        source -> source.path("unrelated/pom.xml")),
                retryable("unrelated/src/main/java/Unrelated.java", false, false));
    }

    @Test
    void unselectedNestedBuildBlocksAPropagatingParentSelection() {
        rewriteRun(
                xml(SpringRetryTestSupport.pom("1.3.4"),
                        source -> source.path("parent/pom.xml")
                                .afterRecipe(after -> assertTrue(after.getMarkers()
                                        .findFirst(SpringRetryProjectMarker.class).isEmpty()))),
                retryable("parent/src/main/java/Parent.java", false, false),
                xml(SpringRetryTestSupport.pom("2.0.13"),
                        source -> source.path("parent/child/pom.xml")),
                retryable("parent/child/src/main/java/Child.java", false, false));
    }

    @Test
    void conflictingSourceAndFutureDeclarationsBlockEveryAutomaticEdit() {
        String pom = SpringRetryTestSupport.project("<dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4", "") +
                SpringRetryTestSupport.dependency("2.0.14", "") +
                "</dependencies>");
        rewriteRun(
                xml(pom, source -> source.path("mixed/pom.xml")
                        .after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>1.3.4</version>"), printed);
                            assertTrue(printed.contains("<version>2.0.14</version>"), printed);
                            assertTrue(printed.contains(SpringRetrySupport.TARGET_CONFLICT), printed);
                        })),
                retryable("mixed/src/main/java/Mixed.java", false, false));
    }

    @Test
    void sourceAndTargetDeclarationsBlockEveryAutomaticEditAndSurfaceTheConflict() {
        String pom = SpringRetryTestSupport.project(
                "<properties><java.version>8</java.version></properties><dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4", "") +
                SpringRetryTestSupport.dependency("2.0.13", "") +
                "</dependencies>");
        rewriteRun(
                xml(pom, source -> source.path("source-target/pom.xml")
                        .after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<java.version>8</java.version>"), printed);
                            assertTrue(printed.contains("<version>1.3.4</version>"), printed);
                            assertTrue(printed.contains("<version>2.0.13</version>"), printed);
                            assertTrue(printed.contains(
                                    FindSpringRetry2013BuildRisks.DECLARATION_CONFLICT), printed);
                        })),
                retryable("source-target/src/main/java/SourceTarget.java", false, false));
    }

    @Test
    void upgradedProjectRetainsEligibilityForMigrationSpecificBuildRisks() {
        String pom = SpringRetryTestSupport.project(
                "<dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4",
                        "<exclusions><exclusion><groupId>org.springframework</groupId>" +
                        "<artifactId>spring-context</artifactId></exclusion></exclusions>") +
                "<dependency><groupId>org.springframework</groupId>" +
                "<artifactId>spring-context</artifactId><version>5.3.39</version></dependency>" +
                "<dependency><groupId>io.micrometer</groupId>" +
                "<artifactId>micrometer-core</artifactId><version>1.10.13</version></dependency>" +
                "<dependency><groupId>org.aspectj</groupId>" +
                "<artifactId>aspectjweaver</artifactId><version>1.9.22</version></dependency>" +
                "</dependencies><build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                "<configuration><relocations><relocation><pattern>org.springframework.retry</pattern>" +
                "</relocation></relocations></configuration></plugin></plugins></build>");
        rewriteRun(xml(pom, source -> source.path("retained-marker/pom.xml")
                .after(actual -> actual)
                .afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>2.0.13</version>"), printed);
                    assertTrue(printed.contains(FindSpringRetry2013BuildRisks.SPRING_BASELINE), printed);
                    assertTrue(printed.contains(
                            FindSpringRetry2013BuildRisks.MICROMETER_ALIGNMENT), printed);
                    assertTrue(printed.contains(FindSpringRetry2013BuildRisks.PROXY_STACK), printed);
                    assertTrue(printed.contains(FindSpringRetry2013BuildRisks.PACKAGING), printed);
                })));
    }

    @Test
    void anExclusiveMavenPropertySelectsTheProject() {
        String before = SpringRetryTestSupport.project(
                "<properties><spring-retry.version>1.3.4</spring-retry.version></properties>" +
                "<dependencies>" + SpringRetryTestSupport.dependency(
                        "${spring-retry.version}", "") + "</dependencies>");
        String after = before.replace(
                "<spring-retry.version>1.3.4</spring-retry.version>",
                "<spring-retry.version>2.0.13</spring-retry.version>");
        rewriteRun(
                xml(before, after, source -> source.path("property/pom.xml")),
                retryable("property/src/main/java/PropertyClient.java", true, true));
    }

    @Test
    void anAmbiguousOrSharedPropertyCannotAuthorizeSourceChanges() {
        String pom = SpringRetryTestSupport.project(
                "<properties><v>1.3.4</v></properties><dependencies>" +
                SpringRetryTestSupport.dependency("${v}", "") +
                "<dependency><groupId>example</groupId><artifactId>other</artifactId>" +
                "<version>${v}</version></dependency></dependencies>");
        rewriteRun(
                xml(pom, source -> source.path("ambiguous/pom.xml")),
                retryable("ambiguous/src/main/java/Ambiguous.java", false, false));
    }

    @Test
    void gradleProjectsAreSelectedAndNestedBuildsRemainIsolated() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework.retry:spring-retry:1.3.4' }",
                        "dependencies { implementation 'org.springframework.retry:spring-retry:2.0.13' }",
                        source -> source.path("gradle/build.gradle")),
                retryable("gradle/src/main/java/Root.java", true, true),
                buildGradle(
                        "dependencies { implementation 'org.springframework.retry:spring-retry:2.0.13' }",
                        source -> source.path("gradle/child/build.gradle")),
                retryable("gradle/child/src/main/java/Child.java", false, false));
    }

    @Test
    void nestedGradleDslCannotAuthorizeTheContainingProject() {
        rewriteRun(
                buildGradle("""
                        project(':app') {
                            dependencies {
                                implementation 'org.springframework.retry:spring-retry:1.3.4'
                            }
                        }
                        """, source -> source.path("nested-dsl/build.gradle")),
                retryable("nested-dsl/app/src/main/java/Nested.java", false, false));
    }

    @Test
    void nestedOrDynamicGradleDeclarationsBlockAnOtherwiseSelectedRoot() {
        rewriteRun(
                buildGradle("""
                        def retryVersion = '1.3.4'
                        dependencies {
                            implementation 'org.springframework.retry:spring-retry:1.3.4'
                            implementation "org.springframework.retry:spring-retry:${retryVersion}"
                        }
                        project(':child') {
                            dependencies {
                                implementation 'org.springframework.retry:spring-retry:1.3.4'
                            }
                        }
                        """, source -> source.path("conflicted-gradle/build.gradle")
                        .after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("spring-retry:1.3.4"), printed);
                            assertFalse(printed.contains("spring-retry:2.0.13"), printed);
                            assertTrue(printed.contains(FindSpringRetry2013BuildRisks.OWNER), printed);
                        })),
                retryable("conflicted-gradle/src/main/java/Conflicted.java", false, false));
    }

    @Test
    void aVersionCatalogReferenceBlocksAnOtherwiseSelectedGradleRoot() {
        rewriteRun(
                buildGradle("""
                        dependencies {
                            implementation 'org.springframework.retry:spring-retry:1.3.4'
                            testImplementation libs.spring.retry
                        }
                        """, source -> source.path("catalog/build.gradle")
                        .after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("spring-retry:1.3.4"), printed);
                            assertFalse(printed.contains("spring-retry:2.0.13"), printed);
                            assertTrue(printed.contains(FindSpringRetry2013BuildRisks.OWNER), printed);
                        })),
                retryable("catalog/src/main/java/CatalogClient.java", false, false));
    }

    @Test
    void selectedProjectDoesNotRewriteAnAppliedGradleScript() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework.retry:spring-retry:1.3.4' }",
                        "dependencies { implementation 'org.springframework.retry:spring-retry:2.0.13' }",
                        source -> source.path("scripts/build.gradle")),
                buildGradle(
                        "dependencies { implementation 'org.springframework.retry:spring-retry:1.3.4' }",
                        source -> source.path("scripts/gradle/dependencies.gradle")),
                retryable("scripts/src/main/java/ScriptedClient.java", true, true));
    }

    @Test
    void competingBuildSystemsAtTheSameRootAreConservativelyBlocked() {
        rewriteRun(
                xml(SpringRetryTestSupport.pom("1.3.4"),
                        source -> source.path("dual-build/pom.xml")),
                buildGradle("plugins { id 'java' }",
                        source -> source.path("dual-build/build.gradle")),
                retryable("dual-build/src/main/java/DualBuildClient.java", false, false));
    }

    @Test
    void dependencyManagementCannotIndirectlyUpgradeAnUnselectedNestedProject() {
        String parent = SpringRetryTestSupport.project(
                "<dependencyManagement><dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4", "") +
                "</dependencies></dependencyManagement>");
        String child = SpringRetryTestSupport.project(
                "<dependencies>" + SpringRetryTestSupport.dependency(null, "") +
                "</dependencies>");
        rewriteRun(
                xml(parent, source -> source.path("managed-parent/pom.xml")),
                retryable("managed-parent/src/main/java/ManagedParent.java", false, false),
                xml(child, source -> source.path("managed-parent/child/pom.xml")
                        .after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindSpringRetry2013BuildRisks.OWNER), printed);
                            assertFalse(printed.contains("<version>2.0.13</version>"), printed);
                        })),
                retryable("managed-parent/child/src/main/java/ManagedChild.java", false, false));
    }

    @Test
    void inheritedParentDependencyCannotUpgradeAcrossAnUnselectedNestedBuild() {
        rewriteRun(
                xml(inheritedParentPom("1.3.4"),
                        source -> source.path("inherited-parent/pom.xml")
                                .afterRecipe(after -> assertTrue(after.getMarkers()
                                        .findFirst(SpringRetryProjectMarker.class).isEmpty()))),
                retryable("inherited-parent/src/main/java/InheritedParent.java", false, false),
                xml(inheritedChildPom("2.0.13"),
                        source -> source.path("inherited-parent/child/pom.xml")
                                .afterRecipe(after -> assertTrue(after.getMarkers()
                                        .findFirst(SpringRetryProjectMarker.class).isEmpty()))),
                retryable("inherited-parent/child/src/main/java/InheritedChild.java", false, false));
    }

    @Test
    void projectMarkerSeesAllNestedBuildBoundariesBeforeSelecting() {
        String parent = SpringRetryTestSupport.project(
                "<dependencyManagement><dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4", "") +
                "</dependencies></dependencyManagement>");
        String child = SpringRetryTestSupport.project(
                "<dependencies>" + SpringRetryTestSupport.dependency(null, "") +
                "</dependencies>");
        rewriteRun(spec -> spec.recipe(new MarkSelectedSpringRetryProjects()),
                xml(parent, source -> source.path("marker-parent/pom.xml")
                        .afterRecipe(after -> assertTrue(after.getMarkers()
                                .findFirst(SpringRetryProjectMarker.class).isEmpty()))),
                xml(child, source -> source.path("marker-parent/child/pom.xml")
                        .afterRecipe(after -> assertTrue(after.getMarkers()
                                .findFirst(SpringRetryProjectMarker.class).isEmpty()))));
    }

    @Test
    void propagatingProjectSelectionRejectsAnUnselectedDescendantBoundary() {
        MarkSelectedSpringRetryProjects.Projects projects =
                new MarkSelectedSpringRetryProjects.Projects();
        projects.record(Path.of("parent/pom.xml"),
                MarkSelectedSpringRetryProjects.State.SELECTED);
        projects.recordPropagation(Path.of("parent/pom.xml"));
        projects.record(Path.of("parent/child/pom.xml"),
                MarkSelectedSpringRetryProjects.State.OTHER);
        assertTrue(projects.nearest(Path.of("parent/src/App.java")) !=
                   MarkSelectedSpringRetryProjects.State.SELECTED);
    }

    @Test
    void dependencyManagementCanUpgradeWhenEveryNestedConsumerOwnsExact134() {
        String parentBefore = SpringRetryTestSupport.project(
                "<dependencyManagement><dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4", "") +
                "</dependencies></dependencyManagement>");
        String parentAfter = parentBefore.replace(
                "<version>1.3.4</version>", "<version>2.0.13</version>");
        rewriteRun(
                xml(parentBefore, parentAfter,
                        source -> source.path("managed-selected/pom.xml")),
                retryable("managed-selected/src/main/java/SelectedParent.java", true, true),
                xml(SpringRetryTestSupport.pom("1.3.4"),
                        SpringRetryTestSupport.pom("2.0.13"),
                        source -> source.path("managed-selected/child/pom.xml")),
                retryable("managed-selected/child/src/main/java/SelectedChild.java", true, true));
    }

    @Test
    void inheritedParentDependencyCanUpgradeWhenEveryNestedBuildOwnsExact134() {
        rewriteRun(
                xml(inheritedParentPom("1.3.4"),
                        inheritedParentPom("2.0.13"),
                        source -> source.path("inherited-selected/pom.xml")),
                retryable("inherited-selected/src/main/java/SelectedParent.java", true, true),
                xml(inheritedChildPom("1.3.4"),
                        inheritedChildPom("2.0.13"),
                        source -> source.path("inherited-selected/child/pom.xml")),
                retryable("inherited-selected/child/src/main/java/SelectedChild.java", true, true));
    }

    @Test
    void officialJavaBaselineIsScopedToTheSelectedNearestBuildRoot() {
        String selected = pomWithJava("1.3.4", "8");
        rewriteRun(spec -> spec.recipe(SpringRetryTestSupport.recipe(
                        "com.huawei.clouds.openrewrite.springretry.UpgradeSpringRetryBuildToJava17")),
                pomXml(selected, source -> source.path("baseline/pom.xml")
                        .after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<maven.compiler.release>17" +
                                                        "</maven.compiler.release>"), printed);
                            assertTrue(printed.contains("<version>1.3.4</version>"), printed);
                        })),
                xml(pomWithJava("2.0.13", "8"),
                        source -> source.path("baseline-target/pom.xml")),
                xml(pomWithJava("3.0.0", "8"),
                        source -> source.path("baseline-future/pom.xml")),
                xml(pomWithJava("1.3.3", "8"),
                        source -> source.path("baseline-outside/pom.xml")));
    }

    @Test
    void sourceRiskMarkersCannotLeakIntoTargetFutureOutsideOrUnrelatedProjects() {
        rewriteRun(
                xml(SpringRetryTestSupport.pom("1.3.4"),
                        SpringRetryTestSupport.pom("2.0.13"),
                        source -> source.path("selected/pom.xml")),
                risky("selected/src/main/java/SelectedRisk.java", true),
                xml(SpringRetryTestSupport.pom("2.0.13"),
                        source -> source.path("target/pom.xml")),
                risky("target/src/main/java/TargetRisk.java", false),
                xml(SpringRetryTestSupport.pom("3.0.0"),
                        source -> source.path("future/pom.xml")
                                .after(actual -> actual)
                                .afterRecipe(after -> assertTrue(
                                        after.printAll().contains(SpringRetrySupport.TARGET_CONFLICT),
                                        after.printAll()))),
                risky("future/src/main/java/FutureRisk.java", false),
                xml(SpringRetryTestSupport.pom("1.3.3"),
                        source -> source.path("outside/pom.xml")
                                .after(actual -> actual)
                                .afterRecipe(after -> assertTrue(
                                        after.printAll().contains(
                                                FindSpringRetry2013BuildRisks.OUTSIDE),
                                        after.printAll()))),
                risky("outside/src/main/java/OutsideRisk.java", false),
                xml(SpringRetryTestSupport.project(""),
                        source -> source.path("unrelated/pom.xml")),
                risky("unrelated/src/main/java/UnrelatedRisk.java", false));
    }

    private static org.openrewrite.test.SourceSpecs retryable(
            String path, boolean migrated, boolean marked) {
        String className = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
        return java("""
                import java.io.IOException;
                import org.springframework.retry.annotation.Retryable;
                class %s {
                    @Retryable(include = IOException.class,
                               maxAttemptsExpression = "#{3}")
                    void call() {}
                }
                """.formatted(className), source -> {
            source.path(path);
            if (migrated || marked) source.after(actual -> actual);
            source.afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(migrated == printed.contains("retryFor = IOException.class"), printed);
                    assertTrue(!migrated == printed.contains("include = IOException.class"), printed);
                    assertTrue(marked == printed.contains(FindSpringRetry20SourceRisks.EXPRESSION), printed);
                    if (!marked) {
                        assertFalse(printed.contains(FindSpringRetry20SourceRisks.PROXY_RECOVERY), printed);
                    }
                });
        });
    }

    private static org.openrewrite.test.SourceSpecs risky(String path, boolean marked) {
        String className = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
        return java("""
                import org.springframework.retry.listener.RetryListenerSupport;
                class %s extends RetryListenerSupport {
                }
                """.formatted(className), source -> {
            source.path(path);
            if (marked) source.after(actual -> actual);
            source.afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(marked == printed.contains(
                            FindSpringRetry20SourceRisks.LISTENER_SUPPORT), printed);
                });
        });
    }

    private static String pomWithJava(String retryVersion, String javaVersion) {
        return SpringRetryTestSupport.project(
                "<properties><maven.compiler.release>" + javaVersion +
                "</maven.compiler.release></properties><dependencies>" +
                SpringRetryTestSupport.dependency(retryVersion, "") + "</dependencies>");
    }

    private static String inheritedParentPom(String retryVersion) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>inherited-parent</artifactId><version>1</version><packaging>pom</packaging>" +
               "<modules><module>child</module></modules><dependencies>" +
               SpringRetryTestSupport.dependency(retryVersion, "") + "</dependencies></project>";
    }

    private static String inheritedChildPom(String retryVersion) {
        return "<project><modelVersion>4.0.0</modelVersion><parent><groupId>example</groupId>" +
               "<artifactId>inherited-parent</artifactId><version>1</version>" +
               "<relativePath>../pom.xml</relativePath></parent><artifactId>child</artifactId>" +
               "<dependencies>" + SpringRetryTestSupport.dependency(retryVersion, "") +
               "</dependencies></project>";
    }
}
