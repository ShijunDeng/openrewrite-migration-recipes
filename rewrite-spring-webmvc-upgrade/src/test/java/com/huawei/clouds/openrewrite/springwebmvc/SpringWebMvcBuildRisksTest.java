package com.huawei.clouds.openrewrite.springwebmvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringWebMvcBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringWebMvc6BuildRisks());
    }

    @Test
    void marksVersionOwnerOutsideAndVariantPrecisely() {
        rewriteRun(pomXml(project("<dependencies>" + target(null, "") + target("${spring.version}", "") +
                        target("5.3.22", "") + target("5.3.23", "<classifier>sources</classifier>") +
                        target("6.2.19", "") + "</dependencies>"), source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.OWNER));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.OUTSIDE));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.VARIANT));
            return actual;
        })));
    }

    @Test
    void marksHigherPrimaryVersionsWithExactNoDowngradeMessage() {
        rewriteRun(
                pomXml(project("<dependencies>" + target("7.0.0", "") + "</dependencies>"),
                        source -> source.after(actual -> {
                            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.TARGET_CONFLICT));
                            return actual;
                        })),
                buildGradle("dependencies { implementation 'org.springframework:spring-webmvc:6.2.20' }",
                        source -> source.after(actual -> {
                            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.TARGET_CONFLICT));
                            return actual;
                        })),
                buildGradleKts("""
                        dependencies {
                            implementation("org.springframework:spring-webmvc:999999999999999999999999.0.0")
                        }
                        """, source -> source.after(actual -> {
                            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.TARGET_CONFLICT));
                            return actual;
                        })));
    }

    @Test
    void variantAloneDoesNotGateCompanionOrJavaMarkers() {
        rewriteRun(pomXml(project("<properties><java.version>8</java.version></properties><dependencies>" +
                target("5.3.23", "<classifier>sources</classifier>") +
                "<dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>5.3.23</version></dependency>" +
                "<dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>" +
                "</dependencies>"), source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.VARIANT));
            assertFalse(actual.contains(FindSpringWebMvc6BuildRisks.JAVA));
            assertFalse(actual.contains(FindSpringWebMvc6BuildRisks.ALIGNMENT));
            assertFalse(actual.contains(FindSpringWebMvc6BuildRisks.JAKARTA));
            return actual;
        })));
    }

    @Test
    void standardPrimaryGatesJavaCompilerAndSpringAlignment() {
        rewriteRun(xml(project("""
                <properties><java.version>11</java.version><maven.compiler.parameters>false</maven.compiler.parameters></properties>
                <dependencies>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId><version>5.3.23</version></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>5.3.21</version></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-beans</artifactId></dependency>
                </dependencies>
                <build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><configuration><release>8</release></configuration></plugin></plugins></build>
                """), source -> source.path("pom.xml").after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.JAVA));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.PARAMETERS));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.ALIGNMENT));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.ALIGNMENT_OWNER));
            return actual;
        })));
    }

    @Test
    void marksBootJakartaRemovedIntegrationsAndLegacyContainer() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>2.7.18</version></parent>
                  <groupId>x</groupId><artifactId>a</artifactId><version>1</version><dependencies>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId><version>5.3.23</version></dependency>
                    <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                    <dependency><groupId>org.apache.tiles</groupId><artifactId>tiles-jsp</artifactId><version>3.0.8</version></dependency>
                    <dependency><groupId>commons-fileupload</groupId><artifactId>commons-fileupload</artifactId><version>1.5</version></dependency>
                    <dependency><groupId>org.webjars</groupId><artifactId>webjars-locator-core</artifactId><version>0.48</version></dependency>
                    <dependency><groupId>org.apache.tomcat.embed</groupId><artifactId>tomcat-embed-core</artifactId><version>9.0.117</version></dependency>
                  </dependencies></project>
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.BOOT));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.JAKARTA));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.INTEGRATION));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.CONTAINER));
            return actual;
        })));
    }

    @Test
    void marksBootLinesThatDoNotOwnSpringFramework62() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion>
                          <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.3.13</version></parent>
                          <groupId>x</groupId><artifactId>a</artifactId><version>1</version><dependencies>
                            <dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId><version>6.2.19</version></dependency>
                          </dependencies></project>
                        """, source -> source.path("old-boot/pom.xml").after(actual -> {
                            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.BOOT));
                            return actual;
                        })),
                pomXml(project("<dependencies>" + target("6.2.19", "") +
                        "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId>" +
                        "<version>${boot.version}</version><type>pom</type><scope>import</scope></dependency></dependencies>"),
                        source -> source.path("external-boot/pom.xml").after(actual -> {
                            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.BOOT));
                            return actual;
                        })),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion>
                          <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.15</version></parent>
                          <groupId>x</groupId><artifactId>a</artifactId><version>1</version><dependencies>
                            <dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId><version>6.2.19</version></dependency>
                          </dependencies></project>
                        """, source -> source.path("aligned-boot/pom.xml")));
    }

    @Test
    void arbitrarilyLargeCompanionVersionsDoNotOverflowBuildRiskScan() {
        rewriteRun(xml(project("<dependencies>" + target("6.2.19", "") +
                "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId>" +
                "<version>999999999999999999999999.0</version><type>pom</type><scope>import</scope></dependency>" +
                "<dependency><groupId>org.apache.tomcat.embed</groupId><artifactId>tomcat-embed-core</artifactId>" +
                "<version>999999999999999999999999.0</version></dependency></dependencies>"),
                source -> source.path("pom.xml").after(actual -> {
                    assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.BOOT), actual);
                    assertFalse(actual.contains(FindSpringWebMvc6BuildRisks.CONTAINER), actual);
                    return actual;
                })));
    }

    @Test
    void profileTargetDoesNotLeakIntoSiblingProfile() {
        rewriteRun(pomXml(project("""
                <profiles>
                  <profile><id>mvc</id><properties><java.version>8</java.version></properties><dependencies>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId><version>5.3.23</version></dependency>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>5.3.23</version></dependency>
                  </dependencies></profile>
                  <profile><id>other</id><properties><java.version>7</java.version></properties><dependencies>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>5.3.21</version></dependency>
                  </dependencies></profile>
                </profiles>
                """), source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.JAVA));
            int count = occurrences(actual, FindSpringWebMvc6BuildRisks.ALIGNMENT);
            assertTrue(count == 1, actual);
            return actual;
        })));
    }

    @Test
    void rootTargetAppliesToProfileCompanions() {
        rewriteRun(pomXml(project("<dependencies>" + target("6.2.19", "") + "</dependencies>" +
                "<profiles><profile><id>p</id><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>5.3.23</version></dependency></dependencies></profile></profiles>"),
                source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.ALIGNMENT));
                    return actual;
                })));
    }

    @Test
    void ignoresArbitraryXmlAndGeneratedPom() {
        rewriteRun(
                xml("<root><dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId><version>5.3.23</version></dependency><java.version>8</java.version></root>"),
                pomXml(project("<properties><java.version>8</java.version></properties><dependencies>" + target("5.3.23", "") + "</dependencies>"),
                        source -> source.path("target/generated/pom.xml")));
    }

    @Test
    void marksGradleDynamicOwnerFamilyPlatformJavaAndLegacyDependencies() {
        rewriteRun(buildGradle("""
                sourceCompatibility = JavaVersion.VERSION_11
                def v = '5.3.23'
                dependencies {
                  implementation "org.springframework:spring-webmvc:${v}"
                  implementation platform('org.springframework:spring-framework-bom:5.3.23')
                  implementation 'org.springframework:spring-core:5.3.23'
                  implementation 'javax.servlet:javax.servlet-api:4.0.1'
                  implementation 'org.apache.tomcat.embed:tomcat-embed-core:9.0.117'
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.OWNER));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.JAVA));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.ALIGNMENT));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.JAKARTA));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.CONTAINER));
            return actual;
        })));
    }

    @Test
    void dynamicVariantDoesNotGateCompanions() {
        rewriteRun(buildGradle("""
                sourceCompatibility = 8
                def v = '5.3.23'
                dependencies {
                  implementation "org.springframework:spring-webmvc:${v}:sources"
                  implementation 'org.springframework:spring-core:5.3.23'
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.VARIANT));
            assertFalse(actual.contains(FindSpringWebMvc6BuildRisks.JAVA));
            assertFalse(actual.contains(FindSpringWebMvc6BuildRisks.ALIGNMENT));
            return actual;
        })));
    }

    @Test
    void marksGradleMapVariantsCatalogAndOutsideVersion() {
        rewriteRun(buildGradle("""
                dependencies {
                  implementation libs.spring.webmvc
                  implementation group: 'org.springframework', name: 'spring-webmvc', version: '5.3.23', classifier: 'sources'
                  implementation 'org.springframework:spring-webmvc:5.3.22'
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.OWNER));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.VARIANT));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.OUTSIDE));
            return actual;
        })));
    }

    @Test
    void marksKotlinVariableOwnerAndToolchainButNotLookalikes() {
        rewriteRun(buildGradleKts("""
                java { toolchain { languageVersion.set(JavaLanguageVersion.of(11)) } }
                val v = "5.3.23"
                dependencies {
                    implementation("org.springframework:spring-webmvc:$v")
                    implementation("example:spring-webmvc:5.3.23")
                    implementation("org.springframework:spring-webmvc-extra:5.3.23")
                }
        """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.OWNER));
            assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.JAVA));
            return actual;
        })));
    }

    @Test
    void rootGradleTargetDoesNotOwnNestedProjectCompanions() {
        rewriteRun(buildGradle("""
                dependencies { implementation 'org.springframework:spring-webmvc:6.2.19' }
                project(':child') { dependencies { implementation 'org.springframework:spring-core:5.3.23' } }
                """, source -> source.afterRecipe(after -> {
            String actual = after.printAll();
            assertFalse(actual.contains(FindSpringWebMvc6BuildRisks.ALIGNMENT));
        })));
    }

    @Test
    void buildMarkersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(project("<properties><java.version>8</java.version></properties><dependencies>" +
                                target("6.2.19", "") + "</dependencies>"),
                        project("<properties><!--~~(" + FindSpringWebMvc6BuildRisks.JAVA + ")~~>--><java.version>8</java.version></properties><dependencies>" +
                                target("6.2.19", "") + "</dependencies>")));
    }

    private static int occurrences(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId><version>1</version>" + body + "</project>";
    }

    private static String target(String version, String metadata) {
        return "<dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + metadata + "</dependency>";
    }
}
