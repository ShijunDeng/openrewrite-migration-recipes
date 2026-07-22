package com.huawei.clouds.openrewrite.netflixeureka;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeNetflixEurekaClientTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.netflixeureka.UpgradeNetflixEurekaClientTo2_0_4";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesDirectMavenDependency() {
        rewriteRun(pomXml(
                pomWithDependency("<version>1.10.18</version>"),
                pomWithDependency("<version>2.0.4</version>")
        ));
    }

    @Test
    void upgradesRuntimeScopedMavenDependency() {
        rewriteRun(pomXml(
                pomWithDependency("<version>1.10.18</version><scope>runtime</scope>"),
                pomWithDependency("<version>2.0.4</version><scope>runtime</scope>")
        ));
    }

    @Test
    void preservesMavenScopeOptionalClassifierAndExclusions() {
        rewriteRun(pomXml(
                pomWithDependency("""
                        <version>1.10.18</version><classifier>tests</classifier><scope>test</scope><optional>true</optional>
                        <exclusions><exclusion><groupId>org.codehaus.jettison</groupId><artifactId>jettison</artifactId></exclusion></exclusions>
                        """),
                pomWithDependency("""
                        <version>2.0.4</version><classifier>tests</classifier><scope>test</scope><optional>true</optional>
                        <exclusions><exclusion><groupId>org.codehaus.jettison</groupId><artifactId>jettison</artifactId></exclusion></exclusions>
                        """)
        ));
    }

    @Test
    void upgradesMavenVersionProperty() {
        rewriteRun(pomXml(
                pomWithPropertiesAndDependency(
                        "<eureka.version>1.10.18</eureka.version>",
                        "<version>${eureka.version}</version>"),
                pomWithPropertiesAndDependency(
                        "<eureka.version>2.0.4</eureka.version>",
                        "<version>${eureka.version}</version>")
        ));
    }

    @Test
    void upgradesDependencyManagementVersion() {
        rewriteRun(pomXml(
                managedPom("<version>1.10.18</version>"),
                managedPom("<version>2.0.4</version>")
        ));
    }

    @Test
    void upgradesDependencyManagementProperty() {
        rewriteRun(pomXml(
                managedPropertyPom("1.10.18"),
                managedPropertyPom("2.0.4")
        ));
    }

    @Test
    void upgradesDependencyInsideMavenProfile() {
        rewriteRun(pomXml(
                profilePom("<version>1.10.18</version>"),
                profilePom("<version>2.0.4</version>")
        ));
    }

    @Test
    void upgradesProfileScopedVersionProperty() {
        rewriteRun(pomXml(
                profilePropertyPom("1.10.18"),
                profilePropertyPom("2.0.4")
        ));
    }

    @Test
    void upgradesGradleStringNotation() {
        rewriteRun(buildGradle(
                gradleWithDependency("implementation 'com.netflix.eureka:eureka-client:1.10.18'"),
                gradleWithDependency("implementation 'com.netflix.eureka:eureka-client:2.0.4'")
        ));
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                gradleWithDependency("runtimeOnly group: 'com.netflix.eureka', name: 'eureka-client', version: '1.10.18'"),
                gradleWithDependency("runtimeOnly group: 'com.netflix.eureka', name: 'eureka-client', version: '2.0.4'")
        ));
    }

    @Test
    void upgradesGradleVersionVariable() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def eurekaVersion = '1.10.18'
                dependencies {
                    implementation "com.netflix.eureka:eureka-client:${eurekaVersion}"
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def eurekaVersion = '2.0.4'
                dependencies {
                    implementation "com.netflix.eureka:eureka-client:${eurekaVersion}"
                }
                """
        ));
    }

    @Test
    void upgradesEveryExplicitGradleOccurrence() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    api 'com.netflix.eureka:eureka-client:1.10.18'
                    testImplementation group: 'com.netflix.eureka', name: 'eureka-client', version: '1.10.18'
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    api 'com.netflix.eureka:eureka-client:2.0.4'
                    testImplementation group: 'com.netflix.eureka', name: 'eureka-client', version: '2.0.4'
                }
                """
        ));
    }

    @Test
    void keepsMavenUsageVersionlessWhileUpdatingLocalManagement() {
        rewriteRun(pomXml(
                locallyManagedVersionlessPom("1.10.18"),
                locallyManagedVersionlessPom("2.0.4")
        ));
    }

    @Test
    void leavesBomManagedMavenDependencyVersionless() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>spring-cloud-app</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId>
                    <version>2023.0.4</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>
                  </dependency></dependencies>
                </project>
                """));
    }

    @Test
    void leavesPlatformManagedGradleDependencyVersionless() {
        rewriteRun(buildGradle("""
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation platform('org.springframework.cloud:spring-cloud-dependencies:2023.0.4')
                    implementation 'com.netflix.eureka:eureka-client'
                }
                """));
    }

    @Test
    void leavesKotlinDslWithoutGradleSemanticModelUntouched() {
        rewriteRun(buildGradleKts("""
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    implementation("com.netflix.eureka:eureka-client:1.10.18")
                }
                """));
    }

    @Test
    void leavesTargetVersionUntouched() {
        rewriteRun(pomXml(pomWithDependency("<version>2.0.4</version>")));
    }

    @Test
    void leavesLaterVersionUntouched() {
        rewriteRun(
                pomXml(pomWithDependency("<version>2.0.6</version>")),
                buildGradle(gradleWithDependency("implementation 'com.netflix.eureka:eureka-client:2.0.6'"),
                        spec -> spec.path("later.gradle"))
        );
    }

    @Test
    void leavesEurekaCoreAndJersey3CompanionsUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>companions</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-core</artifactId><version>1.10.18</version></dependency>
                    <dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-client-jersey3</artifactId><version>2.0.2</version></dependency>
                    <dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-client-archaius2</artifactId><version>2.0.2</version></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void leavesSpringCloudWrapperUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>wrapper</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId><version>4.1.4</version>
                  </dependency></dependencies>
                </project>
                """));
    }

    @Test
    void leavesSameArtifactFromDifferentGroupUntouched() {
        rewriteRun(buildGradle(gradleWithDependency(
                "implementation 'example.eureka:eureka-client:1.10.18'")));
    }

    @Test
    void leavesDiscoveryClientLifecycleSourceForManualMigration() {
        // Reduced from Gravitee at ad52ed93c38dc7d3200040bc183aa9010518000a.
        // DiscoveryClient's two-argument constructor is incompatible in 2.x; a transport
        // choice is architectural, so this dependency recipe deliberately does not guess.
        rewriteRun(java("""
                package io.gravitee.discovery.eureka;

                class EurekaServiceDiscovery {
                    private DiscoveryClient client;

                    void start(ApplicationInfoManager info, EurekaClientConfig config) {
                        client = new DiscoveryClient(info, config);
                    }

                    void stop() {
                        client.shutdown();
                    }
                }

                class DiscoveryClient {
                    DiscoveryClient(ApplicationInfoManager info, EurekaClientConfig config) {}
                    void shutdown() {}
                }
                class ApplicationInfoManager {}
                interface EurekaClientConfig {}
                """));
    }

    @Test
    void leavesRawEurekaPropertiesForManualValidation() {
        rewriteRun(text("""
                eureka.name=inventory
                eureka.registration.enabled=true
                eureka.shouldFetchRegistry=true
                eureka.serviceUrl.default=http://localhost:8761/eureka/
                eureka.preferSameZone=true
                """, spec -> spec.path("src/main/resources/eureka-client.properties")));
    }

    @Test
    void leavesSpringCloudTlsAndHealthConfigurationUntouched() {
        rewriteRun(text("""
                eureka:
                  client:
                    healthcheck:
                      enabled: true
                    tls:
                      enabled: true
                      key-store: classpath:client.p12
                  instance:
                    metadata-map:
                      zone: zone-a
                """, spec -> spec.path("src/main/resources/application.yml")));
    }

    @Test
    void upgradesApacheDubboBomSnapshot() {
        // Reduced from apache/dubbo-spi-extensions at 705910bd9bdd9e8f42c436c2a5d1927d5f7a2876:
        // https://github.com/apache/dubbo-spi-extensions/blob/705910bd9bdd9e8f42c436c2a5d1927d5f7a2876/dubbo-extensions-dependencies-bom/pom.xml#L134
        rewriteRun(pomXml(
                sharedCompanionPropertyPom("1.10.18"),
                sharedCompanionPropertyPom("2.0.4")
        ));
    }

    @Test
    void upgradesApacheSeataManagedSnapshotAndPreservesExclusions() {
        // Reduced from apache/incubator-seata at e6d0860a4345b10cb59c65c78215ec51d67f59d1:
        // https://github.com/apache/incubator-seata/blob/e6d0860a4345b10cb59c65c78215ec51d67f59d1/dependencies/pom.xml#L57
        rewriteRun(pomXml(
                seataSnapshotPom("1.10.18"),
                seataSnapshotPom("2.0.4")
        ));
    }

    @Test
    void upgradesGraviteeSnapshotAndPreservesLifecycleSource() {
        // Build and source reduced from gravitee-io-community/gravitee-service-discovery-eureka
        // at ad52ed93c38dc7d3200040bc183aa9010518000a.
        rewriteRun(
                pomXml(
                        pomWithPropertiesAndDependency("<eureka.version>1.10.18</eureka.version>", "<version>${eureka.version}</version>"),
                        pomWithPropertiesAndDependency("<eureka.version>2.0.4</eureka.version>", "<version>${eureka.version}</version>")
                ),
                text("""
                        client = new DiscoveryClient(
                            new ApplicationInfoManager(instanceConfig, instanceInfo),
                            new EurekaClientConfigBean(environment, new EurekaTransportConfigBean(environment)));
                        client.registerEventListener(listener);
                        client.unregisterEventListener(listener);
                        """, spec -> spec.path("src/main/java/io/gravitee/discovery/eureka/EurekaServiceDiscovery.java"))
        );
    }

    @Test
    void upgradesCorneastSnapshotAndPreservesConsumerSource() {
        // Reduced from Alioth4J/corneast at 4e94c5be23b28a91f107e65811322fdfde906d30:
        // https://github.com/Alioth4J/corneast/blob/4e94c5be23b28a91f107e65811322fdfde906d30/corneast-client/pom.xml#L26-L29
        rewriteRun(
                pomXml(pomWithDependency("<version>1.10.18</version>"), pomWithDependency("<version>2.0.4</version>")),
                text("""
                        this.eurekaClient = new DiscoveryClient(applicationInfoManager, clientConfig);
                        Application application = eurekaClient.getApplication(applicationName);
                        return application.getInstances();
                        """, spec -> spec.path("corneast-client/src/main/java/com/alioth4j/corneast/client/eureka/EurekaConsumer.java"))
        );
    }

    @Test
    void upgradesZuulGradleSnapshotAndPreservesNeighborDependencies() {
        // Reduced from gridgentoo/zuul at 3d5a5fdf9f3cc8c3866ac2b3f6ed058202c6f1ad:
        // https://github.com/gridgentoo/zuul/blob/3d5a5fdf9f3cc8c3866ac2b3f6ed058202c6f1ad/zuul-core/build.gradle#L18-L24
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                def versions_ribbon = '2.7.18'
                dependencies {
                    api project(':zuul-discovery')
                    api "com.netflix.ribbon:ribbon-core:${versions_ribbon}"
                    api "com.netflix.eureka:eureka-client:1.10.18"
                    api 'io.reactivex:rxjava:1.3.8'
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                def versions_ribbon = '2.7.18'
                dependencies {
                    api project(':zuul-discovery')
                    api "com.netflix.ribbon:ribbon-core:${versions_ribbon}"
                    api "com.netflix.eureka:eureka-client:2.0.4"
                    api 'io.reactivex:rxjava:1.3.8'
                }
                """
        ));
    }

    @Test
    void recipeMetadataIsDiscoverable() {
        Recipe recipe = environment().activateRecipes(RECIPE_NAME);
        assertEquals("Upgrade Netflix Eureka Client to 2.0.4", recipe.getDisplayName());
        assertTrue(recipe.getDescription().contains("explicitly versioned"));
        assertTrue(recipe.getTags().contains("netflix-eureka"));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static String pomWithDependency(String details) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>eureka-app</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>%s
                  </dependency></dependencies>
                </project>
                """.formatted(details);
    }

    private static String pomWithPropertiesAndDependency(String properties, String details) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>eureka-app</artifactId><version>1</version>
                  <properties>%s</properties>
                  <dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>%s
                  </dependency></dependencies>
                </project>
                """.formatted(properties, details);
    }

    private static String managedPom(String details) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>eureka-bom</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>%s
                  </dependency></dependencies></dependencyManagement>
                </project>
                """.formatted(details);
    }

    private static String managedPropertyPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>eureka-bom</artifactId><version>1</version>
                  <properties><eureka.version>%s</eureka.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId><version>${eureka.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """.formatted(version);
    }

    private static String profilePom(String details) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>profile-app</artifactId><version>1</version>
                  <profiles><profile><id>eureka</id><activation><activeByDefault>true</activeByDefault></activation><dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>%s
                  </dependency></dependencies></profile></profiles>
                </project>
                """.formatted(details);
    }

    private static String profilePropertyPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>profile-app</artifactId><version>1</version>
                  <properties><eureka.version>%s</eureka.version></properties>
                  <profiles><profile><id>eureka</id><activation><activeByDefault>true</activeByDefault></activation>
                    <dependencies><dependency>
                      <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId><version>${eureka.version}</version>
                    </dependency></dependencies>
                  </profile></profiles>
                </project>
                """.formatted(version);
    }

    private static String gradleWithDependency(String dependency) {
        return """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    %s
                }
                """.formatted(dependency);
    }

    private static String locallyManagedVersionlessPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>managed-app</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId><version>%s</version>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
    }

    private static String sharedCompanionPropertyPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.apache.dubbo</groupId><artifactId>dubbo-extensions-dependencies-bom</artifactId><version>1</version>
                  <properties><eureka.version>%s</eureka.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId><version>${eureka.version}</version></dependency>
                    <dependency><groupId>com.netflix.eureka</groupId><artifactId>eureka-core</artifactId><version>${eureka.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """.formatted(version);
    }

    private static String seataSnapshotPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.apache.seata</groupId><artifactId>seata-dependencies</artifactId><version>1</version>
                  <properties><eureka-clients.version>%s</eureka-clients.version><jettison.version>1.5.4</jettison.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.netflix.eureka</groupId><artifactId>eureka-client</artifactId><version>${eureka-clients.version}</version>
                    <exclusions>
                      <exclusion><groupId>javax.servlet</groupId><artifactId>servlet-api</artifactId></exclusion>
                      <exclusion><groupId>org.codehaus.jettison</groupId><artifactId>jettison</artifactId></exclusion>
                    </exclusions>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """.formatted(version);
    }
}
