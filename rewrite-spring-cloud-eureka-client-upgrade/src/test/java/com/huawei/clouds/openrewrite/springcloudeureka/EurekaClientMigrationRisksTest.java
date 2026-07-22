package com.huawei.clouds.openrewrite.springcloudeureka;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class EurekaClientMigrationRisksTest implements RewriteTest {
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.springcloudeureka.MigrateEurekaClientTo4_2_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(eurekaParser()).typeValidationOptions(TypeValidation.none());
    }

    @Test
    void removesOfficiallyRemovedRibbonEurekaProperty() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                properties(
                        "ribbon.eureka.enabled=true\neureka.client.enabled=true\n",
                        "eureka.client.enabled=true\n"
                ),
                yaml(
                        "ribbon:\n  eureka:\n    enabled: true\nspring:\n  application:\n    name: orders\n",
                        "spring:\n  application:\n    name: orders\n"
                )
        );
    }

    @Test
    void obsoleteConfigurationRemovalIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                properties(
                        "ribbon.eureka.enabled=false\nspring.application.name=orders\n",
                        "spring.application.name=orders\n"
                )
        );
    }

    @Test
    void marksWukongCrmRealRibbonRule() {
        // WuKongOpenSource/WukongCRM-11.0-JAVA, fixed commit 1fa4ec2fdf727111eca73ef4c94dfbb7712c83bb.
        rewriteRun(
                javaRiskSpec(),
                java(
                        """
                        package com.kakarote.gateway.config;
                        import com.netflix.loadbalancer.IRule;
                        class LBConfig { IRule commonRule() { return null; } }
                        """,
                        """
                        package com.kakarote.gateway.config;
                        import com.netflix.loadbalancer.IRule;
                        class LBConfig { /*~~(Ribbon return type detected; replace the policy bean with a Spring Cloud LoadBalancer strategy after mapping behavior)~~>*/IRule commonRule() { return null; } }
                        """
                )
        );
    }

    @Test
    void marksRemovedMutableOptionalArgs() {
        rewriteRun(
                javaRiskSpec(),
                java(
                        """
                        import org.springframework.cloud.netflix.eureka.MutableDiscoveryClientOptionalArgs;
                        class TransportConfig { MutableDiscoveryClientOptionalArgs args = new MutableDiscoveryClientOptionalArgs(); }
                        """,
                        """
                        import org.springframework.cloud.netflix.eureka.MutableDiscoveryClientOptionalArgs;
                        class TransportConfig { /*~~(MutableDiscoveryClientOptionalArgs was removed; choose the target HTTP transport and its transport-specific optional args/customizer)~~>*/MutableDiscoveryClientOptionalArgs args = new MutableDiscoveryClientOptionalArgs(); }
                        """
                )
        );
    }

    @Test
    void marksChangedCloudEurekaClientConstructor() {
        rewriteRun(
                javaRiskSpec(),
                java(
                        """
                        import org.springframework.cloud.netflix.eureka.CloudEurekaClient;
                        class ClientFactory { Object create() { return new CloudEurekaClient(null, null, null); } }
                        """,
                        """
                        import org.springframework.cloud.netflix.eureka.CloudEurekaClient;
                        class ClientFactory { Object create() { return /*~~(CloudEurekaClient constructors now require TransportClientFactories; prefer auto-configuration or choose RestTemplate/RestClient/WebClient/Jersey deliberately)~~>*/new CloudEurekaClient(null, null, null); } }
                        """
                )
        );
    }

    @Test
    void marksDirectRegistryLifecycleAndManualStatus() {
        rewriteRun(
                javaRiskSpec(),
                java(
                        """
                        import com.netflix.discovery.EurekaClient;
                        import com.netflix.appinfo.ApplicationInfoManager;
                        class Lifecycle { void run(EurekaClient client, ApplicationInfoManager manager) {
                            client.getApplication("orders"); manager.setInstanceStatus("DOWN");
                        } }
                        """,
                        """
                        import com.netflix.discovery.EurekaClient;
                        import com.netflix.appinfo.ApplicationInfoManager;
                        class Lifecycle { void run(EurekaClient client, ApplicationInfoManager manager) {
                            /*~~(Direct Eureka client lifecycle/registry API detected; retest initialization, refresh, empty registry, shutdown and transport failures)~~>*/client.getApplication("orders"); /*~~(Manual instance status affects registration and health propagation; verify Actuator health integration and shutdown/drain ordering)~~>*/manager.setInstanceStatus("DOWN");
                        } }
                        """
                )
        );
    }

    @Test
    void marksLoadBalancedFeignAndRefreshAnnotations() {
        rewriteRun(
                javaRiskSpec(),
                java(
                        """
                        import org.springframework.cloud.client.loadbalancer.LoadBalanced;
                        import org.springframework.cloud.openfeign.FeignClient;
                        import org.springframework.cloud.context.config.annotation.RefreshScope;
                        @RefreshScope class Clients { @LoadBalanced Object rest; @FeignClient(name = "inventory") interface Inventory {} }
                        """,
                        """
                        import org.springframework.cloud.client.loadbalancer.LoadBalanced;
                        import org.springframework.cloud.openfeign.FeignClient;
                        import org.springframework.cloud.context.config.annotation.RefreshScope;
                        /*~~(Eureka refresh can briefly unregister the client; AOT/native requires spring.cloud.refresh.enabled=false)~~>*/@RefreshScope class Clients { /*~~(Verify spring-cloud-loadbalancer is present and retest blocking/reactive client, retry, cache, zone, hint and health-check behavior)~~>*/@LoadBalanced Object rest; /*~~(Verify Feign name/contextId matches the Eureka service ID and that OpenFeign plus Spring Cloud LoadBalancer are explicitly present)~~>*/@FeignClient(name = "inventory") interface Inventory {} }
                        """,
                        source -> source.afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("spring-cloud-loadbalancer is present"));
                            assertTrue(printed.contains("Feign name/contextId"));
                            assertTrue(printed.contains("briefly unregister"));
                        })
                )
        );
    }

    @Test
    void marksRedundantEnableDiscoveryClientForReview() {
        rewriteRun(
                javaRiskSpec(),
                java(
                        """
                        import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
                        @EnableDiscoveryClient class Application {}
                        """,
                        """
                        import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
                        /*~~(Discovery auto-configuration no longer needs this annotation in a normal Boot application; verify multi-discovery intent before removal)~~>*/@EnableDiscoveryClient class Application {}
                        """
                )
        );
    }

    @Test
    void marksJakartaBootstrapAndLegacyNativeImports() {
        rewriteRun(
                javaRiskSpec(),
                java(
                        """
                        import javax.servlet.Filter;
                        import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
                        import org.springframework.nativex.hint.NativeHint;
                        class Legacy { Filter filter; PropertySourceLocator locator; NativeHint hint; }
                        """,
                        """
                        /*~~(Spring Boot 3.4 uses Spring Framework 6 and Jakarta EE; migrate this Javax API and its dependency together)~~>*/import javax.servlet.Filter;
                        /*~~(Legacy bootstrap customization detected; choose Config Data or add/test spring-cloud-starter-bootstrap explicitly)~~>*/import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
                        /*~~(Spring Native-era hints are not the Boot 3 AOT model; port to RuntimeHints and test Eureka's native restrictions)~~>*/import org.springframework.nativex.hint.NativeHint;
                        class Legacy { Filter filter; PropertySourceLocator locator; NativeHint hint; }
                        """,
                        source -> source.afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("Jakarta EE"));
                            assertTrue(printed.contains("Legacy bootstrap customization"));
                            assertTrue(printed.contains("Spring Native-era hints"));
                        })
                )
        );
    }

    @Test
    void marksCustomHealthCheckSpi() {
        rewriteRun(
                javaRiskSpec(),
                java(
                        """
                        import com.netflix.appinfo.HealthCheckHandler;
                        class CustomHealth implements HealthCheckHandler {}
                        """,
                        """
                        import com.netflix.appinfo.HealthCheckHandler;
                        /*~~(Custom Eureka client/config/health/transport SPI detected; compile against 4.2.0 and retest lifecycle, TLS, timeouts and registration status)~~>*/class CustomHealth implements HealthCheckHandler {}
                        """
                )
        );
    }

    @Test
    void marksApacheLinkisRealJavaBootAndCloudBaseline() {
        // apache/linkis, fixed commit 974438c957554ad025e4ac4af0f30bac91574c29.
        rewriteRun(
                spec -> spec.recipe(new FindEurekaClientBuildMigrationRisks()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>org.apache.linkis</groupId><artifactId>linkis</artifactId><version>1</version><properties>
                          <java.version>1.8</java.version><spring.boot.version>2.7.18</spring.boot.version><spring-cloud.version>2021.0.8</spring-cloud.version>
                        </properties></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>org.apache.linkis</groupId><artifactId>linkis</artifactId><version>1</version><properties>
                          <!--~~(Eureka Client 4.2.0 and Spring Boot 3.4 require Java 17 or newer)~~>--><java.version>1.8</java.version><!--~~(Eureka Client 4.2.0 belongs to Spring Cloud 2024.0, aligned with Spring Boot 3.4.x)~~>--><spring.boot.version>2.7.18</spring.boot.version><!--~~(Align the whole Spring Cloud BOM to the 2024.0.x release train; do not override only Eureka Client)~~>--><spring-cloud.version>2021.0.8</spring-cloud.version>
                        </properties></project>
                        """
                )
        );
    }

    @Test
    void marksRibbonHttpClient4AndJavaxBuildDependencies() {
        rewriteRun(
                spec -> spec.recipe(new FindEurekaClientBuildMigrationRisks()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy</artifactId><version>1</version><dependencies>
                          <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-ribbon</artifactId><version>2.2.10.RELEASE</version></dependency>
                          <dependency><groupId>org.apache.httpcomponents</groupId><artifactId>httpclient</artifactId><version>4.5.14</version></dependency>
                          <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy</artifactId><version>1</version><dependencies>
                          <!--~~(Netflix Ribbon is removed from the target train; replace it with spring-cloud-starter-loadbalancer and port custom policies)~~>--><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-ribbon</artifactId><version>2.2.10.RELEASE</version></dependency>
                          <!--~~(Apache HttpClient 4 customization does not match the target HttpComponents Client 5 transport; migrate coordinates and APIs deliberately)~~>--><dependency><groupId>org.apache.httpcomponents</groupId><artifactId>httpclient</artifactId><version>4.5.14</version></dependency>
                          <!--~~(Boot 3.4 uses Jakarta APIs; replace this Javax dependency and all affected imports before runtime testing)~~>--><dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                        </dependencies></project>
                        """,
                        source -> source.afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("Netflix Ribbon is removed"));
                            assertTrue(printed.contains("Client 5 transport"));
                            assertTrue(printed.contains("replace this Javax dependency"));
                        })
                )
        );
    }

    @Test
    void marksGradleJavaAndCloudBomBaseline() {
        rewriteRun(
                spec -> spec.recipe(new FindEurekaClientBuildMigrationRisks()),
                buildGradle(
                        "sourceCompatibility = JavaVersion.VERSION_11\ndependencies { implementation platform('org.springframework.cloud:spring-cloud-dependencies:2021.0.8') }",
                        "/*~~(Eureka Client 4.2.0 requires a Java 17+ Gradle toolchain and runtime)~~>*/sourceCompatibility = JavaVersion.VERSION_11\ndependencies { implementation platform(/*~~(Align the complete Spring Cloud platform/BOM to 2024.0.x)~~>*/'org.springframework.cloud:spring-cloud-dependencies:2021.0.8') }"
                )
        );
    }

    @Test
    void acceptsAlignedCloudBomPropertyWithoutFalsePositive() {
        rewriteRun(
                spec -> spec.recipe(new FindEurekaClientBuildMigrationRisks()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>aligned</artifactId><version>1</version>
                          <properties><java.version>17</java.version><spring-cloud.version>2024.0.0</spring-cloud.version></properties>
                          <dependencyManagement><dependencies><dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>${spring-cloud.version}</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                        </project>
                        """
                )
        );
    }

    @Test
    void marksVeryLinkedInRealRegistrationFlags() {
        // Sohob/VeryLinkedIN, fixed commit bb02ce79fb5608709ee0ce3d69862949c46775b7.
        rewriteRun(
                spec -> spec.recipe(new FindEurekaClientPropertiesMigrationRisks()),
                properties(
                        "eureka.client.register-with-eureka=false\neureka.client.fetch-registry=false\n",
                        "~~(Verify registration/fetch flags, Actuator health propagation and registry status/health/home URLs during startup and shutdown)~~>eureka.client.register-with-eureka=false\n~~(Verify registration/fetch flags, Actuator health propagation and registry status/health/home URLs during startup and shutdown)~~>eureka.client.fetch-registry=false\n"
                )
        );
    }

    @Test
    void marksAviationAppRealZoneHealthAndLeaseSettings() {
        // emirtotic/aviation-app, fixed commit 578d5f1a2b9c743b7ead9020006194c1facf9965.
        rewriteRun(
                spec -> spec.recipe(new FindEurekaClientYamlMigrationRisks()),
                yaml(
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
                        """
                        eureka:
                          client:
                            service-url:
                              ~~(Verify defaultZone casing, /eureka/ path, region/zone fallback, DNS/proxy reachability and multi-server behavior)~~>defaultZone: http://localhost:8761/eureka
                            ~~(Retest heartbeat, lease expiration, registry fetch, eviction and self-preservation; sample test intervals are unsafe production defaults)~~>registry-fetch-interval-seconds: 3
                          instance:
                            ~~(Verify registration/fetch flags, Actuator health propagation and registry status/health/home URLs during startup and shutdown)~~>status-page-url-path: /actuator/health
                            ~~(Verify registration/fetch flags, Actuator health propagation and registry status/health/home URLs during startup and shutdown)~~>health-check-url-path: /actuator/health
                            ~~(Retest heartbeat, lease expiration, registry fetch, eviction and self-preservation; sample test intervals are unsafe production defaults)~~>lease-renewal-interval-in-seconds: 3
                            ~~(Retest heartbeat, lease expiration, registry fetch, eviction and self-preservation; sample test intervals are unsafe production defaults)~~>lease-expiration-duration-in-seconds: 10
                        """,
                        source -> source.afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("defaultZone casing"));
                            assertTrue(printed.contains("Actuator health propagation"));
                            assertTrue(printed.contains("Retest heartbeat"));
                        })
                )
        );
    }

    @Test
    void marksBootstrapHealthCheckAtEarlyPhase() {
        rewriteRun(
                spec -> spec.recipe(new FindEurekaClientYamlMigrationRisks()),
                yaml(
                        "eureka:\n  client:\n    healthcheck:\n      enabled: true\n",
                        "eureka:\n  client:\n    healthcheck:\n      ~~(Do not enable Eureka healthcheck in bootstrap configuration; it can register UNKNOWN before Actuator is ready)~~>enabled: true\n",
                        source -> source.path("src/main/resources/bootstrap.yml")
                )
        );
    }

    @Test
    void marksTlsAuthZoneRefreshNetworkAndNativeProperties() {
        rewriteRun(
                spec -> spec.recipe(new FindEurekaClientPropertiesMigrationRisks()),
                properties(
                        """
                        eureka.client.serviceUrl.defaultZone=https://${EUREKA_USER}:${EUREKA_PASSWORD}@eureka.internal/eureka/
                        eureka.client.refresh.enable=true
                        eureka.instance.prefer-ip-address=true
                        server.port=0
                        """,
                        """
                        ~~(Review Eureka TLS/auth/proxy secrets, certificate stores, hostname verification and externally reachable registry URLs)~~>eureka.client.serviceUrl.defaultZone=https://${EUREKA_USER}:${EUREKA_PASSWORD}@eureka.internal/eureka/
                        ~~(Refresh can temporarily unregister Eureka; AOT/native requires spring.cloud.refresh.enabled=false)~~>eureka.client.refresh.enable=true
                        ~~(Verify advertised hostname/IP/instance ID from every interface, container, NAT and IPv4/IPv6 environment)~~>eureka.instance.prefer-ip-address=true
                        ~~(Eureka native/AOT does not support a random client port; keep build-time service IDs/config and runtime values consistent)~~>server.port=0
                        """,
                        source -> source.afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("TLS/auth/proxy secrets"));
                            assertTrue(printed.contains("temporarily unregister"));
                            assertTrue(printed.contains("advertised hostname/IP"));
                            assertTrue(printed.contains("random client port"));
                        })
                )
        );
    }

    @Test
    void ignoresUnrelatedApplicationConfiguration() {
        rewriteRun(
                spec -> spec.recipe(new FindEurekaClientPropertiesMigrationRisks()),
                properties("spring.application.name=orders\nmanagement.endpoints.web.exposure.include=health\n")
        );
    }

    private static Consumer<RecipeSpec> javaRiskSpec() {
        return spec -> spec.recipe(new FindEurekaClientJavaMigrationRisks())
                .parser(eurekaParser()).typeValidationOptions(TypeValidation.none());
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.springcloudeureka")
                .scanYamlResources().build();
    }

    private static JavaParser.Builder<?, ?> eurekaParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package com.netflix.loadbalancer; public interface IRule {}",
                "package org.springframework.cloud.netflix.ribbon; public @interface RibbonClient { String name(); Class<?> configuration(); }",
                "package org.springframework.cloud.netflix.ribbon; public @interface RibbonClients { RibbonClient[] value(); Class<?> defaultConfiguration(); }",
                "package org.springframework.cloud.netflix.eureka; public class MutableDiscoveryClientOptionalArgs { public MutableDiscoveryClientOptionalArgs() {} }",
                "package org.springframework.cloud.netflix.eureka; public class CloudEurekaClient { public CloudEurekaClient(Object a, Object b, Object c) {} }",
                "package org.springframework.cloud.netflix.eureka; public @interface EnableEurekaClient {}",
                "package com.netflix.discovery; public interface EurekaClient { Object getApplication(String name); void shutdown(); }",
                "package com.netflix.appinfo; public class ApplicationInfoManager { public void setInstanceStatus(String status) {} }",
                "package com.netflix.appinfo; public interface HealthCheckHandler {}",
                "package com.netflix.discovery; public interface EurekaClientConfig {}",
                "package com.netflix.appinfo; public interface EurekaInstanceConfig {}",
                "package com.netflix.discovery.shared.transport.jersey; public interface TransportClientFactories {}",
                "package org.springframework.cloud.client.discovery; public @interface EnableDiscoveryClient {}",
                "package org.springframework.cloud.client.loadbalancer; public @interface LoadBalanced {}",
                "package org.springframework.cloud.openfeign; public @interface FeignClient { String name(); String contextId() default \"\"; }",
                "package org.springframework.cloud.context.config.annotation; public @interface RefreshScope {}",
                "package javax.servlet; public interface Filter {}",
                "package org.springframework.cloud.bootstrap.config; public interface PropertySourceLocator {}",
                "package org.springframework.nativex.hint; public @interface NativeHint {}"
        );
    }
}
