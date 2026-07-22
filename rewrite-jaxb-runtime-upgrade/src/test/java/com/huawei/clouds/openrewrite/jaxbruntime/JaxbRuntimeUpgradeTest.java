package com.huawei.clouds.openrewrite.jaxbruntime;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class JaxbRuntimeUpgradeTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.jaxbruntime.UpgradeJaxbRuntimeTo4_0_8";
    private static final String BINDING_RECIPE =
            "com.huawei.clouds.openrewrite.jaxbruntime.MigrateJaxbBindingsToJakarta3";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.jaxbruntime.MigrateJaxbRuntimeTo4_0_8";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE))
                .parser(jaxbParser());
    }

    @Test
    void upgradesSpreadsheetVersion2_3_7() {
        rewriteRun(pomXml(runtimePom("2.3.7"), runtimePom("4.0.8")));
    }

    @Test
    void upgradesSpreadsheetVersion2_3_8FromOpenRouteService() {
        // Reduced from GIScience/openrouteservice at 786d1d119922a0206c837c9b938bdb769453af27:
        // https://github.com/GIScience/openrouteservice/blob/786d1d119922a0206c837c9b938bdb769453af27/openrouteservice/pom.xml#L313-L318
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.heigit.ors</groupId><artifactId>openrouteservice</artifactId><version>6.8.3</version>
                  <dependencies>
                    <dependency>
                      <!-- Java 11 upgrade -->
                      <groupId>org.glassfish.jaxb</groupId>
                      <artifactId>jaxb-runtime</artifactId>
                      <version>2.3.8</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.heigit.ors</groupId><artifactId>openrouteservice</artifactId><version>6.8.3</version>
                  <dependencies>
                    <dependency>
                      <!-- Java 11 upgrade -->
                      <groupId>org.glassfish.jaxb</groupId>
                      <artifactId>jaxb-runtime</artifactId>
                      <version>4.0.8</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesNiFiStyleMavenPropertyAndManagedDependency() {
        // Reduced from apache/nifi at 6bdea7e2047110e68dbd099ea74ed8552e6c064d:
        // https://github.com/apache/nifi/blob/6bdea7e2047110e68dbd099ea74ed8552e6c064d/pom.xml#L128
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.apache.nifi</groupId><artifactId>nifi</artifactId><version>2.0.0-M1</version>
                  <properties><jaxb.runtime.version>2.3.8</jaxb.runtime.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId><version>${jaxb.runtime.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.apache.nifi</groupId><artifactId>nifi</artifactId><version>2.0.0-M1</version>
                  <properties><jaxb.runtime.version>4.0.8</jaxb.runtime.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId><version>${jaxb.runtime.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesDirectDependencyManagementVersion() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId><version>2.3.7</version></dependency></dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId><version>4.0.8</version></dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesDataHubStyleGradleStringNotation() {
        // Reduced from datahub-project/datahub at 55339d3f80f06a35975b38304d7c1af800a97c71:
        // https://github.com/datahub-project/datahub/blob/55339d3f80f06a35975b38304d7c1af800a97c71/metadata-service/war/build.gradle#L76
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.8")
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.8")
                }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotationFrom2_3_7() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly group: 'org.glassfish.jaxb', name: 'jaxb-runtime', version: '2.3.7'
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly group: 'org.glassfish.jaxb', name: 'jaxb-runtime', version: '4.0.8'
                }
                """
        ));
    }

    @Test
    void upgradesGradleVersionVariableFrom2_3_8() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def jaxbRuntimeVersion = '2.3.8'
                dependencies {
                    runtimeOnly "org.glassfish.jaxb:jaxb-runtime:${jaxbRuntimeVersion}"
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def jaxbRuntimeVersion = '4.0.8'
                dependencies {
                    runtimeOnly "org.glassfish.jaxb:jaxb-runtime:${jaxbRuntimeVersion}"
                }
                """
        ));
    }

    @Test
    void preservesMavenScopeAndOptionalFlag() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>classified</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId><version>2.3.8</version><scope>runtime</scope><optional>true</optional>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>classified</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId><version>4.0.8</version><scope>runtime</scope><optional>true</optional>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void leavesTargetAndNewerRuntimeVersionsUntouched() {
        rewriteRun(
                pomXml(runtimePom("4.0.8")),
                pomXml(runtimePom("4.0.9"), spec -> spec.path("newer-pom.xml"))
        );
    }

    @Test
    void leavesSimilarCoordinatesUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>similar</artifactId><version>1</version><dependencies>
                  <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-impl</artifactId><version>2.3.8</version></dependency>
                  <dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-core</artifactId><version>4.0.8</version></dependency>
                  <dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-xjc</artifactId><version>2.3.8</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void leavesUnversionedUnmanagedDependencyUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unmanaged</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-bom</artifactId><version>2.3.8</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                  <dependencies>
                  <dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void dependencyOnlyRecipeDoesNotChangeJavaxSource() {
        rewriteRun(java(
                """
                import javax.xml.bind.JAXBContext;
                import javax.xml.bind.annotation.XmlRootElement;

                @XmlRootElement
                class LegacyModel {
                    JAXBContext context;
                }
                """
        ));
    }

    @Test
    void comprehensiveRecipeChangesLegacyJaxbApiMavenCoordinate() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                pomXml(
                        apiPom("javax.xml.bind", "jaxb-api", "2.3.1"),
                        apiPom("jakarta.xml.bind", "jakarta.xml.bind-api", "4.0.5")
                )
        );
    }

    @Test
    void comprehensiveRecipeUpgradesExistingJakartaCoordinateWithJavaxPackages() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                pomXml(
                        apiPom("jakarta.xml.bind", "jakarta.xml.bind-api", "2.3.3"),
                        apiPom("jakarta.xml.bind", "jakarta.xml.bind-api", "4.0.5")
                )
        );
    }

    @Test
    void comprehensiveRecipeChangesLegacyJaxbApiGradleCoordinate() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)).beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies { implementation 'javax.xml.bind:jaxb-api:2.3.1' }
                        """,
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies { implementation 'jakarta.xml.bind:jakarta.xml.bind-api:4.0.5' }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeChangesActivationApiMavenCoordinate() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                pomXml(
                        apiPom("javax.activation", "javax.activation-api", "1.2.0"),
                        apiPom("jakarta.activation", "jakarta.activation-api", "2.1.4")
                )
        );
    }

    @Test
    void comprehensiveRecipeChangesActivationApiGradleCoordinate() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)).beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        dependencies { api 'javax.activation:javax.activation-api:1.2.0' }
                        """,
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        dependencies { api 'jakarta.activation:jakarta.activation-api:2.1.4' }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeUpgradesExistingJakartaActivationApi() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                pomXml(
                        apiPom("jakarta.activation", "jakarta.activation-api", "1.2.2"),
                        apiPom("jakarta.activation", "jakarta.activation-api", "2.1.4")
                )
        );
    }

    @Test
    void comprehensiveRecipeMigratesJaxbImportsAnnotationsAndQualifiedNames() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.xml.bind.JAXBContext;
                        import javax.xml.bind.JAXBElement;
                        import javax.xml.bind.annotation.XmlAttribute;
                        import javax.xml.bind.annotation.XmlElement;
                        import javax.xml.bind.annotation.XmlRootElement;
                        import javax.xml.bind.annotation.adapters.XmlAdapter;

                        @XmlRootElement(name = "order")
                        class Order {
                            @XmlAttribute String id;
                            @XmlElement String customer;
                            JAXBContext context;
                            JAXBElement<Order> element;
                            XmlAdapter<String, String> adapter;
                            javax.xml.bind.Marshaller marshaller;
                        }
                        """,
                        """
                        import jakarta.xml.bind.JAXBContext;
                        import jakarta.xml.bind.JAXBElement;
                        import jakarta.xml.bind.annotation.XmlAttribute;
                        import jakarta.xml.bind.annotation.XmlElement;
                        import jakarta.xml.bind.annotation.XmlRootElement;
                        import jakarta.xml.bind.annotation.adapters.XmlAdapter;

                        @XmlRootElement(name = "order")
                        class Order {
                            @XmlAttribute String id;
                            @XmlElement String customer;
                            JAXBContext context;
                            JAXBElement<Order> element;
                            XmlAdapter<String, String> adapter;
                            jakarta.xml.bind.Marshaller marshaller;
                        }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeMigratesActivationImports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.activation.DataHandler;
                        import javax.activation.MimeType;

                        class AttachmentHolder {
                            DataHandler handler;
                            MimeType mimeType;
                            javax.activation.DataSource source;
                        }
                        """,
                        """
                        import jakarta.activation.DataHandler;
                        import jakarta.activation.MimeType;

                        class AttachmentHolder {
                            DataHandler handler;
                            MimeType mimeType;
                            jakarta.activation.DataSource source;
                        }
                        """
                )
        );
    }

    @Test
    void migratesGoogleHealthcareStyleNamespacePrefixMapperAndProperty() {
        // Reduced from GoogleCloudPlatform/healthcare-data-harmonization at a69ff9619ae665ce475f6206ebc1fb459f69fbc2:
        // https://github.com/GoogleCloudPlatform/healthcare-data-harmonization/blob/a69ff9619ae665ce475f6206ebc1fb459f69fbc2/wstl1/tools/XmlToJson/src/main/java/com/google/cloud/healthcare/etl/xmltojson/XmlToJsonCDARev2.java
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        package com.sun.xml.bind.marshaller;
                        public abstract class NamespacePrefixMapper {}
                        """,
                        source -> source.path("src/test/java/com/sun/xml/bind/marshaller/NamespacePrefixMapper.java")
                ),
                java(
                        """
                        import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
                        import javax.xml.bind.Marshaller;
                        import javax.xml.bind.PropertyException;

                        class CdaMarshaller {
                            void configure(Marshaller marshaller, NamespacePrefixMapper mapper) throws PropertyException {
                                marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", mapper);
                            }
                        }
                        """,
                        """
                        import jakarta.xml.bind.Marshaller;
                        import jakarta.xml.bind.PropertyException;
                        import org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper;

                        class CdaMarshaller {
                            void configure(Marshaller marshaller, NamespacePrefixMapper mapper) throws PropertyException {
                                marshaller.setProperty("org.glassfish.jaxb.namespacePrefixMapper", mapper);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void migratesSupportedRiMarshallerPropertyKeys() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        class RiProperties {
                            String[] names = {
                                "com.sun.xml.bind.indentString",
                                "com.sun.xml.bind.characterEscapeHandler",
                                "com.sun.xml.bind.xmlDeclaration",
                                "com.sun.xml.bind.xmlHeaders",
                                "com.sun.xml.bind.objectIdentitityCycleDetection"
                            };
                        }
                        """,
                        """
                        class RiProperties {
                            String[] names = {
                                "org.glassfish.jaxb.indentString",
                                "org.glassfish.jaxb.characterEscapeHandler",
                                "org.glassfish.jaxb.xmlDeclaration",
                                "org.glassfish.jaxb.xmlHeaders",
                                "org.glassfish.jaxb.objectIdentitityCycleDetection"
                            };
                        }
                        """
                )
        );
    }

    @Test
    void leavesBusinessStringsAndSimilarPackagesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        class UnrelatedStrings {
                            String documentation = "javax.xml.bind.JAXBContext";
                            String applicationKey = "com.sun.xml.bind.namespace-prefix-mapper";
                            javax.xml.parsers.DocumentBuilder parser;
                        }
                        """
                )
        );
    }

    @Test
    void migratesPahoStyleExternalBindingFileWithJxbPrefix() {
        // Reduced from eclipse-paho/paho.mqtt-spy at 737699afbabaf01520302080a6f8b910f121ab2f:
        // https://github.com/eclipse-paho/paho.mqtt-spy/blob/737699afbabaf01520302080a6f8b910f121ab2f/spy-common/src/main/resources/spy-bindings.xjb
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(BINDING_RECIPE)),
                xml(
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <jxb:bindings version="2.1"
                                      xmlns:jxb="http://java.sun.com/xml/ns/jaxb"
                                      xmlns:xs="http://www.w3.org/2001/XMLSchema">
                          <jxb:bindings schemaLocation="spy.xsd" node="/xs:schema">
                            <jxb:schemaBindings><jxb:package name="org.eclipse.paho.mqttspy.model"/></jxb:schemaBindings>
                          </jxb:bindings>
                        </jxb:bindings>
                        """,
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <jxb:bindings version="3.0"
                                      xmlns:jxb="https://jakarta.ee/xml/ns/jaxb"
                                      xmlns:xs="http://www.w3.org/2001/XMLSchema">
                          <jxb:bindings schemaLocation="spy.xsd" node="/xs:schema">
                            <jxb:schemaBindings><jxb:package name="org.eclipse.paho.mqttspy.model"/></jxb:schemaBindings>
                          </jxb:bindings>
                        </jxb:bindings>
                        """,
                        source -> source.path("spy-common/src/main/resources/spy-bindings.xjb")
                )
        );
    }

    @Test
    void migratesExternalBindingWithJaxbPrefixSingleQuotesAndSchemaUrl() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(BINDING_RECIPE)),
                xml(
                        """
                        <jaxb:bindings xmlns:jaxb='http://java.sun.com/xml/ns/jaxb'
                                       xsi:schemaLocation='http://java.sun.com/xml/ns/jaxb http://java.sun.com/xml/ns/jaxb/bindingschema_2_0.xsd'
                                       xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'
                                       version='1.0'>
                        </jaxb:bindings>
                        """,
                        """
                        <jaxb:bindings xmlns:jaxb='https://jakarta.ee/xml/ns/jaxb'
                                       xsi:schemaLocation='https://jakarta.ee/xml/ns/jaxb https://jakarta.ee/xml/ns/jaxb/bindingschema_3_0.xsd'
                                       xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'
                                       version='3.0'>
                        </jaxb:bindings>
                        """,
                        source -> source.path("src/main/xjb/common.jxb")
                )
        );
    }

    @Test
    void migratesExternalBindingWithDefaultNamespace() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(BINDING_RECIPE)),
                xml(
                        """
                        <bindings xmlns="http://java.sun.com/xml/ns/jaxb" version="2.0"
                                  xmlns:xs="http://www.w3.org/2001/XMLSchema">
                          <bindings schemaLocation="model.xsd" node="/xs:schema"/>
                        </bindings>
                        """,
                        """
                        <bindings xmlns="https://jakarta.ee/xml/ns/jaxb" version="3.0"
                                  xmlns:xs="http://www.w3.org/2001/XMLSchema">
                          <bindings schemaLocation="model.xsd" node="/xs:schema"/>
                        </bindings>
                        """,
                        source -> source.path("src/main/resources/default-namespace.xjb")
                )
        );
    }

    @Test
    void migratesInlineXsdCustomizationNamespaceAndVersion() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(BINDING_RECIPE)),
                xml(
                        """
                        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                                   xmlns:jxb="http://java.sun.com/xml/ns/jaxb"
                                   jxb:version="2.1">
                          <xs:annotation><xs:appinfo><jxb:globalBindings generateElementProperty="false"/></xs:appinfo></xs:annotation>
                        </xs:schema>
                        """,
                        """
                        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                                   xmlns:jxb="https://jakarta.ee/xml/ns/jaxb"
                                   jxb:version="3.0">
                          <xs:annotation><xs:appinfo><jxb:globalBindings generateElementProperty="false"/></xs:appinfo></xs:annotation>
                        </xs:schema>
                        """,
                        source -> source.path("src/main/resources/order.xsd")
                )
        );
    }

    @Test
    void bindingRecipeLeavesJakarta3FilesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(BINDING_RECIPE)),
                xml(
                        """
                        <jxb:bindings version="3.0"
                                      xmlns:jxb="https://jakarta.ee/xml/ns/jaxb"
                                      xmlns:xs="http://www.w3.org/2001/XMLSchema">
                        </jxb:bindings>
                        """,
                        source -> source.path("src/main/resources/current.xjb")
                )
        );
    }

    @Test
    void preservesXjcVendorExtensionNamespaceWhileMigratingStandardBindingNamespace() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(BINDING_RECIPE)),
                xml(
                        """
                        <jxb:bindings version="2.1"
                                      xmlns:jxb="http://java.sun.com/xml/ns/jaxb"
                                      xmlns:xjc="http://java.sun.com/xml/ns/jaxb/xjc"
                                      jxb:extensionBindingPrefixes="xjc">
                          <jxb:globalBindings><xjc:serializable uid="1"/></jxb:globalBindings>
                        </jxb:bindings>
                        """,
                        """
                        <jxb:bindings version="3.0"
                                      xmlns:jxb="https://jakarta.ee/xml/ns/jaxb"
                                      xmlns:xjc="http://java.sun.com/xml/ns/jaxb/xjc"
                                      jxb:extensionBindingPrefixes="xjc">
                          <jxb:globalBindings><xjc:serializable uid="1"/></jxb:globalBindings>
                        </jxb:bindings>
                        """,
                        source -> source.path("src/main/resources/extensions.xjb")
                )
        );
    }

    @Test
    void bindingRecipeDoesNotTouchOrdinaryXmlOrUnlistedExtensions() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(BINDING_RECIPE)),
                xml(
                        """
                        <configuration>
                          <url>http://java.sun.com/xml/ns/jaxb</url>
                          <version>2.1</version>
                        </configuration>
                        """,
                        source -> source.path("src/main/resources/application.xml")
                )
        );
    }

    @Test
    void comprehensiveRecipeMigratesDependencySourceAndBindingTogether() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                pomXml(runtimePom("2.3.7"), runtimePom("4.0.8")),
                java(
                        """
                        import javax.xml.bind.annotation.XmlRootElement;
                        @XmlRootElement class Message {}
                        """,
                        """
                        import jakarta.xml.bind.annotation.XmlRootElement;
                        @XmlRootElement class Message {}
                        """
                ),
                xml(
                        """
                        <jxb:bindings version="2.0" xmlns:jxb="http://java.sun.com/xml/ns/jaxb"/>
                        """,
                        """
                        <jxb:bindings version="3.0" xmlns:jxb="https://jakarta.ee/xml/ns/jaxb"/>
                        """,
                        source -> source.path("src/main/resources/bindings.xjb")
                )
        );
    }

    @Test
    void allDeclarativeRecipesValidate() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe binding = environment.activateRecipes(BINDING_RECIPE);
        Recipe migration = environment.activateRecipes(MIGRATION_RECIPE);

        assertTrue(dependency.validateAll().stream().allMatch(validation -> validation.isValid()),
                dependency.validateAll().toString());
        assertTrue(binding.validateAll().stream().allMatch(validation -> validation.isValid()),
                binding.validateAll().toString());
        assertTrue(migration.validateAll().stream().allMatch(validation -> validation.isValid()),
                migration.validateAll().toString());
        assertEquals(DEPENDENCY_RECIPE, dependency.getName());
        assertEquals(BINDING_RECIPE, binding.getName());
        assertEquals(MIGRATION_RECIPE, migration.getName());
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static JavaParser.Builder<?, ?> jaxbParser() {
        return JavaParser.fromJavaVersion().classpath(
                "jaxb-api",
                "javax.activation-api",
                "jakarta.xml.bind-api",
                "jakarta.activation-api",
                "jaxb-runtime"
        );
    }

    private static String runtimePom(String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>jaxb-app</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId><version>%s</version></dependency>
                </dependencies></project>
                """.formatted(version);
    }

    private static String apiPom(String groupId, String artifactId, String version) {
        return """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>jaxb-api-app</artifactId><version>1</version><dependencies>
                  <dependency><groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version></dependency>
                </dependencies></project>
                """.formatted(groupId, artifactId, version);
    }
}
