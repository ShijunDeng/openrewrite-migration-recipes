package com.huawei.clouds.openrewrite.springwebmvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class SpringWebMvcSelectedProjectGateTest implements RewriteTest {
    private static final String PREFIX =
            "com.huawei.clouds.openrewrite.springwebmvc.";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(gatedSourceRecipe())
                .parser(JavaParser.fromJavaVersion()
                        .classpath("javax.servlet-api", "jakarta.servlet-api"))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void selectedMavenOwnerEnablesOfficialSourceMigration() {
        rewriteRun(
                xml(pom("5.3.23"), source -> source.path("pom.xml")),
                servletSource(
                        "import javax.servlet.Filter;\nclass WebFilter { Filter filter; }\n",
                        "import jakarta.servlet.Filter;\nclass WebFilter { Filter filter; }\n",
                        "src/main/java/WebFilter.java"));
    }

    @Test
    void exclusiveMavenPropertyOwnerRetainsPreUpgradeEligibility() {
        rewriteRun(
                xml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>app</artifactId>
                          <version>1</version>
                          <properties><mvc.version>5.3.39</mvc.version></properties>
                          <dependencies><dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-webmvc</artifactId>
                            <version>${mvc.version}</version>
                          </dependency></dependencies>
                        </project>
                        """, source -> source.path("pom.xml")),
                servletSource(
                        "import javax.servlet.Filter;\nclass WebFilter { Filter filter; }\n",
                        "import jakarta.servlet.Filter;\nclass WebFilter { Filter filter; }\n",
                        "src/main/java/WebFilter.java"));
    }

    @Test
    void targetHigherOffWhitelistAndUnrelatedProjectsAreNoop() {
        rewriteRun(
                xml(pom("6.2.19"), source -> source.path("target/pom.xml")),
                servletSource(
                        "import javax.servlet.Filter;\nclass TargetFilter { Filter filter; }\n",
                        null,
                        "target/src/main/java/TargetFilter.java"),
                xml(pom("7.0.0"), source -> source.path("higher/pom.xml")),
                servletSource(
                        "import javax.servlet.Filter;\nclass HigherFilter { Filter filter; }\n",
                        null,
                        "higher/src/main/java/HigherFilter.java"),
                xml(pom("5.3.22"), source -> source.path("off-list/pom.xml")),
                servletSource(
                        "import javax.servlet.Filter;\nclass OffListFilter { Filter filter; }\n",
                        null,
                        "off-list/src/main/java/OffListFilter.java"),
                servletSource(
                        "import javax.servlet.Filter;\nclass UnrelatedFilter { Filter filter; }\n",
                        null,
                        "unrelated/src/main/java/UnrelatedFilter.java"));
    }

    @Test
    void conflictingOwnersBlockEverySourceLeaf() {
        rewriteRun(
                xml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>app</artifactId>
                          <version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>org.springframework</groupId>
                              <artifactId>spring-webmvc</artifactId>
                              <version>5.3.23</version>
                            </dependency>
                            <dependency>
                              <groupId>org.springframework</groupId>
                              <artifactId>spring-webmvc</artifactId>
                              <version>6.2.19</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("pom.xml")),
                servletSource(
                        "import javax.servlet.Filter;\nclass WebFilter { Filter filter; }\n",
                        null,
                        "src/main/java/WebFilter.java"));
    }

    @Test
    void nestedBuildBoundaryStopsOuterEligibility() {
        rewriteRun(
                xml(pom("5.3.23"), source -> source.path("pom.xml")),
                servletSource(
                        "import javax.servlet.Filter;\nclass RootFilter { Filter filter; }\n",
                        "import jakarta.servlet.Filter;\nclass RootFilter { Filter filter; }\n",
                        "src/main/java/RootFilter.java"),
                xml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>nested</artifactId>
                          <version>1</version>
                        </project>
                        """, source -> source.path("nested/pom.xml")),
                servletSource(
                        "import javax.servlet.Filter;\nclass NestedFilter { Filter filter; }\n",
                        null,
                        "nested/src/main/java/NestedFilter.java"));
    }

    @Test
    void selectedGradleOwnerUsesTheSameGate() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework:spring-webmvc:6.1.14' }",
                        source -> source.path("build.gradle")),
                servletSource(
                        "import javax.servlet.Filter;\nclass WebFilter { Filter filter; }\n",
                        "import jakarta.servlet.Filter;\nclass WebFilter { Filter filter; }\n",
                        "src/main/java/WebFilter.java"));
    }

    private static org.openrewrite.test.SourceSpecs servletSource(
            String before, String after, String path) {
        return after == null
                ? java(before, source -> source.path(path))
                : java(before, after, source -> source.path(path));
    }

    private static Recipe gatedSourceRecipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                PREFIX + "MarkSelectedSpringWebMvcProjects",
                PREFIX + "MigrateSelectedSpringWebMvc6Java");
    }

    private static String pom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>app</artifactId>
                  <version>1</version>
                  <dependencies><dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-webmvc</artifactId>
                    <version>%s</version>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
    }
}
