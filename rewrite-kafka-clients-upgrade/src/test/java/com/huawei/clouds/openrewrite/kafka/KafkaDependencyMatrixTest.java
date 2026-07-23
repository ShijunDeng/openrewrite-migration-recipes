package com.huawei.clouds.openrewrite.kafka;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class KafkaDependencyMatrixTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.kafka.UpgradeKafkaClientsDependencyTo4_1_2";
    private static final String[] VERSIONS = {
            "2.4.1", "2.5.1", "3.1.2", "3.4.0", "3.4.1",
            "3.5.1", "3.6.0", "3.6.1", "3.6.2", "3.7.0"
    };

    @ParameterizedTest(name = "Maven {0} handles {1}")
    @MethodSource("mavenMatrix")
    void handlesEveryVisibleVersionAcrossMavenOwnershipContexts(String context, String version) {
        String before = maven(context, version, false);
        if ("plugin".equals(context)) {
            rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                    xml(before, source -> source.path("pom.xml")));
        } else {
            rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                    pomXml(before, maven(context, version, true)));
        }
    }

    static Stream<Arguments> mavenMatrix() {
        return Stream.of("direct", "managed", "profile", "plugin", "runtime", "optional", "property", "shared-property")
                .flatMap(context -> Stream.of(VERSIONS).map(version -> Arguments.of(context, version)));
    }

    @ParameterizedTest(name = "Gradle {0} upgrades {1}")
    @MethodSource("gradleMatrix")
    void upgradesEveryVisibleVersionAcrossGradleNotations(String notation, String version) {
        String before = gradle(notation, version);
        String after = gradle(notation, "4.1.2");
        if (notation.startsWith("kotlin")) {
            rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                    buildGradleKts(before, after, source -> source.path(notation + "/build.gradle.kts")));
        } else {
            rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                    buildGradle(before, after, source -> source.path(notation + "/build.gradle")));
        }
    }

    static Stream<Arguments> gradleMatrix() {
        return Stream.of("groovy-single", "groovy-double", "groovy-map", "groovy-map-parens", "kotlin-implementation", "kotlin-api")
                .flatMap(notation -> Stream.of(VERSIONS).map(version -> Arguments.of(notation, version)));
    }

    @ParameterizedTest(name = "preserves unlisted Maven version {0}")
    @ValueSource(strings = {
            "0.11.0.3", "1.0.2", "2.0.1", "2.1.1", "2.2.2", "2.3.1", "2.4.0", "2.5.0",
            "2.6.3", "2.7.2", "2.8.2", "3.0.2", "3.1.0", "3.1.1", "3.2.3", "3.3.2",
            "3.4.2", "3.5.0", "3.5.2", "3.6.3", "3.7.1", "3.7.2", "3.8.0", "3.9.0",
            "4.0.0", "4.0.1", "4.1.0", "4.1.1", "4.1.2", "4.1.3", "4.2.0", "4.2.1", "5.0.0",
            "LATEST", "RELEASE",
            "[3.7,4.0)", "(,4.0]", "3.+", "3.x", "${revision}", "${kafka.version}-custom"
    })
    void preservesUnlistedTargetNewerRangeAndDynamicMavenVersions(String version) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                xml(maven("direct", version, false), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "preserves noncanonical Gradle declaration {0}")
    @ValueSource(strings = {
            "org.apache.kafka:kafka-clients:3.7.1", "org.apache.kafka:kafka-clients:4.1.2",
            "org.apache.kafka:kafka-clients:4.1.3", "org.apache.kafka:kafka-clients:4.2.1",
            "org.apache.kafka:kafka-clients:3.+", "org.apache.kafka:kafka-clients:[3.7,4.0)",
            "org.apache.kafka:kafka-clients:3.7.0@zip", "org.apache.kafka:kafka-streams:3.7.0",
            "org.apache.kafka:kafka_2.13:3.7.0", "com.example:kafka-clients:3.7.0",
            "org.apache.kafka:kafka-clients-test:3.7.0", "org.apache.kafka:kafka-client:3.7.0"
    })
    void preservesUnlistedCustomAndSimilarGradleCoordinates(String coordinate) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), buildGradle(
                "plugins { id 'java' }\ndependencies { implementation '" + coordinate + "' }"));
    }

    @ParameterizedTest(name = "preserves custom Maven shape {0}")
    @ValueSource(strings = {"classifier", "test-jar", "pom", "war", "zip", "versionless", "other-group", "other-artifact"})
    void preservesCustomVersionlessAndSimilarMavenDeclarations(String shape) {
        String extra = switch (shape) {
            case "classifier" -> "<classifier>tests</classifier>";
            case "test-jar" -> "<type>test-jar</type>";
            case "pom" -> "<type>pom</type>";
            case "war" -> "<type>war</type>";
            case "zip" -> "<type>zip</type>";
            default -> "";
        };
        String group = "other-group".equals(shape) ? "com.example" : "org.apache.kafka";
        String artifact = "other-artifact".equals(shape) ? "kafka-streams" : "kafka-clients";
        String version = "versionless".equals(shape) ? "" : "<version>3.7.0</version>";
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), xml(
                "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>" +
                "<dependencies><dependency><groupId>" + group + "</groupId><artifactId>" + artifact +
                "</artifactId>" + version + extra + "</dependency></dependencies></project>", source -> source.path("pom.xml")));
    }

    @Test
    void upgradesPropertyReferencedByMultipleOwnedClientDeclarations() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><kafka.version>3.6.2</kafka.version></properties>
                  <dependencies>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><kafka.version>4.1.2</kafka.version></properties>
                  <dependencies>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void preservesProfileShadowedPropertyInsteadOfResolvingTheWrongScope() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), xml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><kafka.version>3.6.2</kafka.version></properties>
                  <profiles><profile><id>newer</id>
                    <properties><kafka.version>3.7.1</kafka.version></properties>
                    <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void changesOnlyTheRootPropertyTagAndNeverAnUnrelatedSameNamedNode() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><kafka.version>3.6.2</kafka.version></properties>
                  <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency></dependencies>
                  <build><plugins><plugin><groupId>x</groupId><artifactId>x</artifactId><configuration><kafka.version>3.6.2</kafka.version></configuration></plugin></plugins></build>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><kafka.version>4.1.2</kafka.version></properties>
                  <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency></dependencies>
                  <build><plugins><plugin><groupId>x</groupId><artifactId>x</artifactId><configuration><kafka.version>3.6.2</kafka.version></configuration></plugin></plugins></build>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void treatsAPropertyUsedByAPluginDependencyAsSharedAndInlinesOnlyTheProjectClient() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><kafka.version>3.6.2</kafka.version></properties>
                  <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency></dependencies>
                  <build><plugins><plugin><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency>
                  </dependencies></plugin></plugins></build>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><kafka.version>3.6.2</kafka.version></properties>
                  <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency></dependencies>
                  <build><plugins><plugin><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency>
                  </dependencies></plugin></plugins></build>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void leavesGeneratedMavenGroovyAndKotlinBuildDescriptorsUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                xml(maven("direct", "3.7.0", false), source -> source.path("target/pom.xml")),
                buildGradle(gradle("groovy-single", "3.7.0"), source -> source.path("build/generated/build.gradle")),
                buildGradleKts(gradle("kotlin-api", "3.7.0"), source -> source.path("out/build.gradle.kts")),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                          <configuration><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>3.7.0</version></dependency></dependencies></configuration>
                        </project>
                        """, source -> source.path("fake/pom.xml")),
                buildGradle("""
                        plugins { id 'java' }
                        implementation 'org.apache.kafka:kafka-clients:3.7.0'
                        dependencies {
                            implementation group: 'org.apache.kafka', name: 'kafka-clients', version: '3.7.0', classifier: 'tests'
                        }
                        """, source -> source.path("ownership/build.gradle")));
    }

    private static String maven(String context, String version, boolean migrated) {
        String target = migrated ? "4.1.2" : version;
        String dependency = "<dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>" + target + "</version></dependency>";
        String body = switch (context) {
            case "managed" -> "<dependencyManagement><dependencies>" + dependency + "</dependencies></dependencyManagement>";
            case "profile" -> "<profiles><profile><id>kafka</id><dependencies>" + dependency + "</dependencies></profile></profiles>";
            case "plugin" -> "<build><plugins><plugin><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies>" + dependency + "</dependencies></plugin></plugins></build>";
            case "runtime" -> "<dependencies>" + dependency.replace("</dependency>", "<scope>runtime</scope></dependency>") + "</dependencies>";
            case "optional" -> "<dependencies>" + dependency.replace("</dependency>", "<optional>true</optional><exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions></dependency>") + "</dependencies>";
            case "property" -> "<properties><kafka.version>" + target + "</kafka.version></properties><dependencies>" +
                    dependency.replace("<version>" + target + "</version>", "<version>${kafka.version}</version>") + "</dependencies>";
            case "shared-property" -> "<properties><kafka.version>" + version + "</kafka.version></properties><dependencies>" +
                    dependency.replace("<version>" + target + "</version>", migrated ? "<version>4.1.2</version>" : "<version>${kafka.version}</version>") +
                    "<dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-streams</artifactId><version>${kafka.version}</version></dependency></dependencies>";
            default -> "<dependencies>" + dependency + "</dependencies>";
        };
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>" + body + "</project>";
    }

    private static String gradle(String notation, String version) {
        String declaration = switch (notation) {
            case "groovy-double" -> "implementation \"org.apache.kafka:kafka-clients:" + version + "\"";
            case "groovy-map" -> "api group: 'org.apache.kafka', name: 'kafka-clients', version: '" + version + "'";
            case "groovy-map-parens" -> "runtimeOnly(group: 'org.apache.kafka', name: 'kafka-clients', version: '" + version + "')";
            case "kotlin-implementation" -> "implementation(\"org.apache.kafka:kafka-clients:" + version + "\")";
            case "kotlin-api" -> "api(\"org.apache.kafka:kafka-clients:" + version + "\")";
            default -> "implementation 'org.apache.kafka:kafka-clients:" + version + "'";
        };
        return notation.startsWith("kotlin") ? "plugins { java }\ndependencies { " + declaration + " }" :
                "plugins { id 'java' }\ndependencies { " + declaration + " }";
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.kafka")
                .scanYamlResources().build();
    }
}
