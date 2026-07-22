package com.huawei.clouds.openrewrite.guava;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;

class FindGuavaBuildRisksTest implements RewriteTest {
    @Test
    void marksGradle6WrapperAndObsoleteGwtProperty() {
        rewriteRun(
                spec -> spec.recipe(UpgradeGuavaTest.environment().activateRecipes(
                        "com.huawei.clouds.openrewrite.guava.FindGuavaBuildMigrationRisks")),
                text(
                        "distributionUrl=https\\://services.gradle.org/distributions/gradle-6.9.4-bin.zip",
                        "~~>distributionUrl=https\\://services.gradle.org/distributions/gradle-6.9.4-bin.zip",
                        source -> source.path("gradle/wrapper/gradle-wrapper.properties")
                ),
                text(
                        "guava.gwt.emergency_reenable_rpc=true",
                        "~~>guava.gwt.emergency_reenable_rpc=true",
                        source -> source.path("src/main/resources/application.properties")
                ),
                text(
                        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14-bin.zip",
                        source -> source.path("modern/gradle/wrapper/gradle-wrapper.properties")
                )
        );
    }
}
