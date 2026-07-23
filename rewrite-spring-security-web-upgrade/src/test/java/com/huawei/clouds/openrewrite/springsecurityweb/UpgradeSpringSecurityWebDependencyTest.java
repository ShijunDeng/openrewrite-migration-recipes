package com.huawei.clouds.openrewrite.springsecurityweb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeSpringSecurityWebDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedSpringSecurityWebDependency());
    }

    @ParameterizedTest(name = "Maven workbook source {0}")
    @ValueSource(strings = {
            "5.1.5.RELEASE", "5.6.12", "5.7.14", "5.8.3", "5.8.6", "5.8.7", "5.8.8",
            "5.8.11", "5.8.15", "5.8.16", "6.2.2", "6.2.3", "6.2.6", "6.2.7", "6.2.8",
            "6.3.4", "6.4.4", "6.4.6", "6.4.10", "6.4.11", "6.4.13", "6.5.1",
            "6.5.9", "6.5.10"
    })
    void upgradesEveryApprovedMavenSource(String version) {
        rewriteRun(xml(pom(version), pom("6.5.11"), source -> source.path("pom.xml")));
    }

    @Test
    void workbookPartitionIsExactAndConflictVersionsAreExcluded() {
        assertEquals(26, SpringSecurityWebUpgradeSupport.WORKBOOK_VERSIONS.size());
        assertEquals(24, SpringSecurityWebUpgradeSupport.UPGRADE_VERSIONS.size());
        assertEquals(Set.of("7.0.0", "7.0.4"), SpringSecurityWebUpgradeSupport.CONFLICT_VERSIONS);
        assertTrue(SpringSecurityWebUpgradeSupport.WORKBOOK_VERSIONS
                .containsAll(SpringSecurityWebUpgradeSupport.UPGRADE_VERSIONS));
        assertTrue(SpringSecurityWebUpgradeSupport.WORKBOOK_VERSIONS
                .containsAll(SpringSecurityWebUpgradeSupport.CONFLICT_VERSIONS));
        assertTrue(SpringSecurityWebUpgradeSupport.UPGRADE_VERSIONS.stream()
                .noneMatch(SpringSecurityWebUpgradeSupport.CONFLICT_VERSIONS::contains));
    }

    @ParameterizedTest(name = "never downgrade {0}")
    @ValueSource(strings = {"7.0.0", "7.0.4", "6.5.12", "6.6.0", "99.0.0"})
    void neverDowngradesHigherVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "fixed/dynamic no-op {0}")
    @ValueSource(strings = {
            "5.8.10", "6.0.0", "6.4.12", "6.5.11", "6.5.11-RC1",
            "${spring-security.version}", "[5.8,7)", "6.+", "+", "latest.release"
    })
    void neverGuessesVersionsOutsideTheExactWhitelist(String version) {
        rewriteRun(xml(pom(version), source -> source.path("maven-" + Math.abs(version.hashCode()) + "/pom.xml")));
    }

    @Test
    void upgradesDependencyManagementProfileAndExclusivePropertyOwners() {
        rewriteRun(
                xml(project("<dependencyManagement><dependencies>" + dep("6.4.13") +
                            "</dependencies></dependencyManagement>"),
                    project("<dependencyManagement><dependencies>" + dep("6.5.11") +
                            "</dependencies></dependencyManagement>"),
                    source -> source.path("management/pom.xml")),
                xml(project("<profiles><profile><id>it</id><dependencies>" + dep("5.8.16") +
                            "</dependencies></profile></profiles>"),
                    project("<profiles><profile><id>it</id><dependencies>" + dep("6.5.11") +
                            "</dependencies></profile></profiles>"),
                    source -> source.path("profile/pom.xml")),
                xml(project("<properties><security.web.version>5.7.14</security.web.version></properties>" +
                            "<dependencies>" + dep("${security.web.version}") + "</dependencies>"),
                    project("<properties><security.web.version>6.5.11</security.web.version></properties>" +
                            "<dependencies>" + dep("${security.web.version}") + "</dependencies>"),
                    source -> source.path("property/pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous owner {0}")
    @MethodSource("ambiguousOwners")
    void leavesSharedDuplicateAndShadowedPropertiesUnchanged(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path(label + "/pom.xml")));
    }

    static Stream<Arguments> ambiguousOwners() {
        return Stream.of(
                Arguments.of("shared-dependency", project(
                        "<properties><v>5.8.16</v></properties><dependencies>" + dep("${v}") +
                        "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${v}</version></dependency>" +
                        "</dependencies>")),
                Arguments.of("shared-build", project(
                        "<properties><v>6.4.13</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("duplicate", project(
                        "<properties><v>5.7.14</v><v>5.7.14</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies>")),
                Arguments.of("profile-shadow", project(
                        "<properties><v>5.8.16</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies><profiles><profile><id>x</id><properties><v>6.4.13</v></properties>" +
                        "</profile></profiles>")));
    }

    @Test
    void upgradesGroovyStringMapLiteralAndKotlinString() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework.security:spring-security-web:5.8.16' }",
                        "dependencies { implementation 'org.springframework.security:spring-security-web:6.5.11' }"),
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.springframework.security', name: 'spring-security-web', version: '6.4.13', transitive: false }",
                        "dependencies { runtimeOnly group: 'org.springframework.security', name: 'spring-security-web', version: '6.5.11', transitive: false }"),
                buildGradle(
                        "dependencies { implementation([group: 'org.springframework.security', name: 'spring-security-web', version: '5.7.14']) }",
                        "dependencies { implementation([group: 'org.springframework.security', name: 'spring-security-web', version: '6.5.11']) }"),
                buildGradleKts(
                        "dependencies { implementation(\"org.springframework.security:spring-security-web:6.5.10\") }",
                        "dependencies { implementation(\"org.springframework.security:spring-security-web:6.5.11\") }"));
    }

    @Test
    void variantsPluginDependenciesNestedScopesAndGeneratedFilesAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + dep("5.8.16", "<classifier>tests</classifier>") +
                            dep("6.4.13", "<type>zip</type>") + "</dependencies>"),
                        source -> source.path("variant/pom.xml")),
                xml(project("<build><plugins><plugin><artifactId>x</artifactId><dependencies>" +
                            dep("5.8.16") + "</dependencies></plugin></plugins></build>"),
                        source -> source.path("plugin/pom.xml")),
                buildGradle("buildscript { dependencies { classpath 'org.springframework.security:spring-security-web:5.8.16' } }"),
                buildGradle("subprojects { dependencies { implementation 'org.springframework.security:spring-security-web:6.4.13' } }"),
                buildGradle("dependencies { constraints { implementation 'org.springframework.security:spring-security-web:5.7.14' } }"),
                buildGradle("dependencies { implementation 'org.springframework.security:spring-security-web:5.8.16:tests' }"),
                buildGradle("dependencies { implementation 'org.springframework.security:spring-security-web:6.4.13@zip' }"),
                xml(pom("5.8.16"), source -> source.path("target/generated/pom.xml")));
    }

    @Test
    void higherVersionComparisonUsesUnboundedNumericSegments() {
        assertTrue(SpringSecurityWebUpgradeSupport.targetConflict("6.5.12"));
        assertTrue(SpringSecurityWebUpgradeSupport.targetConflict("6.6.0-RC1"));
        assertTrue(SpringSecurityWebUpgradeSupport.targetConflict("999999999999999999999.0.0"));
        assertFalse(SpringSecurityWebUpgradeSupport.targetConflict("6.5.11"));
        assertFalse(SpringSecurityWebUpgradeSupport.targetConflict("6.5.11-RC1"));
        assertFalse(SpringSecurityWebUpgradeSupport.targetConflict("5.8.16"));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("5.8.16"), pom("6.5.11"), source -> source.path("pom.xml")),
                buildGradle(
                        "dependencies { implementation 'org.springframework.security:spring-security-web:6.4.13' }",
                        "dependencies { implementation 'org.springframework.security:spring-security-web:6.5.11' }"));
    }

    @Test
    void publicUpgradeRecipeContainsOnlyTheStrictRecipe() {
        var recipe = environment().activateRecipes(
                "com.huawei.clouds.openrewrite.springsecurityweb.UpgradeSpringSecurityWebTo6_5_11");
        assertEquals(1, recipe.getRecipeList().size());
        assertEquals(UpgradeSelectedSpringSecurityWebDependency.class.getName(),
                recipe.getRecipeList().get(0).getName());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springsecurityweb")
                .build();
    }

    static String pom(String version) {
        return project("<dependencies>" + dep(version) + "</dependencies>");
    }

    static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>client</artifactId><version>1</version>" + body + "</project>";
    }

    static String dep(String version) {
        return dep(version, "");
    }

    static String dep(String version, String extra) {
        return "<dependency><groupId>org.springframework.security</groupId>" +
               "<artifactId>spring-security-web</artifactId><version>" + version +
               "</version>" + extra + "</dependency>";
    }
}
