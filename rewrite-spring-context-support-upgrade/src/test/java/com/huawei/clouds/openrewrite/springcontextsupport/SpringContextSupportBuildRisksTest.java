package com.huawei.clouds.openrewrite.springcontextsupport;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringContextSupportBuildRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springcontextsupport.FindSpringContextSupport6BuildMigrationRisks";
    private static final String JAVA =
            "Spring Framework 6.2 requires Java 17 or newer; align compiler, toolchain, CI, container and runtime JDKs";
    private static final String LEGACY =
            "Legacy Context Support integration dependency detected; migrate Ehcache 2 or javax Mail/Activation to a Jakarta-compatible provider and retest the integration";
    private static final String OPTIONAL =
            "Spring Context Support optional integration detected; align its Spring 6.2-compatible version and test provider-specific cache, mail, Quartz or FreeMarker behavior";
    private static final String ALIGNMENT =
            "Spring Framework modules must use one 6.2.19 line; align the owning BOM/property instead of running mixed Spring versions";
    private static final String MANAGED =
            "spring-context-support is versionless or externally managed; resolve and migrate the owning Spring BOM, parent, platform or catalog to 6.2.19";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE));
    }

    @Test
    void marksMavenJavaBaselineLegacyOptionalAndMixedSpringModules() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>risks</artifactId><version>1</version>
                  <properties><java.version>1.8</java.version><spring.version>5.3.29</spring.version></properties>
                  <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><release>11</release></configuration></plugin></plugins></build>
                  <dependencies>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version></dependency>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>${spring.version}</version></dependency>
                    <dependency><groupId>net.sf.ehcache</groupId><artifactId>ehcache</artifactId><version>2.10.9.2</version></dependency>
                    <dependency><groupId>javax.mail</groupId><artifactId>mail</artifactId><version>1.4.7</version></dependency>
                    <dependency><groupId>org.ehcache</groupId><artifactId>ehcache</artifactId><version>3.10.8</version></dependency>
                    <dependency><groupId>org.freemarker</groupId><artifactId>freemarker</artifactId><version>2.3.33</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>risks</artifactId><version>1</version>
                  <properties><!--~~(%s)~~>--><java.version>1.8</java.version><spring.version>5.3.29</spring.version></properties>
                  <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><!--~~(%s)~~>--><release>11</release></configuration></plugin></plugins></build>
                  <dependencies>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version></dependency>
                    <!--~~(%s)~~>--><dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>${spring.version}</version></dependency>
                    <!--~~(%s)~~>--><dependency><groupId>net.sf.ehcache</groupId><artifactId>ehcache</artifactId><version>2.10.9.2</version></dependency>
                    <!--~~(%s)~~>--><dependency><groupId>javax.mail</groupId><artifactId>mail</artifactId><version>1.4.7</version></dependency>
                    <!--~~(%s)~~>--><dependency><groupId>org.ehcache</groupId><artifactId>ehcache</artifactId><version>3.10.8</version></dependency>
                    <!--~~(%s)~~>--><dependency><groupId>org.freemarker</groupId><artifactId>freemarker</artifactId><version>2.3.33</version></dependency>
                  </dependencies>
                </project>
                """.formatted(JAVA, JAVA, ALIGNMENT, LEGACY, LEGACY, OPTIONAL, OPTIONAL)
        ));
    }

    @Test
    void marksVersionlessUnresolvedAndDynamicTargetOwners() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owners</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>${missing.version}</version></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>[5.3,6)</version></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>owners</artifactId><version>1</version><dependencies>
                  <!--~~(%s)~~>--><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId></dependency>
                  <!--~~(%s)~~>--><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>${missing.version}</version></dependency>
                  <!--~~(%s)~~>--><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>[5.3,6)</version></dependency>
                </dependencies></project>
                """.formatted(MANAGED, MANAGED, MANAGED), source -> source.path("pom.xml")
        ),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>local-management</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version></dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId></dependency></dependencies>
                        </project>
                        """, source -> source.path("local-management/pom.xml")));
    }

    @Test
    void resolvedRootPropertyIsNotReportedAsExternalOwner() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                  <properties><spring.context.support.version>6.2.19</spring.context.support.version></properties><dependencies><dependency>
                    <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>${spring.context.support.version}</version>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void doesNothingWithoutStandardTargetDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unrelated</artifactId><version>1</version>
                  <properties><java.version>8</java.version></properties><dependencies>
                    <dependency><groupId>net.sf.ehcache</groupId><artifactId>ehcache</artifactId><version>2.10.9.2</version></dependency>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>5.3.29</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void marksClassifierAndCustomTypeWithoutGatingUnrelatedBuildMarkers() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>variant</artifactId><version>1</version>
                  <properties><java.version>8</java.version></properties><dependencies>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.29</version><classifier>sources</classifier></dependency>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.29</version><type>test-jar</type></dependency>
                    <dependency><groupId>net.sf.ehcache</groupId><artifactId>ehcache</artifactId><version>2.10.9.2</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>variant</artifactId><version>1</version>
                  <properties><java.version>8</java.version></properties><dependencies>
                    <!--~~(spring-context-support classifier/type/ext/variant declaration is not the standard runtime artifact; verify its classpath and migrate the exact variant manually)~~>--><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.29</version><classifier>sources</classifier></dependency>
                    <!--~~(spring-context-support classifier/type/ext/variant declaration is not the standard runtime artifact; verify its classpath and migrate the exact variant manually)~~>--><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.29</version><type>test-jar</type></dependency>
                    <dependency><groupId>net.sf.ehcache</groupId><artifactId>ehcache</artifactId><version>2.10.9.2</version></dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml")
        ),
                buildGradle(
                        """
                        dependencies {
                          implementation 'org.springframework:spring-context-support:5.3.29:sources'
                          runtimeOnly group: 'org.springframework', name: 'spring-context-support', version: '5.3.29', ext: 'zip'
                        }
                        """,
                        """
                        dependencies {
                          implementation /*~~(spring-context-support classifier/type/ext/variant declaration is not the standard runtime artifact; verify its classpath and migrate the exact variant manually)~~>*/'org.springframework:spring-context-support:5.3.29:sources'
                          /*~~(spring-context-support classifier/type/ext/variant declaration is not the standard runtime artifact; verify its classpath and migrate the exact variant manually)~~>*/runtimeOnly group: 'org.springframework', name: 'spring-context-support', version: '5.3.29', ext: 'zip'
                        }
                        """));
    }

    @Test
    void marksGroovyJavaLevelsIntegrationFamiliesAndAlignment() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = '11'
                java { toolchain { languageVersion = JavaLanguageVersion.of(8) } }
                dependencies {
                  implementation 'org.springframework:spring-context-support:6.2.19'
                  implementation 'org.springframework:spring-context:5.3.29'
                  runtimeOnly 'com.sun.mail:javax.mail:1.6.2'
                  runtimeOnly 'jakarta.mail:jakarta.mail-api:2.1.3'
                  runtimeOnly group: 'org.quartz-scheduler', name: 'quartz', version: '2.3.2'
                }
                """,
                """
                plugins { id 'java' }
                sourceCompatibility = /*~~(%s)~~>*/JavaVersion.VERSION_1_8
                targetCompatibility = /*~~(%s)~~>*/'11'
                java { toolchain { languageVersion = /*~~(%s)~~>*/JavaLanguageVersion.of(8) } }
                dependencies {
                  implementation 'org.springframework:spring-context-support:6.2.19'
                  implementation /*~~(%s)~~>*/'org.springframework:spring-context:5.3.29'
                  runtimeOnly /*~~(%s)~~>*/'com.sun.mail:javax.mail:1.6.2'
                  runtimeOnly /*~~(%s)~~>*/'jakarta.mail:jakarta.mail-api:2.1.3'
                  /*~~(%s)~~>*/runtimeOnly group: 'org.quartz-scheduler', name: 'quartz', version: '2.3.2'
                }
                """.formatted(JAVA, JAVA, JAVA, ALIGNMENT, LEGACY, OPTIONAL, OPTIONAL)
        ));
    }

    @Test
    void marksKotlinJavaBaselineWhenTargetIsPresent() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                java {
                    sourceCompatibility = JavaVersion.VERSION_11
                    toolchain { languageVersion = JavaLanguageVersion.of(8) }
                }
                dependencies { implementation("org.springframework:spring-context-support:6.2.19") }
                """,
                """
                plugins { java }
                java {
                    sourceCompatibility = /*~~(%s)~~>*/JavaVersion.VERSION_11
                    toolchain { languageVersion = /*~~(%s)~~>*/JavaLanguageVersion.of(8) }
                }
                dependencies { implementation("org.springframework:spring-context-support:6.2.19") }
                """.formatted(JAVA, JAVA)
        ));
    }

    @Test
    void targetInsideProfileOrDependencyManagementStillGatesOwningPom() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <properties><maven.compiler.release>11</maven.compiler.release></properties>
                  <profiles><profile><id>spring</id><dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version>
                  </dependency></dependencies></dependencyManagement></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <properties><!--~~(%s)~~>--><maven.compiler.release>11</maven.compiler.release></properties>
                  <profiles><profile><id>spring</id><dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version>
                  </dependency></dependencies></dependencyManagement></profile></profiles>
                </project>
                """.formatted(JAVA)
        ));
    }

    @Test
    void ignoresNestedAndBuildscriptGradleLookalikes() {
        rewriteRun(buildGradle(
                """
                sourceCompatibility = JavaVersion.VERSION_1_8
                buildscript { dependencies { implementation 'org.springframework:spring-context-support:6.2.19' } }
                dependencies { generated { implementation 'org.springframework:spring-context-support:6.2.19' } }
                fake { dependencies { implementation 'org.springframework:spring-context-support:6.2.19' } }
                dependencies { runtimeOnly 'example:spring-context-support:6.2.19' }
                """
        ));
    }

    @Test
    void marksOnlyExactOptionalCoordinates() {
        rewriteRun(buildGradle(
                """
                dependencies {
                  implementation 'org.springframework:spring-context-support:6.2.19'
                  runtimeOnly 'org.quartz-scheduler:quartz:2.3.2'
                  runtimeOnly 'example:quartz:2.3.2'
                  runtimeOnly 'org.freemarker:freemarker-extra:2.3.33'
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(OPTIONAL));
                    assertFalse(printed.contains("*/'example:quartz"));
                    assertFalse(printed.contains("*/'org.freemarker:freemarker-extra"));
                })
        ));
    }

    @Test
    void skipsGeneratedAndInstalledBuildFiles() {
        rewriteRun(
                xml("""
                    <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>generated</artifactId><version>1</version>
                      <properties><java.version>8</java.version></properties><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version></dependency></dependencies>
                    </project>
                    """, source -> source.path("target/generated/pom.xml")),
                buildGradle(
                        "sourceCompatibility = '8'\ndependencies { implementation 'org.springframework:spring-context-support:6.2.19' }",
                        source -> source.path("installed/build.gradle"))
        );
    }

    @Test
    void buildMarkersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>idempotent</artifactId><version>1</version>
                  <properties><java.version>11</java.version></properties><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>idempotent</artifactId><version>1</version>
                  <properties><!--~~(%s)~~>--><java.version>11</java.version></properties><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version></dependency></dependencies>
                </project>
                """.formatted(JAVA)
        ));
    }
}
