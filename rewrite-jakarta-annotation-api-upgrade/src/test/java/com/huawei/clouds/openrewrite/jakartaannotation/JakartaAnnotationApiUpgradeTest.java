package com.huawei.clouds.openrewrite.jakartaannotation;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class JakartaAnnotationApiUpgradeTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.jakartaannotation.UpgradeJakartaAnnotationApiTo3_0_0";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.jakartaannotation.MigrateJakartaAnnotationApiTo3_0_0";
    private static final String MANAGED_BEAN_AUDIT_RECIPE =
            "com.huawei.clouds.openrewrite.jakartaannotation.FindRemovedManagedBeanUsages";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE))
                .parser(annotationParser());
    }

    @Test
    void upgradesDropwizardMetricsManagedDependency() {
        // Reduced from dropwizard/metrics at 3d704a3b80b93815ad44a7b29601a65eaeb5c3bf:
        // https://github.com/dropwizard/metrics/blob/3d704a3b80b93815ad44a7b29601a65eaeb5c3bf/metrics-jersey2/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>io.dropwizard.metrics</groupId><artifactId>metrics-jersey2</artifactId><version>1</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>jakarta.annotation</groupId>
                        <artifactId>jakarta.annotation-api</artifactId>
                        <version>1.3.5</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>io.dropwizard.metrics</groupId><artifactId>metrics-jersey2</artifactId><version>1</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>jakarta.annotation</groupId>
                        <artifactId>jakarta.annotation-api</artifactId>
                        <version>3.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesDirectMavenDependencyAndPreservesMetadata() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>annotations-app</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId><version>1.3.5</version>
                      <scope>provided</scope><optional>true</optional>
                    </dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>annotations-app</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId><version>3.0.0</version>
                      <scope>provided</scope><optional>true</optional>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesMavenVersionProperty() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property-app</artifactId><version>1</version>
                  <properties><jakarta-annotations.version>1.3.5</jakarta-annotations.version></properties>
                  <dependencies><dependency><groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId><version>${jakarta-annotations.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property-app</artifactId><version>1</version>
                  <properties><jakarta-annotations.version>3.0.0</jakarta-annotations.version></properties>
                  <dependencies><dependency><groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId><version>${jakarta-annotations.version}</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesOpenApiGeneratorStyleGradleStringDependency() {
        // Reduced from OpenAPITools/openapi-generator at 82cb1ad5ab515e81cf54a598b91cf5ff61b718c1:
        // https://github.com/OpenAPITools/openapi-generator/blob/82cb1ad5ab515e81cf54a598b91cf5ff61b718c1/samples/server/petstore/jaxrs-resteasy/eap/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    compileOnly 'jakarta.annotation:jakarta.annotation-api:1.3.5'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    compileOnly 'jakarta.annotation:jakarta.annotation-api:3.0.0'
                }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    api group: 'jakarta.annotation', name: 'jakarta.annotation-api', version: '1.3.5'
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    api group: 'jakarta.annotation', name: 'jakarta.annotation-api', version: '3.0.0'
                }
                """
        ));
    }

    @Test
    void upgradesGradleVersionVariable() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def annotationsVersion = '1.3.5'
                dependencies {
                    compileOnly "jakarta.annotation:jakarta.annotation-api:${annotationsVersion}"
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def annotationsVersion = '3.0.0'
                dependencies {
                    compileOnly "jakarta.annotation:jakarta.annotation-api:${annotationsVersion}"
                }
                """
        ));
    }

    @Test
    void leavesKotlinDslWithoutGradleSemanticModelUntouched() {
        // UpgradeDependencyVersion uses GradleProject markers for Kotlin DSL. A parser-only
        // RewriteTest has no Tooling API model and must fail safe instead of editing raw text.
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    compileOnly("jakarta.annotation:jakarta.annotation-api:1.3.5")
                }
                """
        ));
    }

    @Test
    void leavesTargetVersionUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>target</artifactId><version>1</version><dependencies><dependency><groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId><version>3.0.0</version></dependency></dependencies></project>
                """));
    }

    @Test
    void leavesLegacyAndSimilarCoordinatesUntouched() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>similar</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>javax.annotation</groupId><artifactId>javax.annotation-api</artifactId><version>1.3.2</version></dependency>
                    <dependency><groupId>jakarta.inject</groupId><artifactId>jakarta.inject-api</artifactId><version>1.0.5</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void dependencyOnlyRecipeDoesNotModifyJavaSource() {
        rewriteRun(java(
                """
                import javax.annotation.PostConstruct;

                class LegacyLifecycle {
                    @PostConstruct void start() {}
                }
                """
        ));
    }

    @Test
    void migratesApacheOzoneLifecycleAnnotations() {
        // Reduced from apache/ozone at 68c4231f512fb7459ece38dae596443320f6a039:
        // https://github.com/apache/ozone/blob/68c4231f512fb7459ece38dae596443320f6a039/hadoop-ozone/s3gateway/src/main/java/org/apache/hadoop/ozone/s3/OzoneClientCache.java
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.annotation.PostConstruct;
                        import javax.annotation.PreDestroy;

                        class OzoneClientCache {
                            @PostConstruct
                            public void initialize() {}

                            @PreDestroy
                            public void cleanup() {}
                        }
                        """,
                        """
                        import jakarta.annotation.PostConstruct;
                        import jakarta.annotation.PreDestroy;

                        class OzoneClientCache {
                            @PostConstruct
                            public void initialize() {}

                            @PreDestroy
                            public void cleanup() {}
                        }
                        """
                )
        );
    }

    @Test
    void migratesResourceAndItsNestedAuthenticationType() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.annotation.Resource;
                        import javax.annotation.Resource.AuthenticationType;

                        class ResourceConsumer {
                            @Resource(name = "jdbc/orders", authenticationType = AuthenticationType.APPLICATION, shareable = false)
                            Object dataSource;
                        }
                        """,
                        """
                        import jakarta.annotation.Resource;
                        import jakarta.annotation.Resource.AuthenticationType;

                        class ResourceConsumer {
                            @Resource(name = "jdbc/orders", authenticationType = AuthenticationType.APPLICATION, shareable = false)
                            Object dataSource;
                        }
                        """
                )
        );
    }

    @Test
    void migratesGeneratedPriorityAndResources() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.annotation.Generated;
                        import javax.annotation.Priority;
                        import javax.annotation.Resource;
                        import javax.annotation.Resources;

                        @Generated(value = "example.Generator", comments = "kept")
                        @Priority(100)
                        @Resources({@Resource(name = "one"), @Resource(name = "two")})
                        class GeneratedComponent {}
                        """,
                        """
                        import jakarta.annotation.Generated;
                        import jakarta.annotation.Priority;
                        import jakarta.annotation.Resource;
                        import jakarta.annotation.Resources;

                        @Generated(value = "example.Generator", comments = "kept")
                        @Priority(100)
                        @Resources({@Resource(name = "one"), @Resource(name = "two")})
                        class GeneratedComponent {}
                        """
                )
        );
    }

    @Test
    void migratesAllSecurityAnnotations() {
        // RolesAllowed shape reduced from voodoodyne/subetha at f3130ad16360724a12d4e33bccaaaa07c4b246d6:
        // https://github.com/voodoodyne/subetha/blob/f3130ad16360724a12d4e33bccaaaa07c4b246d6/src/main/java/org/subethamail/core/admin/EegorBean.java
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.annotation.security.DeclareRoles;
                        import javax.annotation.security.DenyAll;
                        import javax.annotation.security.PermitAll;
                        import javax.annotation.security.RolesAllowed;
                        import javax.annotation.security.RunAs;

                        @DeclareRoles({"siteAdmin", "auditor"})
                        @RunAs("siteAdmin")
                        class EegorBean {
                            @RolesAllowed("siteAdmin") void enableTestMode() {}
                            @PermitAll void status() {}
                            @DenyAll void reset() {}
                        }
                        """,
                        """
                        import jakarta.annotation.security.*;

                        @DeclareRoles({"siteAdmin", "auditor"})
                        @RunAs("siteAdmin")
                        class EegorBean {
                            @RolesAllowed("siteAdmin") void enableTestMode() {}
                            @PermitAll void status() {}
                            @DenyAll void reset() {}
                        }
                        """
                )
        );
    }

    @Test
    void migratesJavaEeSamplesDataSourceAnnotations() {
        // Reduced from javaee-samples/javaee7-samples at 4a67b232d71bc2b3b6d418e955218fa71741b943:
        // https://github.com/javaee-samples/javaee7-samples/blob/4a67b232d71bc2b3b6d418e955218fa71741b943/jpa/datasourcedefinition/src/main/java/org/javaee7/jpa/datasourcedefinition/DataSourceDefinitionHolder.java
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.annotation.sql.DataSourceDefinition;
                        import javax.annotation.sql.DataSourceDefinitions;

                        @DataSourceDefinitions({
                            @DataSourceDefinition(name = "java:global/Orders", className = "org.h2.jdbcx.JdbcDataSource", url = "jdbc:h2:mem:orders"),
                            @DataSourceDefinition(name = "java:global/Audit", className = "org.h2.jdbcx.JdbcDataSource", url = "jdbc:h2:mem:audit")
                        })
                        class DataSourceDefinitionHolder {}
                        """,
                        """
                        import jakarta.annotation.sql.DataSourceDefinition;
                        import jakarta.annotation.sql.DataSourceDefinitions;

                        @DataSourceDefinitions({
                            @DataSourceDefinition(name = "java:global/Orders", className = "org.h2.jdbcx.JdbcDataSource", url = "jdbc:h2:mem:orders"),
                            @DataSourceDefinition(name = "java:global/Audit", className = "org.h2.jdbcx.JdbcDataSource", url = "jdbc:h2:mem:audit")
                        })
                        class DataSourceDefinitionHolder {}
                        """
                )
        );
    }

    @Test
    void migratesFullyQualifiedAnnotationReferences() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        @javax.annotation.Generated("generator")
                        class FullyQualifiedComponent {
                            @javax.annotation.PostConstruct void initialize() {}
                        }
                        """,
                        """
                        import jakarta.annotation.Generated;
                        import jakarta.annotation.PostConstruct;

                        @Generated("generator")
                        class FullyQualifiedComponent {
                            @PostConstruct void initialize() {}
                        }
                        """
                )
        );
    }

    @Test
    void migratesWildcardImportedTypesWithoutRenamingTheWholePackage() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.annotation.*;

                        class WildcardLifecycle {
                            @PostConstruct void initialize() {}
                            @PreDestroy void destroy() {}
                        }
                        """,
                        """
                        import jakarta.annotation.PostConstruct;
                        import jakarta.annotation.PreDestroy;

                        class WildcardLifecycle {
                            @PostConstruct void initialize() {}
                            @PreDestroy void destroy() {}
                        }
                        """
                )
        );
    }

    @Test
    void migratesPackageAnnotation() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        @javax.annotation.Generated("schema-generator")
                        package example.generated;
                        """,
                        """
                        @Generated("schema-generator")
                        package example.generated;

                        import jakarta.annotation.Generated;
                        """,
                        spec -> spec.path("src/main/java/example/generated/package-info.java")
                )
        );
    }

    @Test
    void preservesJavaSeAnnotationProcessingPackage() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.annotation.processing.AbstractProcessor;
                        import javax.annotation.processing.ProcessingEnvironment;
                        import javax.annotation.processing.RoundEnvironment;
                        import javax.lang.model.SourceVersion;
                        import javax.lang.model.element.TypeElement;
                        import java.util.Set;

                        class ExampleProcessor extends AbstractProcessor {
                            @Override public synchronized void init(ProcessingEnvironment environment) { super.init(environment); }
                            @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) { return false; }
                            @Override public Set<String> getSupportedAnnotationTypes() { return Set.of("example.*"); }
                            @Override public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }
                        }
                        """
                )
        );
    }

    @Test
    void doesNotInventRemovedManagedBeanTypeButMigratesOtherAnnotations() {
        // Reduced from OpenLiberty/open-liberty at 673c04f54357bfd6d07be45a862884c45f0b6c54:
        // https://github.com/OpenLiberty/open-liberty/blob/673c04f54357bfd6d07be45a862884c45f0b6c54/dev/com.ibm.ws.cdi.jee_fat/fat/src/com/ibm/ws/cdi/jee/ejbWithJsp/ejb/MyManagedBean1.java
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.annotation.ManagedBean;
                        import javax.annotation.Resource;

                        @ManagedBean("MyManagedBean1")
                        class MyManagedBean1 {
                            @Resource(lookup = "globalGreeting") String greeting;
                        }
                        """,
                        """
                        import jakarta.annotation.Resource;

                        import javax.annotation.ManagedBean;

                        @ManagedBean("MyManagedBean1")
                        class MyManagedBean1 {
                            @Resource(lookup = "globalGreeting") String greeting;
                        }
                        """
                )
        );
    }

    @Test
    void leavesStringsCommentsAndBusinessTypesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        class AnnotationNames {
                            // javax.annotation.PostConstruct is configuration, not a Java type use.
                            String lifecycle = "javax.annotation.PostConstruct";
                            String processor = "javax.annotation.processing.Processor";

                            @interface Resource {}
                            @Resource class BusinessResource {}
                        }
                        """
                )
        );
    }

    @Test
    void leavesAlreadyMigratedJakartaSourceUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package jakarta.annotation;
                                public @interface PostConstruct {}
                                """,
                                """
                                package jakarta.annotation.security;
                                public @interface RolesAllowed { String[] value(); }
                                """
                        )),
                java(
                        """
                        import jakarta.annotation.PostConstruct;
                        import jakarta.annotation.security.RolesAllowed;

                        class JakartaComponent {
                            @PostConstruct void initialize() {}
                            @RolesAllowed("admin") void secured() {}
                        }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeUpdatesBuildAndSourceTogether() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>full-migration</artifactId><version>1</version><dependencies><dependency><groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId><version>1.3.5</version><scope>provided</scope></dependency></dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>full-migration</artifactId><version>1</version><dependencies><dependency><groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId><version>3.0.0</version><scope>provided</scope></dependency></dependencies></project>
                        """
                ),
                java(
                        """
                        import javax.annotation.PostConstruct;
                        import javax.annotation.security.RolesAllowed;

                        class ApplicationService {
                            @PostConstruct void initialize() {}
                            @RolesAllowed("operator") void run() {}
                        }
                        """,
                        """
                        import jakarta.annotation.PostConstruct;
                        import jakarta.annotation.security.RolesAllowed;

                        class ApplicationService {
                            @PostConstruct void initialize() {}
                            @RolesAllowed("operator") void run() {}
                        }
                        """
                )
        );
    }

    @Test
    void auditMarksLegacyManagedBeanForManualMigration() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MANAGED_BEAN_AUDIT_RECIPE)),
                java(
                        """
                        import javax.annotation.ManagedBean;

                        @ManagedBean("legacy")
                        class LegacyManagedBean {}
                        """,
                        """
                        import javax.annotation.ManagedBean;

                        @/*~~>*/ManagedBean("legacy")
                        class LegacyManagedBean {}
                        """
                )
        );
    }

    @Test
    void auditAlsoMarksPartiallyMigratedJakartaManagedBean() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MANAGED_BEAN_AUDIT_RECIPE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package jakarta.annotation;
                                public @interface ManagedBean { String value() default ""; }
                                """
                        )),
                java(
                        """
                        import jakarta.annotation.ManagedBean;

                        @ManagedBean("legacy")
                        class JakartaManagedBean {}
                        """,
                        """
                        import jakarta.annotation.ManagedBean;

                        @/*~~>*/ManagedBean("legacy")
                        class JakartaManagedBean {}
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesEveryRecipe() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe migration = environment.activateRecipes(MIGRATION_RECIPE);
        Recipe audit = environment.activateRecipes(MANAGED_BEAN_AUDIT_RECIPE);

        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> DEPENDENCY_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATION_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MANAGED_BEAN_AUDIT_RECIPE.equals(recipe.getName())));
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(migration.validate().isValid(), () -> migration.validate().failures().toString());
        assertTrue(audit.validate().isValid(), () -> audit.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jakartaannotation")
                .scanYamlResources()
                .build();
    }

    private static JavaParser.Builder<?, ?> annotationParser() {
        return JavaParser.fromJavaVersion().classpath("jakarta.annotation-api");
    }
}
