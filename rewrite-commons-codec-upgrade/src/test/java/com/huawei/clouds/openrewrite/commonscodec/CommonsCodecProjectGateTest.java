package com.huawei.clouds.openrewrite.commonscodec;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class CommonsCodecProjectGateTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
                        .dependsOn(CommonsCodecTestApi.sources()))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath(
                                "com.huawei.clouds.openrewrite.commonscodec",
                                "org.openrewrite.apache.commons.codec",
                                "org.openrewrite.java.dependencies")
                        .build()
                        .activateRecipes(
                                "com.huawei.clouds.openrewrite.commonscodec.MigrateCommonsCodecTo1_22_0"));
    }

    @Test
    void selectedMavenProjectUpgradesBuildAndSource() {
        rewriteRun(
                xml(pom("1.11"), pom("1.22.0"),
                        source -> source.path("pom.xml")),
                java(oldDigest("Selected"), newDigest("Selected"),
                        source -> source.path("src/main/java/Selected.java")));
    }

    @Test
    void mavenPropertyOwnershipAndProfileScopeGateTheWholeRoot() {
        String propertyBefore = project(
                "<properties><codec.version>1.11</codec.version></properties>" +
                "<dependencies><dependency><groupId>commons-codec</groupId>" +
                "<artifactId>commons-codec</artifactId>" +
                "<version>${codec.version}</version></dependency></dependencies>");
        String propertyAfter = propertyBefore.replace(
                "<codec.version>1.11</codec.version>",
                "<codec.version>1.22.0</codec.version>");
        String profileBefore = project(
                "<properties><codec.version>2.0</codec.version></properties>" +
                "<profiles><profile><id>codec</id><properties>" +
                "<codec.version>1.13</codec.version></properties>" +
                "<dependencies><dependency><groupId>commons-codec</groupId>" +
                "<artifactId>commons-codec</artifactId>" +
                "<version>${codec.version}</version></dependency></dependencies>" +
                "</profile></profiles>");
        String profileAfter = profileBefore.replace(
                "<codec.version>1.13</codec.version>",
                "<codec.version>1.22.0</codec.version>");
        String shared = project(
                "<properties><codec.version>1.11</codec.version></properties>" +
                "<dependencies><dependency><groupId>commons-codec</groupId>" +
                "<artifactId>commons-codec</artifactId>" +
                "<version>${codec.version}</version></dependency>" +
                "<dependency><groupId>example</groupId><artifactId>shared</artifactId>" +
                "<version>${codec.version}</version></dependency></dependencies>");
        rewriteRun(
                xml(propertyBefore, propertyAfter,
                        source -> source.path("property/pom.xml")),
                java(oldDigest("PropertySelected"),
                        newDigest("PropertySelected"),
                        source -> source.path(
                                "property/src/main/java/PropertySelected.java")),
                xml(profileBefore, profileAfter,
                        source -> source.path("profile/pom.xml")),
                java(oldDigest("ProfileSelected"),
                        newDigest("ProfileSelected"),
                        source -> source.path(
                                "profile/src/main/java/ProfileSelected.java")),
                xml(shared, source -> source.path("shared/pom.xml")),
                java(oldDigest("SharedBlocked"),
                        source -> source.path(
                        "shared/src/main/java/SharedBlocked.java")));
    }

    @Test
    void versionlessConsumerRequiresSelectedLocalDependencyManagement() {
        String managedBefore = project(
                "<dependencyManagement><dependencies>" + dep("1.11") +
                "</dependencies></dependencyManagement>" +
                "<dependencies><dependency><groupId>commons-codec</groupId>" +
                "<artifactId>commons-codec</artifactId></dependency></dependencies>");
        String managedAfter = managedBefore.replace(
                "<version>1.11</version>", "<version>1.22.0</version>");
        String unmanaged = project(
                "<dependencies>" + dep("1.11") + "</dependencies>" +
                "<profiles><profile><id>unmanaged</id><dependencies>" +
                "<dependency><groupId>commons-codec</groupId>" +
                "<artifactId>commons-codec</artifactId></dependency>" +
                "</dependencies></profile></profiles>");
        rewriteRun(
                pomXml(managedBefore, managedAfter,
                        source -> source.path("managed/pom.xml")),
                java(oldDigest("Managed"), newDigest("Managed"),
                        source -> source.path(
                                "managed/src/main/java/Managed.java")),
                pomXml(unmanaged,
                        source -> source.path("unmanaged/pom.xml")),
                java(oldDigest("Unmanaged"),
                        source -> source.path(
                                "unmanaged/src/main/java/Unmanaged.java")));
    }

    @Test
    void selectedGradleProjectsUpgradeBuildAndSource() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'commons-codec:commons-codec:1.13' }",
                        "dependencies { implementation 'commons-codec:commons-codec:1.22.0' }",
                        source -> source.path("groovy/build.gradle")),
                java(oldDigest("GroovySelected"), newDigest("GroovySelected"),
                        source -> source.path(
                                "groovy/src/main/java/GroovySelected.java")),
                buildGradleKts(
                        "dependencies { implementation(\"commons-codec:commons-codec:1.15\") }",
                        "dependencies { implementation(\"commons-codec:commons-codec:1.22.0\") }",
                        source -> source.path("kotlin/build.gradle.kts")),
                java(oldDigest("KotlinSelected"), newDigest("KotlinSelected"),
                        source -> source.path(
                                "kotlin/src/main/java/KotlinSelected.java")));
    }

    @Test
    void noDependencyTargetOffListFutureAndDynamicProjectsAreNoop() {
        rewriteRun(
                java(oldDigest("NoBuild"),
                        source -> source.path("no-build/src/main/java/NoBuild.java")),
                xml(project(""), source -> source.path("missing/pom.xml")),
                java(oldDigest("Missing"),
                        source -> source.path("missing/src/main/java/Missing.java")),
                xml(pom("1.22.0"), source -> source.path("target/pom.xml")),
                java(oldDigest("Target"),
                        source -> source.path("target/src/main/java/Target.java")),
                xml(pom("1.17.2"), source -> source.path("off-list/pom.xml")),
                java(oldDigest("OffList"),
                        source -> source.path("off-list/src/main/java/OffList.java")),
                xml(pom("2.0"), source -> source.path("future/pom.xml")),
                java(oldDigest("Future"),
                        source -> source.path("future/src/main/java/Future.java")),
                xml(pom("[1.11,2)"), source -> source.path("range/pom.xml")),
                java(oldDigest("Range"),
                        source -> source.path("range/src/main/java/Range.java")),
                xml(pom("${revision}"), source -> source.path("dynamic/pom.xml")),
                java(oldDigest("Dynamic"),
                        source -> source.path("dynamic/src/main/java/Dynamic.java")));
    }

    @Test
    void mixedVersionsVariantsAndNestedOwnersBlockTheWholeProject() {
        String mixed = project("<dependencies>" +
                               dep("1.11") + dep("1.13") +
                               "</dependencies>");
        String variant = project("<dependencies>" +
                                 dep("1.11") +
                                 "<dependency><groupId>commons-codec</groupId>" +
                                 "<artifactId>commons-codec</artifactId>" +
                                 "<version>1.11</version>" +
                                 "<classifier>tests</classifier></dependency>" +
                                 "</dependencies>");
        String nestedOwner = project("<dependencies>" + dep("1.11") +
                                     "</dependencies><build><plugins><plugin>" +
                                     "<artifactId>x</artifactId><dependencies>" +
                                     dep("1.13") +
                                     "</dependencies></plugin></plugins></build>");
        rewriteRun(
                xml(mixed, source -> source.path("mixed/pom.xml")),
                java(oldDigest("Mixed"),
                        source -> source.path("mixed/src/main/java/Mixed.java")),
                xml(variant, source -> source.path("variant/pom.xml")),
                java(oldDigest("Variant"),
                        source -> source.path("variant/src/main/java/Variant.java")),
                xml(nestedOwner, source -> source.path("nested-owner/pom.xml")),
                java(oldDigest("NestedOwner"),
                        source -> source.path(
                                "nested-owner/src/main/java/NestedOwner.java")));
    }

    @Test
    void nestedBuildWithoutDependencyShadowsSelectedParent() {
        rewriteRun(
                xml(pom("1.16.0"), pom("1.22.0"),
                        source -> source.path("pom.xml")),
                java(oldDigest("Parent"), newDigest("Parent"),
                        source -> source.path("src/main/java/Parent.java")),
                xml(project(""), source -> source.path("child/pom.xml")),
                java(oldDigest("Child"),
                        source -> source.path("child/src/main/java/Child.java")));
    }

    @Test
    void siblingBuildEligibilityDoesNotLeak() {
        rewriteRun(
                xml(pom("1.14"), pom("1.22.0"),
                        source -> source.path("selected/pom.xml")),
                java(oldDigest("SelectedSibling"), newDigest("SelectedSibling"),
                        source -> source.path(
                                "selected/src/main/java/SelectedSibling.java")),
                xml(pom("1.17"), source -> source.path("other/pom.xml")),
                java(oldDigest("OtherSibling"),
                        source -> source.path(
                                "other/src/main/java/OtherSibling.java")));
    }

    @Test
    void auxiliaryGradleScriptIsNotABoundaryButConflictsAreHonored() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'commons-codec:commons-codec:1.11' }",
                        source -> source.path("build.gradle")),
                buildGradle(
                        "dependencies { implementation 'commons-codec:commons-codec:1.13' }",
                        source -> source.path("gradle/codec.gradle")),
                java(oldDigest("Blocked"),
                        source -> source.path("src/main/java/Blocked.java")));
    }

    @Test
    void mixedDynamicGradleCoordinatesBlockGroovyAndKotlinRoots() {
        rewriteRun(
                buildGradle("""
                        def codecVersion = '1.13'
                        dependencies {
                            implementation 'commons-codec:commons-codec:1.11'
                            testImplementation "commons-codec:commons-codec:$codecVersion"
                        }
                        """, source -> source.path("groovy/build.gradle")),
                java(oldDigest("GroovyDynamic"),
                        source -> source.path(
                                "groovy/src/main/java/GroovyDynamic.java")),
                buildGradleKts("""
                        val codecVersion = "1.13"
                        dependencies {
                            implementation("commons-codec:commons-codec:1.11")
                            testImplementation("commons-codec:commons-codec:$codecVersion")
                        }
                        """, source -> source.path("kotlin/build.gradle.kts")),
                java(oldDigest("KotlinDynamic"),
                        source -> source.path(
                                "kotlin/src/main/java/KotlinDynamic.java")));
    }

    @Test
    void hiddenMavenCoordinatesAndGradleAliasesBlockTheWholeRoot() {
        String resolvedMaven = project(
                "<properties><codec.group>commons-codec</codec.group></properties>" +
                "<dependencies>" + dep("1.11") +
                "<dependency><groupId>${codec.group}</groupId>" +
                "<artifactId>commons-codec</artifactId>" +
                "<version>1.13</version></dependency></dependencies>");
        String unresolvedMaven = project(
                "<dependencies>" + dep("1.11") +
                "<dependency><groupId>${external.group}</groupId>" +
                "<artifactId>commons-codec</artifactId>" +
                "<version>1.11</version></dependency></dependencies>");
        rewriteRun(
                xml(resolvedMaven,
                        source -> source.path("maven-resolved/pom.xml")),
                java(oldDigest("MavenResolved"),
                        source -> source.path(
                                "maven-resolved/src/main/java/MavenResolved.java")),
                xml(unresolvedMaven,
                        source -> source.path("maven-unresolved/pom.xml")),
                java(oldDigest("MavenUnresolved"),
                        source -> source.path(
                                "maven-unresolved/src/main/java/MavenUnresolved.java")),
                buildGradle("""
                        dependencies {
                            implementation 'commons-codec:commons-codec:1.11'
                            testImplementation(libs.commons.codec)
                        }
                        """, source -> source.path("catalog/build.gradle")),
                java(oldDigest("Catalog"),
                        source -> source.path(
                                "catalog/src/main/java/Catalog.java")),
                buildGradleKts("""
                        val codecVersion = "1.13"
                        dependencies {
                            implementation("commons-codec:commons-codec:1.11")
                            testImplementation("commons-codec:commons-codec:" + codecVersion)
                        }
                        """, source -> source.path("concatenated/build.gradle.kts")),
                java(oldDigest("Concatenated"),
                        source -> source.path(
                                "concatenated/src/main/java/Concatenated.java")));
    }

    @Test
    void auxiliaryGradleScriptWithSameVersionInheritsItsBuildRoot() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'commons-codec:commons-codec:1.11' }",
                        "dependencies { implementation 'commons-codec:commons-codec:1.22.0' }",
                        source -> source.path("build.gradle")),
                buildGradle(
                        "dependencies { implementation 'commons-codec:commons-codec:1.11' }",
                        "dependencies { implementation 'commons-codec:commons-codec:1.22.0' }",
                        source -> source.path("gradle/codec.gradle")),
                java(oldDigest("Inherited"), newDigest("Inherited"),
                        source -> source.path("src/main/java/Inherited.java")));
    }

    @Test
    void generatedTreesAreNeverChangedOrMarked() {
        rewriteRun(
                xml(pom("1.11"), pom("1.22.0"),
                        source -> source.path("pom.xml")),
                java(oldDigest("Generated"),
                        source -> source.path(
                                "target/generated-sources/Generated.java")),
                java(oldDigest("GeneratedSources"),
                        source -> source.path(
                                "generated-sources/GeneratedSources.java")),
                java(oldDigest("Installed"),
                        source -> source.path(
                                "install/src/main/java/Installed.java")),
                text("Import-Package: org.apache.commons.codec.binary\n",
                        source -> source.path(
                                "build/META-INF/MANIFEST.MF")));
    }

    @Test
    void ordinaryInstallerAndGeneratedApiPackagesAreAuthoredSources() {
        rewriteRun(
                xml(pom("1.11"), pom("1.22.0"),
                        source -> source.path("pom.xml")),
                java(oldDigest("Installer"), newDigest("Installer"),
                        source -> source.path(
                                "src/main/java/example/installer/Installer.java")),
                java(oldDigest("Installation"), newDigest("Installation"),
                        source -> source.path(
                                "src/main/java/example/installation/Installation.java")),
                java(oldDigest("GeneratedApi"), newDigest("GeneratedApi"),
                        source -> source.path(
                                "src/main/java/example/generatedapi/GeneratedApi.java")));
    }

    @Test
    void resourcesAreMarkedOnlyInASelectedProject() {
        rewriteRun(
                xml(pom("1.11"), pom("1.22.0"),
                        source -> source.path("selected/pom.xml")),
                text("Import-Package: org.apache.commons.codec.binary\n",
                        source -> source.path("selected/bnd.bnd")
                                .after(actual -> actual)
                                .afterRecipe(after -> assertTrue(
                                        after.printAll().contains(
                                                "complete bundle graph"),
                                        after::printAll))),
                xml(pom("1.17"), source -> source.path("other/pom.xml")),
                text("Import-Package: org.apache.commons.codec.binary\n",
                        source -> source.path("other/bnd.bnd")));
    }

    @Test
    void selectedOfficialBase64MigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("1.15"), pom("1.22.0"),
                        source -> source.path("pom.xml")),
                java(
                        """
                        import org.apache.commons.codec.binary.Base64;
                        class Vector {
                            byte[] encoded() {
                                return Base64.encodeBase64(new byte[] { 0, 0 });
                            }
                        }
                        """,
                        """
                        import java.util.Base64;

                        class Vector {
                            byte[] encoded() {
                                return Base64.getEncoder().encode(new byte[]{0, 0});
                            }
                        }
                        """,
                        source -> source.path(
                                "src/test/java/Vector.java")));
    }

    private static String oldDigest(String className) {
        return "import org.apache.commons.codec.digest.DigestUtils; class " +
               className +
               " { byte[] id(byte[] value){return DigestUtils.sha(value);} }";
    }

    private static String newDigest(String className) {
        return "import org.apache.commons.codec.digest.DigestUtils; class " +
               className +
               " { byte[] id(byte[] value){return DigestUtils.sha1(value);} }";
    }

    private static String pom(String version) {
        return project("<dependencies>" + dep(version) + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion>" +
               "<groupId>example</groupId><artifactId>app</artifactId>" +
               "<version>1</version>" + body + "</project>";
    }

    private static String dep(String version) {
        return "<dependency><groupId>commons-codec</groupId>" +
               "<artifactId>commons-codec</artifactId><version>" +
               version + "</version></dependency>";
    }
}
