package com.huawei.clouds.openrewrite.mybatisspring;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class MyBatisSpringMavenPlatformRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindMyBatisSpringMavenPlatformRisks());
    }

    @Test
    void marksExplicitPlatformVersionsBelowTargetBaselines() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.4.0</version>
                  </parent>
                  <groupId>example</groupId><artifactId>platform-risks</artifactId><version>1</version>
                  <properties>
                    <java.version>11</java.version>
                    <spring.version>6.2.0</spring.version>
                    <spring-batch.version>5.2.0</spring-batch.version>
                  </properties>
                  <dependencies>
                    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>2.0.7</version></dependency>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>6.2.0</version></dependency>
                    <dependency><groupId>org.springframework.batch</groupId><artifactId>spring-batch-core</artifactId><version>5.2.0</version></dependency>
                    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis</artifactId><version>3.4.6</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <!--~~(Spring Boot below 4 manages Spring Framework below the MyBatis-Spring 4 baseline)~~>--><parent>
                    <groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.4.0</version>
                  </parent>
                  <groupId>example</groupId><artifactId>platform-risks</artifactId><version>1</version>
                  <properties>
                    <!--~~(MyBatis-Spring 4 requires Java 17 or newer; upgrade compiler and runtime together)~~>--><java.version>11</java.version>
                    <!--~~(MyBatis-Spring 4 requires Spring Framework 7.0 or newer)~~>--><spring.version>6.2.0</spring.version>
                    <!--~~(MyBatis-Spring 4 batch integration requires Spring Batch 6.0 or newer)~~>--><spring-batch.version>5.2.0</spring-batch.version>
                  </properties>
                  <dependencies>
                    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>2.0.7</version></dependency>
                    <!--~~(MyBatis-Spring 4 requires Spring Framework 7.0 or newer)~~>--><dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>6.2.0</version></dependency>
                    <!--~~(MyBatis-Spring 4 batch integration requires Spring Batch 6.0 or newer)~~>--><dependency><groupId>org.springframework.batch</groupId><artifactId>spring-batch-core</artifactId><version>5.2.0</version></dependency>
                    <!--~~(MyBatis-Spring 4 requires MyBatis 3.5 or newer; target 4.0.0 is tested with 3.5.19)~~>--><dependency><groupId>org.mybatis</groupId><artifactId>mybatis</artifactId><version>3.4.6</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesCompatibleAndUnresolvedPlatformVersionsUnmarked() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>compatible-platform</artifactId><version>1</version>
                  <properties>
                    <java.version>17</java.version>
                    <spring.version>7.0.1</spring.version>
                    <spring-batch.version>6.0.0</spring-batch.version>
                    <managed.spring.version>${revision}</managed.spring.version>
                  </properties>
                  <dependencies>
                    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>4.0.0</version></dependency>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>7.0.1</version></dependency>
                    <dependency><groupId>org.springframework.batch</groupId><artifactId>spring-batch-core</artifactId><version>6.0.0</version></dependency>
                    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis</artifactId><version>3.5.19</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesUnrelatedLegacyPlatformPomUnmarked() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>unrelated-legacy-module</artifactId><version>1</version>
                  <properties><java.version>8</java.version><spring.version>5.3.39</spring.version></properties>
                  <dependencies>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>5.3.39</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }
}
