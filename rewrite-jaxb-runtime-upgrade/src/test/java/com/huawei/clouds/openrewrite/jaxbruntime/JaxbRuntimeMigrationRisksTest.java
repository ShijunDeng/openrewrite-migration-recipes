package com.huawei.clouds.openrewrite.jaxbruntime;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

class JaxbRuntimeMigrationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath(
                        "jaxb-api", "javax.activation-api", "jakarta.xml.bind-api", "jakarta.activation-api", "jaxb-runtime"))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void marksRemovedValidatorAndCreateValidator() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbJavaMigrationRisks()),
                java(
                        """
                        import javax.xml.bind.JAXBContext;
                        import javax.xml.bind.Validator;
                        class Validation { Validator old(JAXBContext context) throws Exception { return context.createValidator(); } }
                        """,
                        """
                        import javax.xml.bind.JAXBContext;
                        /*~~(JAXB Validator was removed; attach javax.xml.validation.Schema to Marshaller/Unmarshaller instead)~~>*/import javax.xml.bind.Validator;
                        class Validation { Validator old(JAXBContext context) throws Exception { return /*~~(Removed JAXB validation API; use SchemaFactory and Marshaller/Unmarshaller#setSchema)~~>*/context.createValidator(); } }
                        """
                )
        );
    }

    @Test
    void marksDatatypeConverterAndReflectionProviderName() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbJavaMigrationRisks()),
                java(
                        """
                        import javax.xml.bind.DatatypeConverter;
                        class Parsing { Object parse() throws Exception {
                            int value = DatatypeConverter.parseInt("not-an-int");
                            return Class.forName("com.sun.xml.bind.v2.ContextFactory");
                        } }
                        """,
                        """
                        import javax.xml.bind.DatatypeConverter;
                        class Parsing { Object parse() throws Exception {
                            int value = /*~~(JAXB 4 rejects more invalid lexical values; add negative tests for date, QName, number and Base64 parsing)~~>*/DatatypeConverter.parseInt("not-an-int");
                            return /*~~(Reflection-based class loading may retain javax or old RI names; verify the literal and native-image metadata)~~>*/Class.forName(/*~~(Old JAXB provider lookup/reflection name is incompatible with JAXB 4 ServiceLoader/JAXBContextFactory discovery)~~>*/"com.sun.xml.bind.v2.ContextFactory");
                        } }
                        """
                )
        );
    }

    @Test
    void marksStaticMarshallerAndNativeSerialization() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbJavaMigrationRisks()),
                java(
                        """
                        import javax.xml.bind.Marshaller;
                        import java.io.ObjectOutputStream;
                        class Shared { static Marshaller marshaller; void save(ObjectOutputStream out, Object value) throws Exception { out.writeObject(value); } }
                        """,
                        """
                        import javax.xml.bind.Marshaller;
                        import java.io.ObjectOutputStream;
                        class Shared { /*~~(Marshaller and Unmarshaller are not thread-safe; create per operation or use a correctly bounded pool/ThreadLocal)~~>*/static Marshaller marshaller; void save(ObjectOutputStream out, Object value) throws Exception { /*~~(If this serializes JAXB-bound classes, verify class names, serialVersionUID and cached/message payload compatibility)~~>*/out.writeObject(value); } }
                        """
                )
        );
    }

    @Test
    void marksRiInternalImportButLeavesMigratedMapperAlone() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbJavaMigrationRisks()),
                java(
                        """
                        import com.sun.xml.bind.v2.runtime.JAXBContextImpl;
                        import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
                        class RiUse { JAXBContextImpl context; NamespacePrefixMapper mapper; }
                        """,
                        """
                        /*~~(RI internal API has no compatibility guarantee; replace it with a standard JAXB API/SPI or port it manually)~~>*/import com.sun.xml.bind.v2.runtime.JAXBContextImpl;
                        import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
                        class RiUse { JAXBContextImpl context; NamespacePrefixMapper mapper; }
                        """,
                        source -> source.path("src/test/java/RiUse.java")
                )
        );
    }

    @Test
    void marksLegacyJavaAndXjcPluginInMaven() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbBuildMigrationRisks()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>xjc</artifactId><version>1</version>
                          <properties><maven.compiler.release>8</maven.compiler.release></properties>
                          <build><plugins><plugin><groupId>org.codehaus.mojo</groupId><artifactId>jaxb2-maven-plugin</artifactId><version>2.5.0</version></plugin></plugins></build>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>xjc</artifactId><version>1</version>
                          <properties><!--~~(JAXB Runtime 4.0.8 requires Java 11 or newer)~~>--><maven.compiler.release>8</maven.compiler.release></properties>
                          <build><plugins><!--~~(JAXB/XJC plugin detected; select a JAXB 4 compatible plugin, regenerate from a clean directory, and diff generated sources)~~>--><plugin><groupId>org.codehaus.mojo</groupId><artifactId>jaxb2-maven-plugin</artifactId><version>2.5.0</version></plugin></plugins></build>
                        </project>
                        """
                )
        );
    }

    @Test
    void marksLegacyAndMisalignedCompanionDependencies() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbBuildMigrationRisks()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>mixed</artifactId><version>1</version><dependencies>
                          <dependency><groupId>javax.xml.bind</groupId><artifactId>jaxb-api</artifactId><version>2.3.1</version></dependency>
                          <dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-xjc</artifactId><version>2.3.8</version></dependency>
                          <dependency><groupId>jakarta.activation</groupId><artifactId>jakarta.activation-api</artifactId><version>2.1.0</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>mixed</artifactId><version>1</version><dependencies>
                          <!--~~(Legacy Javax JAXB/Activation API cannot be mixed with JAXB 4; migrate coordinate, imports and consumers together)~~>--><dependency><groupId>javax.xml.bind</groupId><artifactId>jaxb-api</artifactId><version>2.3.1</version></dependency>
                          <!--~~(JAXB Runtime/core/XJC/JXC artifacts must be aligned to 4.0.8; verify generated-code plugins and classpath)~~>--><dependency><groupId>com.sun.xml.bind</groupId><artifactId>jaxb-xjc</artifactId><version>2.3.8</version></dependency>
                          <!--~~(JAXB Runtime 4.0.8 BOM aligns jakarta.activation-api to 2.1.4)~~>--><dependency><groupId>jakarta.activation</groupId><artifactId>jakarta.activation-api</artifactId><version>2.1.0</version></dependency>
                        </dependencies></project>
                        """
                )
        );
    }

    @Test
    void marksVersionlessRuntimeAndGradleJava8() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbBuildMigrationRisks()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-bom</artifactId><version>4.0.8</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement><dependencies>
                          <dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-bom</artifactId><version>4.0.8</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement><dependencies>
                          <!--~~(Versionless JAXB Runtime is controlled by a parent/BOM; align that owner to 4.0.8 instead of injecting a local version)~~>--><dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId></dependency>
                        </dependencies></project>
                        """
                ),
                buildGradle(
                        "sourceCompatibility = '1.8'\n",
                        "/*~~(JAXB Runtime 4.0.8 requires Java 11 or newer for build, XJC and runtime)~~>*/sourceCompatibility = '1.8'\n"
                )
        );
    }

    @Test
    void marksRemovedProviderDiscoveryFiles() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbConfigurationMigrationRisks()),
                text(
                        "javax.xml.bind.context.factory=com.sun.xml.bind.v2.ContextFactory\n",
                        "~~(JAXB 4 removed jaxb.properties provider discovery; register JAXBContextFactory with ServiceLoader/module provides)~~>javax.xml.bind.context.factory=com.sun.xml.bind.v2.ContextFactory\n",
                        source -> source.path("src/main/resources/model/jaxb.properties")
                ),
                text(
                        "com.sun.xml.bind.v2.ContextFactory\n",
                        "~~(Legacy JAXB service descriptor; register jakarta.xml.bind.JAXBContextFactory and verify shaded/module-path discovery)~~>com.sun.xml.bind.v2.ContextFactory\n",
                        source -> source.path("src/main/resources/META-INF/services/javax.xml.bind.JAXBContext")
                )
        );
    }

    @Test
    void marksOsgiAndNativeImageMetadata() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbConfigurationMigrationRisks()),
                text(
                        "Import-Package: javax.xml.bind,com.sun.xml.bind.v2\n",
                        "~~(OSGi metadata imports legacy Javax/RI packages; align Import-Package/Require-Capability and test provider visibility)~~>Import-Package: javax.xml.bind,com.sun.xml.bind.v2\n",
                        source -> source.path("META-INF/MANIFEST.MF")
                ),
                text(
                        "[{\"name\":\"javax.xml.bind.JAXBContext\"}]\n",
                        "~~(Native-image/reflection metadata contains old JAXB names; regenerate hints for Jakarta JAXB and the chosen provider)~~>[{\"name\":\"javax.xml.bind.JAXBContext\"}]\n",
                        source -> source.path("META-INF/native-image/example/reflect-config.json")
                )
        );
    }

    @Test
    void marksInlineBindingOutsideAutomaticExtensionsAndLeavesUnrelatedText() {
        rewriteRun(
                spec -> spec.recipe(new FindJaxbConfigurationMigrationRisks()),
                text(
                        "<jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\"/>\n",
                        "~~(JAXB binding customization is outside the auto-fixed xjb/jxb/xsd set; migrate standard namespace but preserve /xjc vendor URI)~~><jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\"/>\n",
                        source -> source.path("src/main/wsdl/service.wsdl")
                ),
                text("ordinary documentation\n", source -> source.path("README.txt"))
        );
    }
}
