package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FindJUnitJupiter6BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJUnitJupiter6BuildRisks());
    }

    @Test
    void publishedTargetIsNoop() {
        rewriteRun(xml(UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source ->
                source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "recommended source {0}")
    @ValueSource(strings = {"5.9.1", "5.8.2", "5.9.3"})
    void recommendedRecipeUpgradesEverySourceWithoutBuildRisk(String sourceVersion) {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitjupiteraggregate").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.junitjupiteraggregate.MigrateJUnitJupiterTo6_0_1")),
                xml(UpgradeJUnitJupiterDependencyTest.pom(sourceVersion), source ->
                        source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>6.0.1</version>");
                            assertNoMarker(after.printAll());
                        })));
    }

    @Test
    void exclusiveTargetPropertyResolvesWithoutMarker() {
        rewriteRun(xml(UpgradeJUnitJupiterDependencyTest.project(
                "<properties><junitJupiter.version>6.0.1</junitJupiter.version></properties><dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("${junitJupiter.version}") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void sharedSelectedPropertyIsMarkedAtDependencyOwner() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<properties><junitJupiter.version>5.8.2</junitJupiter.version></properties><dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("${junitJupiter.version}") +
                "</dependencies><build><finalName>${junitJupiter.version}</finalName></build>");
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitjupiteraggregate").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.junitjupiteraggregate.MigrateJUnitJupiterTo6_0_1")),
                xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.OWNER);
                    assertContains(after.printAll(), "<junitJupiter.version>5.8.2</junitJupiter.version>");
                })));
    }

    @ParameterizedTest(name = "external owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[3,5)", "4.+", "+", "latest.release"})
    void marksVersionlessDynamicAndRangedOwners(String version) {
        String dependency = version.isEmpty() ? UpgradeJUnitJupiterDependencyTest.depWithoutVersion() :
                UpgradeJUnitJupiterDependencyTest.dep(version);
        rewriteRun(xml(UpgradeJUnitJupiterDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.OWNER))));
    }

    @Test
    void marksFixedVersionOutsideWorkbook() {
        rewriteRun(xml(UpgradeJUnitJupiterDependencyTest.pom("5.10.0"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.OUTSIDE))));
    }

    @Test
    void marksMavenVariants() {
        rewriteRun(
                xml(UpgradeJUnitJupiterDependencyTest.project("<dependencies>" +
                        UpgradeJUnitJupiterDependencyTest.dep("6.0.1", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("classifier/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.VARIANT))),
                xml(UpgradeJUnitJupiterDependencyTest.project("<dependencies>" +
                        UpgradeJUnitJupiterDependencyTest.dep("6.0.1", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("zip/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.VARIANT))));
    }

    @Test
    void variantsDoNotGateCompanionMavenOrGradleAudits() {
        String maven = UpgradeJUnitJupiterDependencyTest.project(
                "<properties><java.version>11</java.version></properties><dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("6.0.1", "<classifier>tests</classifier>") +
                "<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-engine</artifactId><version>5.8.2</version></dependency>" +
                "</dependencies><build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId><version>2.22.2</version></plugin></plugins></build>");
        rewriteRun(xml(maven, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll(); assertContains(out, FindJUnitJupiter6BuildRisks.VARIANT);
                    assertFalse(out.contains(FindJUnitJupiter6BuildRisks.JAVA) || out.contains(FindJUnitJupiter6BuildRisks.ALIGNMENT) ||
                                out.contains(FindJUnitJupiter6BuildRisks.PROVIDER), out);
                })),
                buildGradle("sourceCompatibility = JavaVersion.VERSION_11\ndependencies { " +
                        "testRuntimeOnly 'org.junit.jupiter:junit-jupiter:6.0.1:tests'; " +
                        "testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.2' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll(); assertContains(out, FindJUnitJupiter6BuildRisks.VARIANT);
                            assertFalse(out.contains(FindJUnitJupiter6BuildRisks.JAVA) || out.contains(FindJUnitJupiter6BuildRisks.ALIGNMENT), out);
                        })));
    }

    @Test
    void profileDependencyDoesNotLeakIntoSiblingCompilerBaseline() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<profiles>" +
                "<profile><id>selected</id><dependencies>" + UpgradeJUnitJupiterDependencyTest.dep("6.0.1") +
                "</dependencies></profile>" +
                "<profile><id>sibling</id><properties><maven.compiler.release>8</maven.compiler.release>" +
                "</properties></profile>" +
                "</profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void profileDependencyMarksSameProfileJavaBaseline() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<profiles><profile><id>selected</id>" +
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<dependencies>" + UpgradeJUnitJupiterDependencyTest.dep("6.0.1") +
                "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.JAVA))));
    }

    @Test
    void rootDependencyMarksProfileJavaBaseline() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<dependencies>" + UpgradeJUnitJupiterDependencyTest.dep("6.0.1") + "</dependencies>" +
                "<profiles><profile><id>downstream</id>" +
                "<properties><maven.compiler.release>10</maven.compiler.release></properties>" +
                "</profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.JAVA))));
    }

    @Test
    void profileDependencyMarksRootJavaBaseline() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<profiles><profile><id>selected</id><dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("6.0.1") + "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.JAVA))));
    }

    @Test
    void resolvesIndirectOwnedMavenJavaBaseline() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<properties><jdk.release>11</jdk.release><maven.compiler.release>${jdk.release}</maven.compiler.release></properties>" +
                "<dependencies>" + UpgradeJUnitJupiterDependencyTest.dep("6.0.1") + "</dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.JAVA))));
    }

    @Test
    void marksUnalignedJUnitFamilyAndBomVersions() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<dependencyManagement><dependencies><dependency><groupId>org.junit</groupId>" +
                "<artifactId>junit-bom</artifactId><version>5.8.2</version><type>pom</type><scope>import</scope>" +
                "</dependency></dependencies></dependencyManagement><dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("6.0.1") +
                "<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-engine</artifactId>" +
                "<version>5.8.2</version></dependency></dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertCount(after.printAll(), FindJUnitJupiter6BuildRisks.ALIGNMENT, 2))));
    }

    @Test
    void alignedJUnit6FamilyIsNoop() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<dependencies>" + UpgradeJUnitJupiterDependencyTest.dep("6.0.1") +
                "<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-engine</artifactId>" +
                "<version>6.0.1</version></dependency></dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksOldAndExternallyOwnedMavenTestProviders() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<properties><surefire.version>2.22.2</surefire.version></properties><dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("6.0.1") + "</dependencies><build><plugins>" +
                "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId>" +
                "<version>${surefire.version}</version></plugin>" +
                "<plugin><artifactId>maven-failsafe-plugin</artifactId></plugin>" +
                "</plugins></build>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.PROVIDER);
            assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.PROVIDER_OWNER);
        })));
    }

    @Test
    void acceptsSupportedMavenProvider() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<dependencies>" + UpgradeJUnitJupiterDependencyTest.dep("6.0.1") +
                "</dependencies><build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId>" +
                "<version>3.5.5</version></plugin></plugins></build>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "pre-Java-17 baseline {0}")
    @ValueSource(strings = {"1.8", "8", "9", "16"})
    void marksPreJava17Baseline(String version) {
        rewriteRun(xml(UpgradeJUnitJupiterDependencyTest.project(
                        "<properties><java.version>" + version + "</java.version></properties><dependencies>" +
                        UpgradeJUnitJupiterDependencyTest.dep("6.0.1") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.JAVA))));
    }

    @ParameterizedTest(name = "supported Java baseline {0}")
    @ValueSource(strings = {"17", "21", "${java.release}"})
    void acceptsSupportedOrExternallyOwnedJavaBaseline(String version) {
        rewriteRun(xml(UpgradeJUnitJupiterDependencyTest.project(
                        "<properties><maven.compiler.release>" + version + "</maven.compiler.release></properties>" +
                        "<dependencies>" + UpgradeJUnitJupiterDependencyTest.dep("6.0.1") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksGradleOutsideDynamicAndVariants() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.junit.jupiter:junit-jupiter:5.10.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.OUTSIDE))),
                buildGradle("def v='6.0.1'\ndependencies { implementation \"org.junit.jupiter:junit-jupiter:$v\" }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.OWNER))),
                buildGradle("dependencies { implementation 'org.junit.jupiter:junit-jupiter:6.0.1@zip' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.VARIANT))),
                buildGradleKts("dependencies { implementation(\"org.junit.jupiter:junit-jupiter:5.9.1\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.OWNER))));
    }

    @Test
    void exactDynamicTemplatesDistinguishOwnersVariantsAndLookalikes() {
        rewriteRun(
                buildGradle("def v = '6.0.1'\ndependencies { testImplementation \"org.junit.jupiter:junit-jupiter:$v\" }",
                        source -> source.path("dynamic.gradle").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.OWNER))),
                buildGradle("def v = '6.0.1'\nsourceCompatibility = JavaVersion.VERSION_11\n" +
                        "dependencies { testImplementation \"org.junit.jupiter:junit-jupiter:${v}:tests\"; " +
                        "testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.2' }",
                        source -> source.path("variant.gradle").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertContains(out, FindJUnitJupiter6BuildRisks.VARIANT);
                            assertFalse(out.contains(FindJUnitJupiter6BuildRisks.JAVA) ||
                                        out.contains(FindJUnitJupiter6BuildRisks.ALIGNMENT), out);
                        })),
                buildGradleKts("val v = \"6.0.1\"\ndependencies { " +
                        "testImplementation(\"prefix-org.junit.jupiter:junit-jupiter:$v\") }",
                        source -> source.path("lookalike.gradle.kts").afterRecipe(after ->
                                assertNoMarker(after.printAll()))));
    }

    @Test
    void dynamicCompanionsUseTheirSpecificJUnit6Actions() {
        rewriteRun(buildGradleKts("val v = \"5.9.3\"\ndependencies {\n" +
                "    testImplementation(\"org.junit.jupiter:junit-jupiter:6.0.1\")\n" +
                "    testRuntimeOnly(\"org.junit.platform:junit-platform-jfr:$v\")\n" +
                "    testImplementation(\"org.junit.jupiter:junit-jupiter-migrationsupport:$v\")\n" +
                "}", source -> source.path("companions.gradle.kts").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, FindJUnitJupiter6BuildRisks.REMOVED_MODULE);
                    assertContains(out, FindJUnitJupiter6BuildRisks.MIGRATION_SUPPORT);
                })));
    }

    @Test
    void marksGradleJavaAndJUnitFamilyAlignment() {
        rewriteRun(buildGradle(
                "java { sourceCompatibility = JavaVersion.VERSION_11 }\n" +
                "dependencies { testRuntimeOnly 'org.junit.jupiter:junit-jupiter:6.0.1'; " +
                "testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.2' }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.JAVA);
                    assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.ALIGNMENT);
                })));
    }

    @Test
    void marksMissingGradleLauncherAndAcceptsAlignedRuntimeDependency() {
        rewriteRun(
                buildGradle("dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:6.0.1' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.GRADLE_LAUNCHER))),
                buildGradleKts("dependencies {\n" +
                        "    testImplementation(\"org.junit.jupiter:junit-jupiter:6.0.1\")\n" +
                        "    testRuntimeOnly(\"org.junit.platform:junit-platform-launcher:6.0.1\")\n" +
                        "}", source -> source.path("aligned.gradle.kts").afterRecipe(after ->
                                assertNoMarker(after.printAll()))));
    }

    @Test
    void launcherMustBeAnUnclassifiedRuntimeDependency() {
        rewriteRun(
                buildGradleKts("val v = \"6.0.1\"\ndependencies {\n" +
                        "    testImplementation(\"org.junit.jupiter:junit-jupiter:6.0.1\")\n" +
                        "    testRuntimeOnly(\"org.junit.platform:junit-platform-launcher:$v\")\n" +
                        "}", source -> source.path("dynamic-launcher.gradle.kts").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String out = after.printAll();
                                    assertContains(out, FindJUnitJupiter6BuildRisks.ALIGNMENT_OWNER);
                                    assertFalse(out.contains(FindJUnitJupiter6BuildRisks.GRADLE_LAUNCHER), out);
                                })),
                buildGradle("dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:6.0.1'; " +
                        "testCompileOnly 'org.junit.platform:junit-platform-launcher:6.0.1' }",
                        source -> source.path("compile-only.gradle").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.GRADLE_LAUNCHER))),
                buildGradle("dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:6.0.1'; " +
                        "testRuntimeOnly 'org.junit.platform:junit-platform-launcher:6.0.1:tests' }",
                        source -> source.path("classified.gradle").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.GRADLE_LAUNCHER))));
    }

    @Test
    void recommendedGradleUpgradeLeavesLauncherActionAtOwnedDependency() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitjupiteraggregate").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.junitjupiteraggregate.MigrateJUnitJupiterTo6_0_1")),
                buildGradle("dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:5.8.2' }",
                        source -> source.after(actual -> actual.replace("5.8.2", "6.0.1")).afterRecipe(after -> {
                            assertContains(after.printAll(), "org.junit.jupiter:junit-jupiter:6.0.1");
                            assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.GRADLE_LAUNCHER);
                        })));
    }

    @Test
    void nestedGradleUnrelatedAndGeneratedFilesAreNoop() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'org.junit.jupiter:junit-jupiter:5.10.0' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'example:junit-platform-launcher:5.10.0' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.10.0"), source -> source.path("generated-code/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void rootGradleDependencyDoesNotOwnNestedProjectPolicy() {
        rewriteRun(buildGradle("dependencies { testRuntimeOnly 'org.junit.jupiter:junit-jupiter:6.0.1'; " +
                "testRuntimeOnly 'org.junit.platform:junit-platform-launcher:6.0.1' }\n" +
                "project(':child') { sourceCompatibility = JavaVersion.VERSION_11; dependencies { " +
                "testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.2' } }",
                source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.10.0"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindJUnitJupiter6BuildRisks.OUTSIDE, 1))));
    }

    @ParameterizedTest(name = "removed module {0}")
    @ValueSource(strings = {"junit-platform-jfr", "junit-platform-runner", "junit-platform-suite-commons"})
    void marksRemovedJUnitPlatformModules(String artifact) {
        String pom = UpgradeJUnitJupiterDependencyTest.project("<dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("6.0.1") +
                "<dependency><groupId>org.junit.platform</groupId><artifactId>" + artifact +
                "</artifactId><version>5.9.3</version></dependency></dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.REMOVED_MODULE))));
    }

    @Test
    void marksDeprecatedMigrationSupportModule() {
        String pom = UpgradeJUnitJupiterDependencyTest.project("<dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("6.0.1") +
                "<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-migrationsupport</artifactId>" +
                "<version>6.0.1</version></dependency></dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.MIGRATION_SUPPORT))));
    }

    @Test
    void directGradleRemovedModulesKeepSpecificMessages() {
        rewriteRun(buildGradle("dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:6.0.1'; " +
                "testRuntimeOnly 'org.junit.platform:junit-platform-runner:5.9.3'; " +
                "testImplementation 'org.junit.jupiter:junit-jupiter-migrationsupport:5.9.3' }",
                source -> source.path("removed.gradle").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, FindJUnitJupiter6BuildRisks.REMOVED_MODULE);
                    assertContains(out, FindJUnitJupiter6BuildRisks.MIGRATION_SUPPORT);
                })));
    }

    @ParameterizedTest(name = "pre-Kotlin-2.2 baseline {0}")
    @ValueSource(strings = {"1.6.21", "1.9.25", "2.0.21", "2.1.20"})
    void marksPreKotlin22Property(String version) {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<properties><kotlin.version>" + version + "</kotlin.version></properties><dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("6.0.1") + "</dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.KOTLIN))));
    }

    @Test
    void acceptsKotlin22Property() {
        String pom = UpgradeJUnitJupiterDependencyTest.project(
                "<properties><kotlin.version>2.2.0</kotlin.version></properties><dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("6.0.1") + "</dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksOldKotlinMavenPlugin() {
        String pom = UpgradeJUnitJupiterDependencyTest.project("<dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("6.0.1") + "</dependencies><build><plugins>" +
                "<plugin><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-maven-plugin</artifactId>" +
                "<version>2.1.20</version></plugin></plugins></build>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitJupiter6BuildRisks.KOTLIN))));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~(") || actual.contains("<!--~~("), actual);
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
