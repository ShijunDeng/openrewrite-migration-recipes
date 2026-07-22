package com.huawei.clouds.openrewrite.ojdbc8;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class Ojdbc8BuildRiskAndValidationTest implements RewriteTest {
    private static final String UPGRADE = "com.huawei.clouds.openrewrite.ojdbc8.UpgradeOjdbc8To23_26_1_0_0";
    private static final String MIGRATE = "com.huawei.clouds.openrewrite.ojdbc8.MigrateOjdbc8To23_26_1_0_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindOjdbcBuildMigrationRisks());
    }

    @Test
    void marksExplicitClientOutsideWorkbookTarget() {
        rewriteRun(pom("<dependencies>" + dep("ojdbc8", "19.18.0.0") + "</dependencies>",
                source -> contains(source, "outside the workbook target")));
    }

    @Test
    void marksCompanionSkew() {
        rewriteRun(pom("<dependencies>" + dep("ojdbc8", "23.26.1.0.0") + dep("ucp", "19.19.0.0") + "</dependencies>",
                source -> contains(source, "companion is not aligned")));
    }

    @Test
    void alignedClientAndCompanionsAreClean() {
        rewriteRun(pom("<dependencies>" + dep("ojdbc8", "23.26.1.0.0") + dep("ucp", "23.26.1.0.0") +
                           depWithGroup("com.oracle.database.security", "oraclepki", "23.26.1.0.0") + "</dependencies>"));
    }

    @Test
    void marksUnresolvedPropertyOwner() {
        rewriteRun(pom("<dependencies>" + dep("ojdbc8", "${oracle.jdbc.version}") + "</dependencies>",
                source -> contains(source, "property/parent/BOM/catalog-owned")));
    }

    @Test
    void marksVersionlessClientWithoutLocalOwner() {
        rewriteRun(pom("<dependencies>" + dep("ojdbc8", null) + "</dependencies>",
                source -> contains(source, "versionless")));
    }

    @Test
    void localTargetDependencyManagementProvesVersionlessClient() {
        rewriteRun(pom("<dependencyManagement><dependencies>" + dep("ojdbc8", "23.26.1.0.0") +
                           "</dependencies></dependencyManagement><dependencies>" + dep("ojdbc8", null) + "</dependencies>"));
    }

    @Test
    void localTargetBomProvesVersionlessClient() {
        rewriteRun(pom("<dependencyManagement><dependencies><dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc-bom</artifactId><version>23.26.1.0.0</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement><dependencies>" + dep("ojdbc8", null) + "</dependencies>"));
    }

    @Test
    void rootTargetManagementProvesProfileClient() {
        rewriteRun(pom("<dependencyManagement><dependencies>" + dep("ojdbc8", "23.26.1.0.0") +
                "</dependencies></dependencyManagement><profiles><profile><id>x</id><dependencies>" +
                dep("ojdbc8", null) + "</dependencies></profile></profiles>"));
    }

    @Test
    void profileManagementOverrideWinsOverRootTarget() {
        rewriteRun(pom("<dependencyManagement><dependencies>" + dep("ojdbc8", "23.26.1.0.0") +
                "</dependencies></dependencyManagement><profiles><profile><id>x</id>" +
                "<dependencyManagement><dependencies>" + dep("ojdbc8", "21.9.0.0") +
                "</dependencies></dependencyManagement><dependencies>" + dep("ojdbc8", null) +
                "</dependencies></profile></profiles>", source -> contains(source, "versionless")));
    }

    @Test
    void rootTargetBomProvesProfileClient() {
        rewriteRun(pom("<dependencyManagement><dependencies><dependency>" +
                "<groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc-bom</artifactId>" +
                "<version>23.26.1.0.0</version><type>pom</type><scope>import</scope></dependency>" +
                "</dependencies></dependencyManagement><profiles><profile><id>x</id><dependencies>" +
                dep("ojdbc8", null) + "</dependencies></profile></profiles>"));
    }

    @Test
    void profileManagementDoesNotProveRootClient() {
        rewriteRun(pom("<dependencies>" + dep("ojdbc8", null) + "</dependencies><profiles><profile><id>x</id><dependencyManagement><dependencies>" + dep("ojdbc8", "23.26.1.0.0") + "</dependencies></dependencyManagement></profile></profiles>",
                source -> contains(source, "versionless")));
    }

    @Test
    void marksDuplicateOracleDrivers() {
        rewriteRun(pom("<dependencies>" + dep("ojdbc8", "23.26.1.0.0") + dep("ojdbc11", "23.26.1.0.0") + "</dependencies>",
                source -> contains(source, "Multiple Oracle JDBC driver artifacts")));
    }

    @Test
    void driversInDifferentProfilesDoNotCreateFalseDuplicate() {
        rewriteRun(pom("<profiles><profile><id>eight</id><dependencies>" + dep("ojdbc8", "23.26.1.0.0") +
                           "</dependencies></profile><profile><id>eleven</id><dependencies>" + dep("ojdbc11", "23.26.1.0.0") + "</dependencies></profile></profiles>"));
    }

    @Test
    void rootDriverAndProfileDriverCreateEffectiveDuplicate() {
        rewriteRun(pom("<dependencies>" + dep("ojdbc8", "23.26.1.0.0") +
                "</dependencies><profiles><profile><id>eleven</id><dependencies>" +
                dep("ojdbc11", "23.26.1.0.0") + "</dependencies></profile></profiles>",
                source -> contains(source, "Multiple Oracle JDBC driver artifacts")));
    }

    @Test
    void dependencyManagementAlternativesAreNotActiveDuplicateDrivers() {
        rewriteRun(pom("<dependencyManagement><dependencies>" + dep("ojdbc8", "23.26.1.0.0") +
                dep("ojdbc11", "23.26.1.0.0") + "</dependencies></dependencyManagement>"));
    }

    @Test
    void resolvesSameNamedPropertiesPerProfile() {
        rewriteRun(pom("<properties><oracle.version>23.26.1.0.0</oracle.version></properties>" +
                "<dependencies>" + dep("ojdbc8", "${oracle.version}") + "</dependencies>" +
                "<profiles><profile><id>old</id><properties><oracle.version>21.9.0.0</oracle.version></properties>" +
                "<dependencies>" + dep("ucp", "${oracle.version}") + "</dependencies></profile></profiles>",
                source -> contains(source, "companion is not aligned")));
    }

    @Test
    void marksLegacyComOracleCoordinate() {
        rewriteRun(pom("<dependencies>" + depWithGroup("com.oracle", "ojdbc8", "12.2.0.1") + "</dependencies>",
                source -> contains(source, "Legacy com.oracle ojdbc coordinates")));
    }

    @Test
    void marksMavenClassifierVariant() {
        rewriteRun(pom("<dependencies><dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc8</artifactId><version>19.19.0.0</version><classifier>sources</classifier></dependency></dependencies>",
                source -> contains(source, "nonstandard Oracle JDBC artifact")));
    }

    @Test
    void marksGradleDynamicOwner() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { implementation 'com.oracle.database.jdbc:ojdbc8:23.+' }\n",
                source -> contains(source, "ranged, dynamic")));
    }

    @Test
    void marksGradleCatalogOwner() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { implementation libs.oracle.jdbc }\n",
                source -> contains(source, "Gradle catalog alias")));
    }

    @Test
    void marksKotlinCatalogOwner() {
        rewriteRun(buildGradleKts("plugins { java }\ndependencies { implementation(libs.ojdbc8) }\n",
                source -> contains(source, "Gradle catalog alias")));
    }

    @Test
    void marksGradleMapVariant() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { implementation group: 'com.oracle.database.jdbc', name: 'ojdbc8', version: '19.19.0.0', classifier: 'sources' }\n",
                source -> contains(source, "nonstandard Oracle JDBC artifact")));
    }

    @Test
    void marksGradleCompanionSkew() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { implementation 'com.oracle.database.jdbc:ojdbc8:23.26.1.0.0'; implementation 'com.oracle.database.jdbc:ucp:21.9.0.0' }\n",
                source -> contains(source, "companion is not aligned")));
    }

    @Test
    void marksGradleDuplicateDrivers() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { implementation 'com.oracle.database.jdbc:ojdbc8:23.26.1.0.0'; runtimeOnly 'com.oracle.database.jdbc:ojdbc11:23.26.1.0.0' }\n",
                source -> contains(source, "Multiple Oracle JDBC driver artifacts")));
    }

    @Test
    void alignedKotlinCoordinateIsClean() {
        rewriteRun(buildGradleKts("plugins { java }\ndependencies { implementation(\"com.oracle.database.jdbc:ojdbc8:23.26.1.0.0\") }\n"));
    }

    @Test
    void generatedBuildFilesAreSkipped() {
        rewriteRun(
                pom("<dependencies>" + dep("ojdbc8", "19.18.0.0") + "</dependencies>", source -> source.path("target/generated/pom.xml")),
                buildGradle("plugins { id 'java' }\ndependencies { implementation 'com.oracle.database.jdbc:ojdbc8:19.18.0.0' }\n", source -> source.path("install/build.gradle"))
        );
    }

    @Test
    void discoversAndValidatesRecipes() {
        Environment environment = UpgradeOjdbc8DependencyTest.environment();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> UPGRADE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATE.equals(recipe.getName())));
        assertEquals(UPGRADE, upgrade.getName());
        assertEquals(MIGRATE, migrate.getName());
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
    }

    private static org.openrewrite.test.SourceSpecs pom(String body) {
        return xml("<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>", source -> source.path("pom.xml"));
    }

    private static org.openrewrite.test.SourceSpecs pom(String body, java.util.function.Consumer<org.openrewrite.test.SourceSpec<org.openrewrite.xml.tree.Xml.Document>> consumer) {
        return xml("<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>", source -> { source.path("pom.xml"); consumer.accept(source); });
    }

    private static String dep(String artifact, String version) { return depWithGroup("com.oracle.database.jdbc", artifact, version); }
    private static String depWithGroup(String group, String artifact, String version) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + "</dependency>";
    }

    private static void contains(org.openrewrite.test.SourceSpec<?> source, String token) {
        source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains(token), after::printAll));
    }
}
