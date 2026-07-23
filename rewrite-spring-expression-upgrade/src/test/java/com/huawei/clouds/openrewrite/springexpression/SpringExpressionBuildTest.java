package com.huawei.clouds.openrewrite.springexpression;

import org.junit.jupiter.api.Test;
import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.config.Environment;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringExpressionBuildTest implements RewriteTest {
    private static final String CONFIGURE =
            "com.huawei.clouds.openrewrite.springexpression.ConfigureSpringExpression6Build";
    private static final PrintOutputCapture.MarkerPrinter SILENT_MARKERS =
            new PrintOutputCapture.MarkerPrinter() {
                @Override public String beforePrefix(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) {
                    return "";
                }
                @Override public String beforeSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) {
                    return "";
                }
                @Override public String afterSyntax(Marker marker, Cursor cursor, UnaryOperator<String> wrapper) {
                    return "";
                }
            };

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringExpressionBuildRisks()).markerPrinter(SILENT_MARKERS);
    }

    @Test
    void marksExactNoDowngradeConflictOutsideOwnerAndVariant() {
        assertEquals(FindSpringExpressionBuildRisks.TARGET_CONFLICT,
                FindSpringExpressionBuildRisks.primaryMessage("7.0.0", "7.0.0"));
        rewriteRun(
                spec -> spec.expectedCyclesThatMakeChanges(1),
                xml(dependencyPom("7.0.0", ""), source -> source.path("newer/pom.xml")
                        .afterRecipe(after -> assertMarks(after, 1, "目标版本冲突（禁止降级）"))),
                xml(dependencyPom("5.3.38", ""), source -> source.path("outside/pom.xml")
                        .afterRecipe(after -> assertMarks(after, 1, "outside the exact 17-version whitelist"))),
                xml(dependencyPom("", ""), source -> source.path("owner/pom.xml")
                        .afterRecipe(after -> assertMarks(after, 1, "externally owned"))),
                xml(dependencyPom("5.3.39", "<classifier>sources</classifier>"),
                    source -> source.path("variant/pom.xml")
                            .afterRecipe(after -> assertMarks(after, 1, "non-JAR"))),
                buildGradle(
                        "dependencies { implementation 'org.springframework:spring-expression:6.3.0' }",
                        source -> source.path("newer.gradle")
                                .afterRecipe(after -> assertMarks(after, 1, "目标版本冲突（禁止降级）"))),
                buildGradleKts(
                        "dependencies { implementation(\"org.springframework:spring-expression:7.1.0\") }",
                        source -> source.path("newer.gradle.kts")
                                .afterRecipe(after -> assertMarks(after, 1, "目标版本冲突（禁止降级）"))),
                buildGradleKts(
                        """
                        dependencies {
                          implementation(
                            group = "org.springframework",
                            name = "spring-expression",
                            version = "7.0.0"
                          )
                        }
                        """,
                        source -> source.path("named.gradle.kts")
                                .afterRecipe(after -> assertMarks(after, 1, "目标版本冲突（禁止降级）")))
        );
    }

    @Test
    void marksHigherPropertyAndVariantVersionsWithExactNoDowngradeConflict() {
        rewriteRun(
                spec -> spec.expectedCyclesThatMakeChanges(1),
                xml(
                        pom("""
                        <properties><spel.version>7.0.0</spel.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("${spel.version}", ""))),
                        source -> source.path("property/pom.xml").afterRecipe(after ->
                                assertMarks(after, 1, "目标版本冲突（禁止降级）"))),
                xml(
                        dependencyPom("7.0.0", "<classifier>sources</classifier>"),
                        source -> source.path("variant/pom.xml").afterRecipe(after ->
                                assertMarks(after, 2, "目标版本冲突（禁止降级）", "non-JAR"))),
                buildGradle(
                        "dependencies { implementation 'org.springframework:spring-expression:7.0.0:sources' }",
                        source -> source.afterRecipe(after ->
                                assertMarks(after, 1, "目标版本冲突（禁止降级）")))
        );
    }

    @Test
    void marksJavaParametersAlignmentBootJakartaAndModuleRisks() {
        rewriteRun(
                spec -> spec.expectedCyclesThatMakeChanges(1),
                xml(pom("""
                        <properties>
                          <java.version>11</java.version>
                          <maven.compiler.parameters>false</maven.compiler.parameters>
                        </properties>
                        <dependencies>
                          %s
                          <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>5.3.39</version></dependency>
                          <dependency><groupId>javax.annotation</groupId><artifactId>javax.annotation-api</artifactId><version>1.3.2</version></dependency>
                        </dependencies>
                        <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>
                          <configuration><release>11</release><parameters>false</parameters></configuration>
                        </plugin></plugins></build>
                        """.formatted(dependency("6.2.18", ""))),
                    source -> source.path("risks/pom.xml").afterRecipe(after ->
                            assertMarks(after, 6, "Java 17", "-parameters", "must align", "Jakarta"))),
                xml(pom("""
                        <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>2.7.18</version></parent>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("6.2.19", ""))),
                    source -> source.path("boot/pom.xml")
                            .afterRecipe(after -> assertMarks(after, 1, "compatible 3.4/3.5"))),
                buildGradle("""
                        sourceCompatibility = JavaVersion.VERSION_1_8
                        targetCompatibility = 11
                        tasks.withType(JavaCompile).configureEach {
                          options.compilerArgs = []
                        }
                        dependencies {
                          implementation 'org.springframework:spring-expression:6.2.18'
                          implementation 'org.springframework:spring-core:5.3.39'
                          implementation 'javax.inject:javax.inject:1'
                        }
                        """, source -> source.afterRecipe(after ->
                        assertMarks(after, 5, "Java 17", "-parameters", "must align", "Jakarta")))
        );
    }

    @Test
    void selectedAndTargetDeclarationsAloneHaveNoBuildMarker() {
        rewriteRun(
                xml(dependencyPom("5.3.39", ""), source -> source.path("selected/pom.xml")
                        .afterRecipe(after -> assertMarks(after, 0))),
                xml(dependencyPom("6.2.19", ""), source -> source.path("target/pom.xml")
                        .afterRecipe(after -> assertMarks(after, 0)))
        );
    }

    @Test
    void configureComposesOfficialMavenRecipes() {
        assertEquals(List.of(
                        "org.openrewrite.maven.UpdateMavenProjectPropertyJavaVersion",
                        "org.openrewrite.maven.AddProperty"),
                ConfigureSpringExpressionBuild.officialMavenRecipes().stream()
                        .map(org.openrewrite.Recipe::getName).toList());
        rewriteRun(
                spec -> spec.recipe(Environment.builder().scanRuntimeClasspath().build()
                        .activateRecipes(CONFIGURE)),
                pomXml(
                        pom("""
                        <properties><java.version>11</java.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("6.2.18", ""))),
                        pom("""
                        <properties><java.version>17</java.version>
                            <maven.compiler.parameters>true</maven.compiler.parameters>
                        </properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("6.2.18", ""))))
        );
    }

    @Test
    void configurePreservesExplicitParameterPolicyAndJava21() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder().scanRuntimeClasspath().build()
                        .activateRecipes(CONFIGURE)),
                pomXml(pom("""
                        <properties>
                          <java.version>21</java.version>
                          <maven.compiler.parameters>false</maven.compiler.parameters>
                        </properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("6.2.19", ""))))
        );
    }

    @Test
    void configureComposesOfficialGradleCompatibilityWithoutDowngrade() {
        assertEquals(List.of(
                        "org.openrewrite.gradle.UpdateJavaCompatibility",
                        "org.openrewrite.gradle.UpdateJavaCompatibility"),
                ConfigureSpringExpressionBuild.officialGradleRecipes().stream()
                        .map(org.openrewrite.Recipe::getName).toList());
        rewriteRun(
                spec -> spec.recipe(Environment.builder().scanRuntimeClasspath().build()
                        .activateRecipes(CONFIGURE)),
                buildGradle(
                        """
                        sourceCompatibility = JavaVersion.VERSION_1_8
                        targetCompatibility = 11
                        dependencies { implementation 'org.springframework:spring-expression:6.2.18' }
                        """,
                        """
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = 17
                        dependencies { implementation 'org.springframework:spring-expression:6.2.18' }
                        """),
                buildGradleKts(
                        """
                        sourceCompatibility = JavaVersion.VERSION_11
                        targetCompatibility = JavaVersion.VERSION_11
                        dependencies {
                          implementation(
                            group = "org.springframework",
                            name = "spring-expression",
                            version = "6.2.19"
                          )
                        }
                        """,
                        """
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                        dependencies {
                          implementation(
                            group = "org.springframework",
                            name = "spring-expression",
                            version = "6.2.19"
                          )
                        }
                        """),
                buildGradle("""
                        sourceCompatibility = JavaVersion.VERSION_21
                        targetCompatibility = 21
                        dependencies { implementation 'org.springframework:spring-expression:6.2.19' }
                        """)
        );
    }

    @Test
    void configureRequiresAnExactLocallyOwnedPrimaryDeclaration() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder().scanRuntimeClasspath().build()
                        .activateRecipes(CONFIGURE)),
                xml(pom("""
                        <properties><java.version>11</java.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("${spring.version}", ""))),
                    source -> source.path("unknown/pom.xml")),
                xml(pom("""
                        <properties><java.version>11</java.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("7.0.0", ""))),
                    source -> source.path("newer/pom.xml")),
                buildGradle("""
                        sourceCompatibility = 11
                        dependencies { implementation libs.spring.expression }
                        """)
        );
    }

    @Test
    void buildMarkersAreTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(dependencyPom("7.0.0", ""), source -> source.path("pom.xml")
                        .afterRecipe(after -> assertMarks(after, 1, "目标版本冲突（禁止降级）"))));
    }

    @Test
    void marksJpmsSpringExpressionBoundary() {
        rewriteRun(
                spec -> spec.expectedCyclesThatMakeChanges(1),
                text(
                        """
                        module example.application {
                            requires spring.expression;
                        }
                        """,
                        source -> source.path("src/main/java/module-info.java").afterRecipe(after ->
                                assertMarks(after, 1, "JPMS/native-image boundaries")))
        );
    }

    private static String pom(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dependencyPom(String version, String metadata) {
        return pom("<dependencies>" + dependency(version, metadata) + "</dependencies>");
    }

    private static String dependency(String version, String metadata) {
        return "<dependency><groupId>org.springframework</groupId><artifactId>spring-expression</artifactId>" +
               (version.isEmpty() ? "" : "<version>" + version + "</version>") +
               metadata + "</dependency>";
    }

    private static void assertMarks(SourceFile source, int expected, String... fragments) {
        List<String> descriptions = new ArrayList<>();
        new TreeVisitor<Tree, Integer>() {
            @Override
            public Tree preVisit(Tree tree, Integer integer) {
                tree.getMarkers().findAll(SearchResult.class).stream()
                        .map(SearchResult::getDescription).forEach(descriptions::add);
                return tree;
            }
        }.visit(source, 0);
        assertEquals(expected, descriptions.size(), descriptions.toString());
        for (String fragment : fragments) {
            assertTrue(descriptions.stream().anyMatch(message ->
                            message != null && message.contains(fragment)),
                    () -> "Missing '" + fragment + "' in " + descriptions);
        }
    }
}
