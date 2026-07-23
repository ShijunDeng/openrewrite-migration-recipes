package com.huawei.clouds.openrewrite.orgjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeOrgJsonDependencyTest implements RewriteTest {
    @Override public void defaults(RecipeSpec spec) { spec.recipe(new UpgradeSelectedOrgJsonDependency()); }

    @ParameterizedTest @ValueSource(strings = {"20160810", "20180813", "20190722", "20200518", "20220320", "20230227", "3.11.2"})
    void upgradesEveryWorkbookSource(String version) { rewriteRun(xml(pom(version), pom("20250107"), s -> s.path("pom.xml"))); }

    @Test void whitelistMatchesRows456Through462() {
        assertEquals(Set.of("20160810", "20180813", "20190722", "20200518", "20220320", "20230227", "3.11.2"), OrgJsonSupport.SOURCES);
    }

    @Test void rootProfileAndDependencyManagementAreOwned() {
        String before = project("<dependencies>" + dep("20160810") + "</dependencies><dependencyManagement><dependencies>" + dep("20180813") +
                "</dependencies></dependencyManagement><profiles><profile><id>it</id><dependencies>" + dep("20230227") + "</dependencies></profile></profiles>");
        String after = before.replace("20160810", "20250107").replace("20180813", "20250107").replace("20230227", "20250107");
        rewriteRun(xml(before, after, s -> s.path("pom.xml")));
    }

    @Test void upgradesExclusivePropertyAndProfileOverride() {
        rewriteRun(xml(project("<properties><v>20190722</v></properties><dependencies>" + dep("${v}") + "</dependencies>"),
                project("<properties><v>20250107</v></properties><dependencies>" + dep("${v}") + "</dependencies>"), s -> s.path("pom.xml")),
                xml(project("<properties><v>1</v></properties><profiles><profile><id>it</id><properties><v>20220320</v></properties><dependencies>" + dep("${v}") + "</dependencies></profile></profiles>"),
                project("<properties><v>1</v></properties><profiles><profile><id>it</id><properties><v>20250107</v></properties><dependencies>" + dep("${v}") + "</dependencies></profile></profiles>"), s -> s.path("nested/pom.xml")));
    }

    @ParameterizedTest @MethodSource("ambiguous")
    void preservesSharedOrAmbiguousProperty(String label, String source) { rewriteRun(xml(source, s -> s.path("pom.xml"))); }
    static Stream<Arguments> ambiguous() { return Stream.of(
            Arguments.of("shared", project("<properties><v>20160810</v></properties><dependencies>" + dep("${v}") + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${v}</version></dependency></dependencies>")),
            Arguments.of("build", project("<properties><v>20160810</v></properties><dependencies>" + dep("${v}") + "</dependencies><build><finalName>${v}</finalName></build>")),
            Arguments.of("duplicate", project("<properties><v>20160810</v><v>20160810</v></properties><dependencies>" + dep("${v}") + "</dependencies>")),
            Arguments.of("attribute", project("<properties><v>20160810</v></properties><dependencies>" + dep("${v}") + "</dependencies><x value=\"${v}\"/>")));
    }

    @ParameterizedTest @ValueSource(strings = {"20160807", "20201115", "20210307", "20211205", "20220924", "20230618", "20231013", "20240303", "20241224", "20250107", "20250517"})
    void doesNotWidenSources(String version) { rewriteRun(xml(pom(version), s -> s.path("pom.xml"))); }

    @Test void protectsVariantsVersionlessPluginAndCustomOwners() {
        rewriteRun(xml(project("<dependencies>" + noVersion() + dep("20160810", "<classifier>tests</classifier>") + dep("20180813", "<type>zip</type>") + "</dependencies>"), s -> s.path("pom.xml")),
                xml(project("<build><plugins><plugin><artifactId>x</artifactId><dependencies>" + dep("20190722") + "</dependencies></plugin></plugins></build>"), s -> s.path("pom.xml")),
                xml(project("<company><dependencies>" + dep("20200518") + "</dependencies></company>"), s -> s.path("pom.xml")));
    }

    @Test void upgradesRootGradleFormsAndMaps() {
        rewriteRun(buildGradle("dependencies { implementation 'org.json:json:20160810' }", "dependencies { implementation 'org.json:json:20250107' }", s -> s.path("build.gradle")),
                buildGradleKts("dependencies { implementation(\"org.json:json:3.11.2\") }", "dependencies { implementation(\"org.json:json:20250107\") }", s -> s.path("build.gradle.kts")),
                buildGradle("dependencies { implementation group: 'org.json', name: 'json', version: '20220320' }", "dependencies { implementation group: 'org.json', name: 'json', version: '20250107' }"),
                buildGradle("dependencies { runtimeOnly([group: 'org.json', name: 'json', version: '20230227']) }", "dependencies { runtimeOnly([group: 'org.json', name: 'json', version: '20250107']) }"));
    }

    @ParameterizedTest @MethodSource("nestedGradle")
    void ignoresNestedGradle(String label, String source) { rewriteRun(buildGradle(source)); }
    static Stream<Arguments> nestedGradle() { String d = "'org.json:json:20160810'"; return Stream.of(
            Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
            Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
            Arguments.of("project", "project(':a') { dependencies { implementation " + d + " } }"),
            Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
            Arguments.of("custom", "custom { dependencies { implementation " + d + " } }")); }

    @ParameterizedTest @ValueSource(strings = {"target", "build", "generated", "generatedSources", "installation", "INSTALL-cache", ".gradle", ".m2", "node_modules"})
    void ignoresGeneratedParents(String parent) { rewriteRun(xml(pom("20160810"), s -> s.path(parent + "/pom.xml"))); }

    @Test void variantsVariablesAndIdempotence() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradle("dependencies { implementation 'org.json:json:20180813' }", "dependencies { implementation 'org.json:json:20250107' }", s -> s.path("install.gradle")),
                buildGradle("dependencies { implementation 'org.json:json:20160810:tests' }"),
                buildGradle("dependencies { implementation 'org.json:json:20160810@zip' }"),
                buildGradle("dependencies { implementation \"org.json:json:$jsonVersion\" }"));
    }

    @Test void publicRecipesHaveStrictComposition() {
        Environment env = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.orgjson").build();
        var upgrade = env.activateRecipes("com.huawei.clouds.openrewrite.orgjson.UpgradeOrgJsonTo20250107");
        var migrate = env.activateRecipes("com.huawei.clouds.openrewrite.orgjson.MigrateOrgJsonTo20250107");
        assertEquals(1, upgrade.getRecipeList().size());
        assertEquals(UpgradeSelectedOrgJsonDependency.class, upgrade.getRecipeList().get(0).getClass());
        assertEquals("com.huawei.clouds.openrewrite.orgjson.UpgradeOrgJsonTo20250107", migrate.getRecipeList().get(0).getName());
    }

    @Test void recommendedRecipeExecutesDependencyAndApiAutomation() {
        Environment env = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.orgjson").build();
        rewriteRun(spec -> spec.recipe(env.activateRecipes("com.huawei.clouds.openrewrite.orgjson.MigrateOrgJsonTo20250107"))
                        .parser(JavaParser.fromJavaVersion().classpath("json")),
                xml(pom("20160810"), pom("20250107"), s -> s.path("pom.xml")),
                java("import org.json.XMLParserConfiguration; class A { Object x = new XMLParserConfiguration(true); }",
                        "import org.json.XMLParserConfiguration; class A { Object x = new XMLParserConfiguration().withKeepStrings(true); }"));
    }

    static String pom(String v) { return project("<dependencies>" + dep(v) + "</dependencies>"); }
    static String project(String body) { return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId><version>1</version>" + body + "</project>"; }
    static String dep(String v) { return dep(v, ""); }
    static String dep(String v, String extra) { return "<dependency><groupId>org.json</groupId><artifactId>json</artifactId><version>" + v + "</version>" + extra + "</dependency>"; }
    static String noVersion() { return "<dependency><groupId>org.json</groupId><artifactId>json</artifactId></dependency>"; }
}
