package com.huawei.clouds.openrewrite.springcloudeureka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeEurekaClientTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springcloudeureka.UpgradeEurekaClientTo4_2_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE));
    }

    @ParameterizedTest(name = "upgrades spreadsheet Maven version {0}")
    @ValueSource(strings = {"2.1.5.RELEASE", "3.1.2", "3.1.5", "3.1.7"})
    void upgradesEverySpreadsheetMavenVersion(String oldVersion) {
        rewriteRun(pomXml(eurekaPom(oldVersion), eurekaPom("4.2.0")));
    }

    @ParameterizedTest(name = "upgrades spreadsheet Gradle version {0}")
    @ValueSource(strings = {"2.1.5.RELEASE", "3.1.2", "3.1.5", "3.1.7"})
    void upgradesEverySpreadsheetGradleVersion(String oldVersion) {
        rewriteRun(buildGradle(
                gradleDependency("implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client:%s'".formatted(oldVersion)),
                gradleDependency("implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client:4.2.0'")
        ));
    }

    @Test
    void upgradesApacheLinkisPropertyManagedDependency() {
        // Reduced from apache/linkis at a fixed commit:
        // https://github.com/apache/linkis/blob/974438c957554ad025e4ac4af0f30bac91574c29/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.apache.linkis</groupId><artifactId>linkis</artifactId><version>1</version>
                  <properties>
                    <java.version>1.8</java.version><spring.boot.version>2.7.18</spring.boot.version>
                    <spring-netflix.version>3.1.7</spring-netflix.version><spring-cloud.version>2021.0.8</spring-cloud.version>
                  </properties>
                  <dependencyManagement><dependencies>
                    <dependency>
                      <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
                      <version>${spring-netflix.version}</version>
                      <exclusions>
                        <exclusion><groupId>com.sun.jersey</groupId><artifactId>*</artifactId></exclusion>
                        <exclusion><groupId>com.sun.jersey.contribs</groupId><artifactId>jersey-apache-client4</artifactId></exclusion>
                        <exclusion><groupId>io.github.x-stream</groupId><artifactId>mxparser</artifactId></exclusion>
                      </exclusions>
                    </dependency>
                    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>${spring-cloud.version}</version><type>pom</type><scope>import</scope></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.apache.linkis</groupId><artifactId>linkis</artifactId><version>1</version>
                  <properties>
                    <java.version>1.8</java.version><spring.boot.version>2.7.18</spring.boot.version>
                    <spring-netflix.version>4.2.0</spring-netflix.version><spring-cloud.version>2021.0.8</spring-cloud.version>
                  </properties>
                  <dependencyManagement><dependencies>
                    <dependency>
                      <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
                      <version>${spring-netflix.version}</version>
                      <exclusions>
                        <exclusion><groupId>com.sun.jersey</groupId><artifactId>*</artifactId></exclusion>
                        <exclusion><groupId>com.sun.jersey.contribs</groupId><artifactId>jersey-apache-client4</artifactId></exclusion>
                        <exclusion><groupId>io.github.x-stream</groupId><artifactId>mxparser</artifactId></exclusion>
                      </exclusions>
                    </dependency>
                    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>${spring-cloud.version}</version><type>pom</type><scope>import</scope></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesVeryLinkedInExplicitDependency() {
        // Reduced from Sohob/VeryLinkedIN at a fixed commit:
        // https://github.com/Sohob/VeryLinkedIN/blob/bb02ce79fb5608709ee0ce3d69862949c46775b7/account/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>2.6.7</version></parent>
                  <groupId>com.verylinkedin</groupId><artifactId>account</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>3.1.2</version></dependency>
                    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>2021.0.2</version><type>pom</type><scope>import</scope></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>2.6.7</version></parent>
                  <groupId>com.verylinkedin</groupId><artifactId>account</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>4.2.0</version></dependency>
                    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>2021.0.2</version><type>pom</type><scope>import</scope></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesAviationAppAndPreservesJavaxExclusion() {
        // Reduced from emirtotic/aviation-app at a fixed commit:
        // https://github.com/emirtotic/aviation-app/blob/578d5f1a2b9c743b7ead9020006194c1facf9965/flight-service/pom.xml
        rewriteRun(pomXml(
                explicitWithExclusion("3.1.5"),
                explicitWithExclusion("4.2.0")
        ));
    }

    @Test
    void upgradesI113SecurityAndLeavesActuatorUntouched() {
        // Reduced from dtrcreative/i113_security at a fixed commit:
        // https://github.com/dtrcreative/i113_security/blob/d5554daa29d2733f1195dfe8f8ae2497cc21b3b9/pom.xml
        rewriteRun(pomXml(
                pomWithActuator("3.1.5"),
                pomWithActuator("4.2.0")
        ));
    }

    @Test
    void leavesTyCodingGreenwichBomManagedDependencyVersionless() {
        // Reduced from TyCoding/cloud-template at a fixed commit. 2.1.5.RELEASE is the
        // Boot parent here; the Eureka starter is owned by the Greenwich.SR1 Cloud BOM.
        // https://github.com/TyCoding/cloud-template/blob/737f98a7383db9f498400ad5e57dd9b3a819dcac/sct-api/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>2.1.5.RELEASE</version></parent>
                  <groupId>cn.tycoding</groupId><artifactId>sct-api</artifactId><version>1</version>
                  <properties><java.version>1.8</java.version><spring-cloud.version>Greenwich.SR1</spring-cloud.version></properties>
                  <dependencyManagement><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>${spring-cloud.version}</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesApiGatewayBomManagedDependencyVersionless() {
        // Reduced from kalayciburak/microservices at a fixed commit:
        // https://github.com/kalayciburak/microservices/blob/ac114f28be0ff22e2733eb0df515bb6191050e8a/api-gateway/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>2.7.6</version></parent>
                  <groupId>com.microservices</groupId><artifactId>api-gateway</artifactId><version>1</version>
                  <properties><java.version>17</java.version><spring-cloud.version>2021.0.5</spring-cloud.version></properties>
                  <dependencyManagement><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>${spring-cloud.version}</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId></dependency>
                    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-sleuth</artifactId><version>3.1.5</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesRealGradleBomManagedDependencyVersionless() {
        // Reduced from jkazama/sample-boot-micro at a fixed commit:
        // https://github.com/jkazama/sample-boot-micro/blob/0e8fb571af8d0ab32d22cfba33ab2eab48836381/build.gradle
        rewriteRun(buildGradle(
                """
                plugins {
                    id 'org.springframework.boot' version '2.2.5.RELEASE'
                    id 'io.spring.dependency-management' version '1.0.9.RELEASE'
                    id 'java'
                }
                repositories { mavenCentral() }
                ext.spring_cloud_version = 'Hoxton.SR2'
                dependencyManagement {
                    imports { mavenBom "org.springframework.cloud:spring-cloud-dependencies:${spring_cloud_version}" }
                }
                dependencies {
                    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
                    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
                }
                """
        ));
    }

    @Test
    void leavesTargetVersionFromRealRepositoryUntouched() {
        // Reduced from Hemil-Fichadia/FakeStoreProductService at a fixed commit:
        // https://github.com/Hemil-Fichadia/FakeStoreProductService/blob/e8fb64cf5675d038be6dbddaf598d61dc5de627f/pom.xml
        rewriteRun(pomXml(eurekaPom("4.2.0")));
    }

    @Test
    void upgradesMavenVersionProperty() {
        rewriteRun(pomXml(
                propertyPom("3.1.7"),
                propertyPom("4.2.0")
        ));
    }

    @Test
    void upgradesMavenDependencyManagementVersion() {
        rewriteRun(pomXml(
                dependencyManagementPom("3.1.2"),
                dependencyManagementPom("4.2.0")
        ));
    }

    @Test
    void upgradesDependencyInsideMavenProfile() {
        rewriteRun(pomXml(
                profilePom("2.1.5.RELEASE"),
                profilePom("4.2.0")
        ));
    }

    @Test
    void upgradesPropertyUsedInsideMavenProfileDependencyManagement() {
        rewriteRun(pomXml(
                profilePropertyPom("3.1.5"),
                profilePropertyPom("4.2.0")
        ));
    }

    @Test
    void upgradesMultipleExplicitMavenOccurrences() {
        rewriteRun(pomXml(
                multiplePom("3.1.2", "3.1.7"),
                multiplePom("4.2.0", "4.2.0")
        ));
    }

    @Test
    void preservesMavenScopeOptionalTypeClassifierAndExclusions() {
        rewriteRun(pomXml(
                detailedPom("3.1.7"),
                detailedPom("4.2.0")
        ));
    }

    @Test
    void upgradesGradleDoubleQuotedDependency() {
        rewriteRun(buildGradle(
                gradleDependency("runtimeOnly(\"org.springframework.cloud:spring-cloud-starter-netflix-eureka-client:3.1.5\")"),
                gradleDependency("runtimeOnly(\"org.springframework.cloud:spring-cloud-starter-netflix-eureka-client:4.2.0\")")
        ));
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                gradleDependency("implementation group: 'org.springframework.cloud', name: 'spring-cloud-starter-netflix-eureka-client', version: '3.1.2'"),
                gradleDependency("implementation group: 'org.springframework.cloud', name: 'spring-cloud-starter-netflix-eureka-client', version: '4.2.0'")
        ));
    }

    @Test
    void upgradesGradleInterpolatedVersionVariable() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def eurekaVersion = '3.1.7'
                dependencies {
                    implementation "org.springframework.cloud:spring-cloud-starter-netflix-eureka-client:${eurekaVersion}"
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def eurekaVersion = '4.2.0'
                dependencies {
                    implementation "org.springframework.cloud:spring-cloud-starter-netflix-eureka-client:${eurekaVersion}"
                }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotationVersionVariable() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                def eurekaVersion = '3.1.5'
                dependencies {
                    api group: 'org.springframework.cloud', name: 'spring-cloud-starter-netflix-eureka-client', version: eurekaVersion
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                def eurekaVersion = '4.2.0'
                dependencies {
                    api group: 'org.springframework.cloud', name: 'spring-cloud-starter-netflix-eureka-client', version: eurekaVersion
                }
                """
        ));
    }

    @Test
    void leavesGradlePlatformManagedDependencyVersionless() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation platform('org.springframework.cloud:spring-cloud-dependencies:2021.0.8')
                    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
                }
                """
        ));
    }

    @Test
    void leavesKotlinDslWithoutGradleSemanticModelUntouched() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client:3.1.7")
                }
                """
        ));
    }

    @Test
    void leavesTargetAndLaterVersionsUntouched() {
        rewriteRun(
                pomXml(eurekaPom("4.2.0")),
                pomXml(eurekaPom("4.2.1"), spec -> spec.path("later-pom.xml")),
                buildGradle(gradleDependency("implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client:5.0.0'"), spec -> spec.path("later.gradle"))
        );
    }

    @Test
    void leavesEurekaServerAndSimilarArtifactsUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>similar</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-server</artifactId><version>3.1.7</version></dependency>
                  <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-netflix-eureka-client</artifactId><version>3.1.7</version></dependency>
                  <dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId><version>1.10.17</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void leavesMavenProjectWithoutTargetDependencyUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plain-boot-app</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId><version>2.7.18</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void dependencyRecipeLeavesRealEurekaSourceUntouched() {
        // Reduced from emirtotic/aviation-app at 578d5f1a2b9c743b7ead9020006194c1facf9965.
        // @EnableEurekaClient no longer exists in 4.2.x; the README calls out its manual removal.
        rewriteRun(
                spec -> spec.typeValidationOptions(TypeValidation.none()),
                java(
                """
                package com.flight;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

                @SpringBootApplication
                @EnableEurekaClient
                public class FlightServiceApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(FlightServiceApplication.class, args);
                    }
                }
                """
                )
        );
    }

    @Test
    void dependencyRecipeLeavesLoadBalancedRestTemplateSourceUntouched() {
        // Reduced from emirtotic/aviation-app at 578d5f1a2b9c743b7ead9020006194c1facf9965.
        rewriteRun(
                spec -> spec.typeValidationOptions(TypeValidation.none()),
                java(
                """
                package com.flight.config;

                import org.springframework.cloud.client.loadbalancer.LoadBalanced;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.web.client.RestTemplate;

                @Configuration
                public class AppConfig {
                    @Bean
                    @LoadBalanced
                    RestTemplate restTemplate() {
                        return new RestTemplate();
                    }
                }
                """
                )
        );
    }

    @Test
    void dependencyRecipeLeavesEurekaConfigurationUntouched() {
        // Safe subset of aviation-app's application.yml at the same fixed commit.
        rewriteRun(text(
                """
                eureka:
                  client:
                    service-url:
                      defaultZone: http://localhost:8761/eureka
                    registry-fetch-interval-seconds: 3
                  instance:
                    status-page-url-path: /actuator/health
                    health-check-url-path: /actuator/health
                    lease-renewal-interval-in-seconds: 3
                    lease-expiration-duration-in-seconds: 10
                """,
                spec -> spec.path("src/main/resources/application.yml")
        ));
    }

    @Test
    void dependencyRecipeLeavesRegistrationFlagsUntouched() {
        // Reduced from Sohob/VeryLinkedIN at bb02ce79fb5608709ee0ce3d69862949c46775b7.
        rewriteRun(text(
                """
                eureka.client.register-with-eureka=false
                eureka.client.fetch-registry=false
                """,
                spec -> spec.path("account/src/main/resources/application.properties")
        ));
    }

    @Test
    void recipeLoadsWithExpectedIdentity() {
        Recipe recipe = environment().activateRecipes(RECIPE);
        assertEquals(RECIPE, recipe.getName());
        assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static String eurekaPom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>eureka-client</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>%s</version></dependency>
                </dependencies></project>
                """.formatted(version);
    }

    private static String propertyPom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                  <properties><spring-cloud-netflix.version>%s</spring-cloud-netflix.version></properties>
                  <dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>${spring-cloud-netflix.version}</version></dependency></dependencies>
                </project>
                """.formatted(version);
    }

    private static String dependencyManagementPom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-explicit</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>%s</version></dependency></dependencies></dependencyManagement>
                </project>
                """.formatted(version);
    }

    private static String profilePom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>cloud</id><activation><activeByDefault>true</activeByDefault></activation><dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>%s</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """.formatted(version);
    }

    private static String profilePropertyPom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-property</artifactId><version>1</version>
                  <properties><eureka.version>%s</eureka.version></properties>
                  <profiles><profile><id>cloud</id><activation><activeByDefault>true</activeByDefault></activation><dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>${eureka.version}</version>
                  </dependency></dependencies></dependencyManagement></profile></profiles>
                </project>
                """.formatted(version);
    }

    private static String multiplePom(String first, String second) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>multiple</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>%s</version><scope>compile</scope></dependency>
                  <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>%s</version><scope>test</scope></dependency>
                </dependencies></project>
                """.formatted(first, second);
    }

    private static String explicitWithExclusion(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>com.flight</groupId><artifactId>flight-service</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>%s</version>
                  <exclusions><exclusion><groupId>javax.servlet</groupId><artifactId>servlet-api</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """.formatted(version);
    }

    private static String pomWithActuator(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>2.7.2</version><relativePath/></parent>
                  <groupId>com.security</groupId><artifactId>gateway</artifactId><version>1</version>
                  <properties><java.version>17</java.version><spring-cloud.version>2022.0.4</spring-cloud.version></properties><dependencies>
                    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>%s</version></dependency>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
                  </dependencies></project>
                """.formatted(version);
    }

    private static String detailedPom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>details</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>%s</version>
                  <type>jar</type><classifier>tests</classifier><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>com.sun.jersey</groupId><artifactId>jersey-client</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """.formatted(version);
    }

    private static String gradleDependency(String declaration) {
        return """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    %s
                }
                """.formatted(declaration);
    }
}
