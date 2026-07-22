package com.huawei.clouds.openrewrite.springcontextsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeSpringContextSupportTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springcontextsupport.UpgradeSpringContextSupportTo6_2_19";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.springcontextsupport.MigrateSpringContextSupportTo6_2_19";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(UPGRADE));
    }

    @ParameterizedTest(name = "Maven upgrades exact spreadsheet version {0}")
    @ValueSource(strings = {
            "1.0.2", "5.1.2.RELEASE", "5.2.5.RELEASE", "5.3.20", "5.3.23",
            "5.3.25", "5.3.27", "5.3.29", "6.0.11"
    })
    void upgradesEveryVisibleSpreadsheetVersion(String oldVersion) {
        // XML parsing intentionally covers 1.0.2, which is in the source sheet but is not in Maven Central.
        rewriteRun(xml(pom(oldVersion), pom("6.2.19"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRealHibernateExampleDependency() {
        // csi21-sdiapos/hibernate-example-1, fixed commit 484dc82474ff707d22002757a1b2dbfe3f572ac2.
        // https://github.com/csi21-sdiapos/hibernate-example-1/blob/484dc82474ff707d22002757a1b2dbfe3f572ac2/pom.xml
        rewriteRun(pomXml(pom("5.3.23"), pom("6.2.19")));
    }

    @Test
    void upgradesLiteralInDependencyManagementAndLeavesVersionlessUse() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.29</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId></dependency></dependencies>
                </project>
                """
        ),
                xml("""
                        <root><project><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version></dependency></dependencies></project></root>
                        """, source -> source.path("nested/pom.xml")),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>explicit-jar</artifactId><version>1</version><dependencies><dependency>
                          <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version><type>jar</type>
                        </dependency></dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>explicit-jar</artifactId><version>1</version><dependencies><dependency>
                          <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version><type>jar</type>
                        </dependency></dependencies></project>
                        """, source -> source.path("explicit-jar/pom.xml")));
    }

    @Test
    void upgradesProfileDependencyAndPreservesMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version><profiles><profile><id>mail</id><dependencies><dependency>
                  <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.25</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.quartz-scheduler</groupId><artifactId>quartz</artifactId></exclusion></exclusions>
                </dependency></dependencies></profile></profiles></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version><profiles><profile><id>mail</id><dependencies><dependency>
                  <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.quartz-scheduler</groupId><artifactId>quartz</artifactId></exclusion></exclusions>
                </dependency></dependencies></profile></profiles></project>
                """
        ));
    }

    @Test
    void upgradesExclusivelyOwnedRootPropertyAcrossMainAndProfileDependencies() {
        rewriteRun(pomXml(
                propertyPom("5.3.27", "spring.context.support.version"),
                propertyPom("6.2.19", "spring.context.support.version")
        ));
    }

    @Test
    void preservesSharedPropertyUsedByAnotherSpringModule() {
        // Meituan-Dianping/Zebra demonstrates one spring.version shared by many Spring artifacts.
        // https://github.com/Meituan-Dianping/Zebra/blob/33d74b831abe7e8e2d29f8c4e145e46ba17432dc/pom.xml
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><spring.version>5.3.23</spring.version></properties><dependencies>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>${spring.version}</version></dependency>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>${spring.version}</version></dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesPropertyReferencedFromAttributeOrDeclaredTwice() {
        rewriteRun(xml(
                """
                <project marker="${spring.version}"><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>attribute</artifactId><version>1</version>
                  <properties><spring.version>5.3.20</spring.version></properties><dependencies><dependency>
                    <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>${spring.version}</version>
                  </dependency></dependencies>
                </project>
                """, source -> source.path("pom.xml")),
                xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicate</artifactId><version>1</version>
                  <properties><spring.version>5.3.20</spring.version><spring.version>5.3.20</spring.version></properties><dependencies><dependency>
                    <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>${spring.version}</version>
                  </dependency></dependencies>
                </project>
                """, source -> source.path("duplicate/pom.xml"))
        );
    }

    @Test
    void preservesRootPropertyWhenAProfileShadowsIt() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shadow</artifactId><version>1</version>
                  <properties><spring.version>5.3.20</spring.version></properties>
                  <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>${spring.version}</version></dependency></dependencies>
                  <profiles><profile><id>old</id><properties><spring.version>5.1.2.RELEASE</spring.version></properties></profile></profiles>
                </project>
                """, source -> source.path("pom.xml")
        ));
    }

    @Test
    void upgradesOnlyActualProjectDependencyNotPluginOrConfigurationLookalikes() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ownership</artifactId><version>1</version>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>generator</artifactId><version>1</version><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version></dependency></dependencies><configuration><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version></dependency></dependencies></configuration></plugin></plugins></build>
                  <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ownership</artifactId><version>1</version>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>generator</artifactId><version>1</version><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version></dependency></dependencies><configuration><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version></dependency></dependencies></configuration></plugin></plugins></build>
                  <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesClassifierTypeUnlistedDynamicManagedAndFutureVersions() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>safety</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version><classifier>sources</classifier></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version><type>test-jar</type></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>5.3.24</version></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>[5.3,6)</version></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>${missing.version}</version></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>6.2.19</version></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>7.0.0</version></dependency>
                </dependencies></project>
                """, source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesAlibabaAndSimilarCoordinates() {
        // The spreadsheet duplicates the row under com.alibaba.spring, but that group has no 6.2.19 artifact.
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>coordinates</artifactId><version>1</version><dependencies>
                  <dependency><groupId>com.alibaba.spring</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context-support-extra</artifactId><version>5.3.23</version></dependency>
                  <dependency><groupId>example</groupId><artifactId>spring-context-support</artifactId><version>5.3.23</version></dependency>
                </dependencies></project>
                """, source -> source.path("pom.xml")
        ));
    }

    @Test
    void upgradesGroovyStringAndBothMapShapesAndKotlinString() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework:spring-context-support:5.3.20' }",
                        "dependencies { implementation 'org.springframework:spring-context-support:6.2.19' }",
                        source -> source.path("string.gradle")),
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.springframework', name: 'spring-context-support', version: '5.3.25' }",
                        "dependencies { runtimeOnly group: 'org.springframework', name: 'spring-context-support', version: '6.2.19' }",
                        source -> source.path("entries.gradle")),
                buildGradle(
                        "dependencies { testImplementation([group: 'org.springframework', name: 'spring-context-support', version: '5.3.27']) }",
                        "dependencies { testImplementation([group: 'org.springframework', name: 'spring-context-support', version: '6.2.19']) }",
                        source -> source.path("literal.gradle")),
                buildGradleKts(
                        "dependencies { implementation(\"org.springframework:spring-context-support:6.0.11\") }",
                        "dependencies { implementation(\"org.springframework:spring-context-support:6.2.19\") }")
        );
    }

    @Test
    void preservesGradleInterpolationCatalogFourPartAndVariantMaps() {
        rewriteRun(
                buildGradle(
                        """
                        def springVersion = '5.3.23'
                        dependencies {
                          implementation "org.springframework:spring-context-support:${springVersion}"
                          implementation libs.spring.context.support
                          implementation 'org.springframework:spring-context-support:5.3.23:sources'
                          implementation group: 'org.springframework', name: 'spring-context-support', version: '5.3.23', classifier: 'sources'
                          implementation([group: 'org.springframework', name: 'spring-context-support', version: '5.3.23', ext: 'zip'])
                        }
                        """),
                buildGradleKts(
                        """
                        val springVersion = "5.3.23"
                        dependencies {
                            implementation("org.springframework:spring-context-support:$springVersion")
                            implementation(libs.spring.context.support)
                        }
                        """)
        );
    }

    @Test
    void ignoresNestedDependenciesAndBuildscript() {
        rewriteRun(buildGradle(
                """
                buildscript { dependencies { implementation 'org.springframework:spring-context-support:5.3.23' } }
                dependencies { generated { implementation 'org.springframework:spring-context-support:5.3.23' } }
                publishing { dependencies { implementation 'org.springframework:spring-context-support:5.3.23' } }
                fake { dependencies { implementation 'org.springframework:spring-context-support:5.3.23' } }
                """
        ));
    }

    @Test
    void skipsGeneratedAndInstalledBuildFiles() {
        rewriteRun(
                xml(pom("5.3.23"), source -> source.path("target/generated-sources/pom.xml")),
                buildGradle("dependencies { implementation 'org.springframework:spring-context-support:5.3.23' }",
                        source -> source.path("install/build.gradle"))
        );
    }

    @Test
    void dependencyUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("5.3.29"), pom("6.2.19")));
    }

    @Test
    void discoversAllPublicRecipesAndRecommendedRecipeComposesUpgrade() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        String[] names = {
                UPGRADE,
                "com.huawei.clouds.openrewrite.springcontextsupport.MigrateDeterministicSpring6JakartaNamespaces",
                "com.huawei.clouds.openrewrite.springcontextsupport.FindSpringContextSupport6BuildMigrationRisks",
                "com.huawei.clouds.openrewrite.springcontextsupport.FindSpringContextSupport6SourceAndConfigRisks",
                MIGRATE
        };
        for (String name : names) assertEquals(name, environment.activateRecipes(name).getName());
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertTrue(migrate.getRecipeList().size() >= 4);
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency>
                 <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static String propertyPom(String version, String property) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                 <properties><%1$s>%2$s</%1$s></properties>
                 <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>${%1$s}</version></dependency></dependencies>
                 <profiles><profile><id>mail</id><dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>${%1$s}</version></dependency></dependencies></profile></profiles>
               </project>
               """.formatted(property, version);
    }
}
