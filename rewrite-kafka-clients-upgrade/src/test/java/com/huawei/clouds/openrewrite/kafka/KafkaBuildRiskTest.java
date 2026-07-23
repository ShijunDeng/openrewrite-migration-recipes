package com.huawei.clouds.openrewrite.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class KafkaBuildRiskTest implements RewriteTest {
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.kafka.AuditKafkaClient4Compatibility";

    @ParameterizedTest(name = "marks Maven Java property {0}={1}")
    @MethodSource("javaProperties")
    void marksUnsupportedMavenJavaProperties(String property, String version) {
        assertPomMarker("<properties><" + property + ">" + version + "</" + property + "></properties>" +
                        targetDependency(), FindKafkaClientBuildRisks.JAVA_MESSAGE);
    }

    static Stream<Arguments> javaProperties() {
        return Stream.of("java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target")
                .flatMap(property -> Stream.of("1.8", "8", "9", "10")
                        .map(version -> Arguments.of(property, version)));
    }

    @ParameterizedTest(name = "marks compiler plugin {0}={1}")
    @MethodSource("compilerLevels")
    void marksUnsupportedCompilerPluginConfiguration(String element, String version) {
        String plugin = "<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                        "<artifactId>maven-compiler-plugin</artifactId><configuration><" + element + ">" + version +
                        "</" + element + "></configuration></plugin></plugins></build>";
        assertPomMarker(plugin + targetDependency(), FindKafkaClientBuildRisks.JAVA_MESSAGE);
    }

    @Test
    void resolvesNestedMavenJavaPropertiesBeforeMarkingTheBaseline() {
        String plugin = "<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                        "<artifactId>maven-compiler-plugin</artifactId><configuration><release>${java.version}</release>" +
                        "</configuration></plugin></plugins></build>";
        assertPomMarker("<properties><jdk.level>8</jdk.level><java.version>${jdk.level}</java.version></properties>" +
                        plugin + targetDependency(), FindKafkaClientBuildRisks.JAVA_MESSAGE);

        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)), xml(project(
                "<properties><jdk.level>17</jdk.level><java.version>${jdk.level}</java.version></properties>" +
                plugin + targetDependency()), source -> source.path("pom.xml")));
    }

    static Stream<Arguments> compilerLevels() {
        return Stream.of("source", "target", "release").flatMap(element -> Stream.of("1.8", "8", "9", "10")
                .map(version -> Arguments.of(element, version)));
    }

    @ParameterizedTest(name = "marks unresolved client {0}")
    @ValueSource(strings = {"2.4.1", "3.7.0", "3.7.1", "4.0.1", "4.1.1", "4.1.20", "[3.7,4.0)", "${kafka.version}", "LATEST"})
    void marksUnselectedAndPreTargetClientDeclarations(String version) {
        assertPomMarker("<dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId>" +
                        "<version>" + version + "</version></dependency></dependencies>",
                FindKafkaClientBuildRisks.targetConflict(version) ?
                        FindKafkaClientBuildRisks.TARGET_CONFLICT :
                        FindKafkaClientBuildRisks.UNRESOLVED_MESSAGE);
    }

    @ParameterizedTest(name = "marks custom client artifact {0}")
    @ValueSource(strings = {"<classifier>tests</classifier>", "<type>test-jar</type>", "<type>pom</type>", "<type>zip</type>", "<type>war</type>"})
    void marksCustomArtifactShapes(String extra) {
        assertPomMarker("<dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId>" +
                        "<version>3.7.0</version>" + extra + "</dependency></dependencies>",
                FindKafkaClientBuildRisks.CUSTOM_ARTIFACT_MESSAGE);
    }

    @ParameterizedTest(name = "marks companion {0}")
    @ValueSource(strings = {
            "kafka-streams", "kafka-streams-test-utils", "kafka_2.12", "kafka_2.13",
            "connect-api", "connect-runtime", "connect", "connect-basic-auth-extension", "connect-file",
            "connect-json", "connect-mirror", "connect-mirror-client", "connect-transforms",
            "kafka-streams-scala_2.12", "kafka-streams-scala_2.13", "kafka-tools", "kafka-server",
            "kafka-server-common"
    })
    void marksAdjacentKafkaComponents(String artifact) {
        assertPomMarker(targetDependency() + "<dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>" +
                        artifact + "</artifactId><version>3.7.0</version></dependency></dependencies>",
                FindKafkaClientBuildRisks.COMPANION_MESSAGE);
    }

    @ParameterizedTest(name = "marks Gradle Java baseline {0}")
    @MethodSource("gradleBaselines")
    void marksUnsupportedGradleJavaBaselines(String language, String build) {
        if ("kotlin".equals(language)) {
            assertKotlinMarker(build, FindKafkaClientBuildRisks.JAVA_MESSAGE);
        } else {
            assertGroovyMarker(build, FindKafkaClientBuildRisks.JAVA_MESSAGE);
        }
    }

    static Stream<Arguments> gradleBaselines() {
        return Stream.of(
                Arguments.of("groovy", "sourceCompatibility = '1.8'"),
                Arguments.of("groovy", "targetCompatibility = JavaVersion.VERSION_1_8"),
                Arguments.of("groovy", "java { toolchain { languageVersion = JavaLanguageVersion.of(8) } }"),
                Arguments.of("groovy", "java { toolchain { languageVersion = JavaLanguageVersion.of(10) } }"),
                Arguments.of("kotlin", "java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }"),
                Arguments.of("kotlin", "kotlin { jvmToolchain(8) }"),
                Arguments.of("kotlin", "kotlin { jvmToolchain(10) }")
        );
    }

    @ParameterizedTest(name = "marks Gradle unresolved declaration {0}")
    @ValueSource(strings = {"3.7.0", "3.7.1", "4.1.1", "4.1.20", "3.+", "${kafkaVersion}"})
    void marksGradleUnresolvedClient(String version) {
        assertGroovyMarker("dependencies { implementation \"org.apache.kafka:kafka-clients:" + version + "\" }",
                FindKafkaClientBuildRisks.targetConflict(version) ?
                        FindKafkaClientBuildRisks.TARGET_CONFLICT :
                        FindKafkaClientBuildRisks.UNRESOLVED_MESSAGE);
    }

    @Test
    void marksHigherVersionsWithExactNoDowngradeMessageAcrossBuildDialects() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                xml(project("""
                        <dependencies><dependency>
                          <groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId>
                          <version>5.0.0</version>
                        </dependency></dependencies>
                        """), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>5.0.0</version>"), printed);
                            assertTrue(printed.contains(FindKafkaClientBuildRisks.TARGET_CONFLICT), printed);
                        })),
                buildGradle("""
                        dependencies {
                            implementation 'org.apache.kafka:kafka-clients:4.1.3'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("kafka-clients:4.1.3"), printed);
                            assertTrue(printed.contains(FindKafkaClientBuildRisks.TARGET_CONFLICT), printed);
                        })),
                buildGradleKts("""
                        dependencies {
                            implementation("org.apache.kafka:kafka-clients:999999999999999999999999.0.0")
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("999999999999999999999999.0.0"), printed);
                            assertTrue(printed.contains(FindKafkaClientBuildRisks.TARGET_CONFLICT), printed);
                        })));
    }

    @Test
    void marksCustomGradleArtifactWithItsSpecificReason() {
        assertGroovyMarker("dependencies { implementation 'org.apache.kafka:kafka-clients:3.7.0@zip' }",
                FindKafkaClientBuildRisks.CUSTOM_ARTIFACT_MESSAGE);
        assertGroovyMarker("dependencies { implementation group: 'org.apache.kafka', name: 'kafka-clients', version: '3.7.0', classifier: 'tests' }",
                FindKafkaClientBuildRisks.CUSTOM_ARTIFACT_MESSAGE);
    }

    @Test
    void acceptsVersionlessDirectDependencyOwnedByLocalTargetManagement() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)), xml(project("""
                <dependencyManagement><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency></dependencies></dependencyManagement>
                <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId></dependency></dependencies>
                """), source -> source.path("pom.xml")));
    }

    @Test
    void rejectsPluginDependencyAsProjectOwnershipProof() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)), xml(project("""
                <properties><java.version>8</java.version></properties>
                <build><plugins><plugin><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>3.7.0</version></dependency>
                </dependencies></plugin></plugins></build>
                """), source -> source.path("pom.xml")),
                xml(project("""
                <properties><java.version>8</java.version></properties>
                <configuration><dependencies><dependency>
                  <groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>3.7.0</version>
                </dependency></dependencies></configuration>
                """), source -> source.path("fake/pom.xml")),
                buildGradle("""
                        plugins { id 'java' }
                        sourceCompatibility = '8'
                        implementation 'org.apache.kafka:kafka-clients:3.7.0'
                        """, source -> source.path("dsl/build.gradle")));
    }

    @Test
    void doesNotTreatCoordinatePrefixesAsClientOrCompanionOwnership() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                buildGradle("""
                        plugins { id 'java' }
                        sourceCompatibility = '8'
                        dependencies {
                            implementation 'org.apache.kafka:kafka-clients-extra:3.7.0'
                            implementation 'org.apache.kafka:kafka-streams-extra:3.7.0'
                        }
                        """),
                buildGradleKts("""
                        plugins { java }
                        kotlin { jvmToolchain(8) }
                        dependencies { implementation("org.apache.kafka:kafka-clients-extra:3.7.0") }
                        """, source -> source.path("prefix/build.gradle.kts")));
    }

    @Test
    void leavesGeneratedBuildDescriptorsUnaudited() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                xml(project("<properties><java.version>8</java.version></properties>" + targetDependency()),
                        source -> source.path("target/pom.xml")),
                buildGradle("sourceCompatibility = '8'\ndependencies { implementation 'org.apache.kafka:kafka-clients:4.1.2' }",
                        source -> source.path("build/generated/build.gradle")));
    }

    @ParameterizedTest(name = "does not mark build lookalike {index}")
    @ValueSource(strings = {
            "<properties><java.version>8</java.version></properties>",
            "<dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-streams</artifactId><version>3.7.0</version></dependency></dependencies>",
            "<dependencies><dependency><groupId>com.example</groupId><artifactId>kafka-clients</artifactId><version>3.7.0</version></dependency></dependencies>",
            "<metadata><kafka-clients>3.7.0</kafka-clients></metadata>",
            "<properties><java.version>11</java.version></properties>" +
                    "<dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency></dependencies>",
            "<properties><java.version>17</java.version></properties>" +
                    "<dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency></dependencies>"
    })
    void leavesUnownedAndSupportedPomValuesUnmarked(String body) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                xml(project(body), source -> source.path("pom.xml")));
    }

    private void assertPomMarker(String body, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                xml(project(body), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    private void assertGroovyMarker(String body, String message) {
        String sourceText = "plugins { id 'java' }\n" + body + "\ndependencies { implementation 'org.apache.kafka:kafka-clients:4.1.2' }";
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                buildGradle(sourceText, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    private void assertKotlinMarker(String body, String message) {
        String sourceText = "plugins { java }\n" + body + "\ndependencies { implementation(\"org.apache.kafka:kafka-clients:4.1.2\") }";
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                buildGradleKts(sourceText, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    private static String targetDependency() {
        return "<dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency></dependencies>";
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>" + body + "</project>";
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.kafka")
                .scanYamlResources().build();
    }
}
