package com.huawei.clouds.openrewrite.ojdbc8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeOjdbc8DependencyTest implements RewriteTest {
    private static final String UPGRADE = "com.huawei.clouds.openrewrite.ojdbc8.UpgradeOjdbc8To23_26_1_0_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "upgrades exact workbook source {0}")
    @ValueSource(strings = {"19.19.0.0", "21.9.0.0", "23.2.0.0"})
    void upgradesEveryVisibleWorkbookSource(String version) {
        rewriteRun(pomXml(pom(version), pom("23.26.1.0.0")));
    }

    @Test
    void whitelistIsExactlyTheThreeVisibleWorkbookCells() {
        assertEquals(Set.of("19.19.0.0", "21.9.0.0", "23.2.0.0"),
                UpgradeSelectedOjdbc8Dependency.SOURCE_VERSIONS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"19.18.0.0", "19.20.0.0", "21.8.0.0", "23.26.1.0.0", "[19,24)", "LATEST"})
    void leavesVersionsNotExplicitlyPresentInWorkbook(String version) {
        rewriteRun(pomXml(pom(version)));
    }

    @Test
    void upgradesDependencyManagementLiteral() {
        rewriteRun(pomXml(
                project("<dependencyManagement><dependencies>" + dependency("23.2.0.0") + "</dependencies></dependencyManagement>"),
                project("<dependencyManagement><dependencies>" + dependency("23.26.1.0.0") + "</dependencies></dependencyManagement>")));
    }

    @Test
    void upgradesDirectProfileDependency() {
        rewriteRun(pomXml(profile("<dependencies>" + dependency("21.9.0.0") + "</dependencies>"),
                profile("<dependencies>" + dependency("23.26.1.0.0") + "</dependencies>")));
    }

    @Test
    void upgradesProfileDependencyManagement() {
        rewriteRun(pomXml(profile("<dependencyManagement><dependencies>" + dependency("19.19.0.0") + "</dependencies></dependencyManagement>"),
                profile("<dependencyManagement><dependencies>" + dependency("23.26.1.0.0") + "</dependencies></dependencyManagement>")));
    }

    @Test
    void preservesScopeOptionalAndExclusions() {
        String oldDependency = "<dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc8</artifactId><version>19.19.0.0</version><scope>runtime</scope><optional>true</optional><exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions></dependency>";
        String newDependency = oldDependency.replace("19.19.0.0", "23.26.1.0.0");
        rewriteRun(pomXml(project("<dependencies>" + oldDependency + "</dependencies>"),
                project("<dependencies>" + newDependency + "</dependencies>")));
    }

    @Test
    void upgradesExclusiveLocalProperty() {
        rewriteRun(pomXml(
                project("<properties><ojdbc.version>21.9.0.0</ojdbc.version></properties><dependencies>" + dependency("${ojdbc.version}") + "</dependencies>"),
                project("<properties><ojdbc.version>23.26.1.0.0</ojdbc.version></properties><dependencies>" + dependency("${ojdbc.version}") + "</dependencies>")));
    }

    @Test
    void upgradesSharedOracleBomFamilyProperty() {
        String old = "<properties><oracle.jdbc.version>19.19.0.0</oracle.jdbc.version></properties><dependencyManagement><dependencies>" +
                     dependency("${oracle.jdbc.version}") +
                     "<dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ucp</artifactId><version>${oracle.jdbc.version}</version></dependency>" +
                     "<dependency><groupId>com.oracle.database.security</groupId><artifactId>oraclepki</artifactId><version>${oracle.jdbc.version}</version></dependency>" +
                     "<dependency><groupId>com.oracle.database.ha</groupId><artifactId>ons</artifactId><version>${oracle.jdbc.version}</version></dependency>" +
                     "</dependencies></dependencyManagement>";
        rewriteRun(pomXml(project(old), project(old.replace("19.19.0.0", "23.26.1.0.0"))));
    }

    @Test
    void upgradesProfileLocalProperty() {
        String body = "<properties><ojdbc.version>23.2.0.0</ojdbc.version></properties><dependencies>" + dependency("${ojdbc.version}") + "</dependencies>";
        rewriteRun(pomXml(profile(body), profile(body.replace("23.2.0.0", "23.26.1.0.0"))));
    }

    @Test
    void upgradesLocalBomOwningVersionlessClient() {
        String old = "<dependencyManagement><dependencies><dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc-bom</artifactId><version>19.19.0.0</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement><dependencies>" + dependency(null) + "</dependencies>";
        rewriteRun(pomXml(project(old), project(old.replace("19.19.0.0", "23.26.1.0.0"))));
    }

    @Test
    void upgradesLocalBomPropertyOwningVersionlessClient() {
        String old = "<properties><oracle.version>21.9.0.0</oracle.version></properties><dependencyManagement><dependencies><dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc-bom</artifactId><version>${oracle.version}</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement><dependencies>" + dependency(null) + "</dependencies>";
        rewriteRun(pomXml(project(old), project(old.replace("21.9.0.0", "23.26.1.0.0"))));
    }

    @Test
    void upgradesLocalManagedEntryOwningVersionlessClient() {
        String old = "<dependencyManagement><dependencies>" + dependency("23.2.0.0") + "</dependencies></dependencyManagement><dependencies>" + dependency(null) + "</dependencies>";
        rewriteRun(pomXml(project(old), project(old.replace("23.2.0.0", "23.26.1.0.0"))));
    }

    @Test
    void leavesVersionlessClientOwnedByParent() {
        rewriteRun(pomXml("<project><modelVersion>4.0.0</modelVersion><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.2.5</version><relativePath/></parent><artifactId>app</artifactId><dependencies>" + dependency(null) + "</dependencies></project>"));
    }

    @Test
    void leavesVersionlessClientOwnedByForeignBom() {
        rewriteRun(xml(project("<dependencyManagement><dependencies><dependency><groupId>org.junit</groupId><artifactId>junit-bom</artifactId><version>5.10.2</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement><dependencies>" + dependency(null) + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void leavesPropertyWithUnrelatedTextConsumer() {
        rewriteRun(pomXml(project("<properties><shared.version>19.19.0.0</shared.version></properties><name>app-${shared.version}</name><dependencies>" + dependency("${shared.version}") + "</dependencies>")));
    }

    @Test
    void leavesPropertyUsedInPlugin() {
        rewriteRun(pomXml(project("<properties><shared.version>19.19.0.0</shared.version></properties><dependencies>" + dependency("${shared.version}") + "</dependencies><build><plugins><plugin><groupId>x</groupId><artifactId>x</artifactId><version>${shared.version}</version></plugin></plugins></build>")));
    }

    @Test
    void leavesPropertyUsedInXmlAttribute() {
        rewriteRun(pomXml("<project label=\"${ojdbc.version}\"><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><properties><ojdbc.version>19.19.0.0</ojdbc.version></properties><dependencies>" + dependency("${ojdbc.version}") + "</dependencies></project>"));
    }

    @Test
    void resolvesSameNamedPropertiesInTheirEffectiveScopes() {
        String before = "<properties><ojdbc.version>19.19.0.0</ojdbc.version></properties><dependencies>" +
                dependency("${ojdbc.version}") + "</dependencies><profiles><profile><id>x</id>" +
                "<properties><ojdbc.version>21.9.0.0</ojdbc.version></properties><dependencies>" +
                dependency("${ojdbc.version}") + "</dependencies></profile></profiles>";
        rewriteRun(pomXml(project(before), project(before.replace("19.19.0.0", "23.26.1.0.0")
                .replace("21.9.0.0", "23.26.1.0.0"))));
    }

    @Test
    void rootBomOwnsVersionlessProfileClient() {
        String before = "<dependencyManagement><dependencies><dependency>" +
                "<groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc-bom</artifactId>" +
                "<version>19.19.0.0</version><type>pom</type><scope>import</scope></dependency>" +
                "</dependencies></dependencyManagement><profiles><profile><id>x</id><dependencies>" +
                dependency(null) + "</dependencies></profile></profiles>";
        rewriteRun(pomXml(project(before), project(before.replace("19.19.0.0", "23.26.1.0.0"))));
    }

    @Test
    void profileBomDoesNotOwnRootClient() {
        String body = "<dependencies>" + dependency(null) +
                "</dependencies><profiles><profile><id>x</id><dependencyManagement><dependencies><dependency>" +
                "<groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc-bom</artifactId>" +
                "<version>19.19.0.0</version><type>pom</type><scope>import</scope></dependency>" +
                "</dependencies></dependencyManagement></profile></profiles>";
        rewriteRun(xml(project(body), source -> source.path("pom.xml")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<classifier>sources</classifier>", "<type>test-jar</type>"})
    void leavesMavenVariants(String variant) {
        rewriteRun(pomXml(project("<dependencies><dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc8</artifactId><version>19.19.0.0</version>" + variant + "</dependency></dependencies>")));
    }

    @Test
    void leavesPluginDependencyThatLooksLikeTarget() {
        rewriteRun(pomXml(project("<build><plugins><plugin><groupId>x</groupId><artifactId>x</artifactId><dependencies>" + dependency("19.19.0.0") + "</dependencies></plugin></plugins></build>")));
    }

    @Test
    void upgradesRealGradleFixtureFromBadaProject() {
        // ArturBrogowicz/BADA-proj-cz2, fixed commit a4ac125598bfb0ffb4e186fa00412ae9eb0dde28.
        rewriteRun(buildGradle("plugins { id 'java' }\nrepositories { mavenCentral() }\ndependencies { implementation 'com.oracle.database.jdbc:ojdbc8:19.19.0.0' }\n",
                "plugins { id 'java' }\nrepositories { mavenCentral() }\ndependencies { implementation 'com.oracle.database.jdbc:ojdbc8:23.26.1.0.0' }\n"));
    }

    @Test
    void upgradesRealMavenFixtureFromDataroundLink() {
        // dataround/dataround-link, fixed commit fd2f1e5480b1fcc6169d01b3847f2dcadfea25b9.
        rewriteRun(pomXml(project("<dependencies>" + dependency("23.2.0.0") + "</dependencies>"),
                project("<dependencies>" + dependency("23.26.1.0.0") + "</dependencies>")));
    }

    @Test
    void upgradesGroovyMapNotation() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { runtimeOnly group: 'com.oracle.database.jdbc', name: 'ojdbc8', version: '21.9.0.0' }\n",
                "plugins { id 'java' }\ndependencies { runtimeOnly group: 'com.oracle.database.jdbc', name: 'ojdbc8', version: '23.26.1.0.0' }\n"));
    }

    @Test
    void upgradesGroovyParenthesizedNotation() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { implementation('com.oracle.database.jdbc:ojdbc8:23.2.0.0') }\n",
                "plugins { id 'java' }\ndependencies { implementation('com.oracle.database.jdbc:ojdbc8:23.26.1.0.0') }\n"));
    }

    @Test
    void upgradesKotlinLiteral() {
        rewriteRun(buildGradleKts("plugins { java }\ndependencies { implementation(\"com.oracle.database.jdbc:ojdbc8:19.19.0.0\") }\n",
                "plugins { java }\ndependencies { implementation(\"com.oracle.database.jdbc:ojdbc8:23.26.1.0.0\") }\n"));
    }

    @Test
    void leavesGradleInterpolation() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndef oracleVersion = '19.19.0.0'\ndependencies { implementation \"com.oracle.database.jdbc:ojdbc8:${oracleVersion}\" }\n"));
    }

    @Test
    void leavesGradleDynamicVersion() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { implementation 'com.oracle.database.jdbc:ojdbc8:19.+' }\n"));
    }

    @Test
    void leavesGradleClassifierAndExtension() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { implementation 'com.oracle.database.jdbc:ojdbc8:19.19.0.0:sources'; runtimeOnly 'com.oracle.database.jdbc:ojdbc8:19.19.0.0@jar' }\n"));
    }

    @Test
    void leavesGradleMapVariant() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { implementation group: 'com.oracle.database.jdbc', name: 'ojdbc8', version: '19.19.0.0', classifier: 'sources' }\n"));
    }

    @Test
    void leavesConstraintAndNestedPseudoDsl() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { constraints { implementation 'com.oracle.database.jdbc:ojdbc8:19.19.0.0' }; testImplementation { implementation 'com.oracle.database.jdbc:ojdbc8:19.19.0.0' } }\n"));
    }

    @Test
    void leavesCustomGradleConfiguration() {
        rewriteRun(buildGradle("plugins { id 'java' }\nconfigurations { oracle }\ndependencies { oracle 'com.oracle.database.jdbc:ojdbc8:19.19.0.0' }\n"));
    }

    @Test
    void leavesOrdinaryGroovyString() {
        rewriteRun(buildGradle("plugins { id 'java' }\ndef coordinate = 'com.oracle.database.jdbc:ojdbc8:19.19.0.0'\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target/generated/pom.xml", "Generated-Code/pom.xml", "build/pom.xml", "out/pom.xml",
            "install/pom.xml", "installation/pom.xml", ".m2/repository/pom.xml", ".yarn/pom.xml", ".cache/pom.xml"})
    void skipsGeneratedAndInstalledMavenFiles(String path) {
        rewriteRun(pomXml(pom("19.19.0.0"), source -> source.path(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target/build.gradle", "build/generated/build.gradle", "generated-client/build.gradle",
            "install/build.gradle", "installation/build.gradle"})
    void skipsGeneratedAndInstalledGradleFiles(String path) {
        rewriteRun(buildGradle("plugins { id 'java' }\ndependencies { implementation 'com.oracle.database.jdbc:ojdbc8:19.19.0.0' }\n", source -> source.path(path)));
    }

    private static String pom(String version) { return project("<dependencies>" + dependency(version) + "</dependencies>"); }
    private static String dependency(String version) {
        return "<dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc8</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + "</dependency>";
    }
    private static String project(String body) { return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>"; }
    private static String profile(String body) { return project("<profiles><profile><id>oracle</id>" + body + "</profile></profiles>"); }
    static Environment environment() { return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.ojdbc8").build(); }
}
