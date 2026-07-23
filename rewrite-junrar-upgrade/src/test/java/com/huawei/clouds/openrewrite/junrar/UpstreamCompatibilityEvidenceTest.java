package com.huawei.clouds.openrewrite.junrar;

import com.github.junrar.Archive;
import com.github.junrar.Junrar;
import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamCompatibilityEvidenceTest {
    private static final List<String> VERSIONS = List.of("7.5.5", "7.5.8", "7.5.10");

    @Test
    void auditedPublicApiDigestsAreIdentical() throws Exception {
        Properties evidence = evidence();
        Set<String> digests = VERSIONS.stream()
                .map(version -> evidence.getProperty("publicApiSha256." + version))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("14578787306111b9637d5c6c2097ac9df66f986d602ec4e44b8c9fc9e5077ef3"),
                digests);
    }

    @Test
    void everyReleaseRetainsJava8AndAutomaticModuleJunrar() throws Exception {
        Properties evidence = evidence();
        for (String version : VERSIONS) {
            assertEquals("52", evidence.getProperty("classMajor." + version));
            assertEquals("junrar", evidence.getProperty("automaticModule." + version));
        }
    }

    @Test
    void fixedTagAndJarIdentitiesAreComplete() throws Exception {
        Properties evidence = evidence();
        for (String version : VERSIONS) {
            assertTrue(evidence.getProperty("tag." + version).matches("[0-9a-f]{40}"));
            assertTrue(evidence.getProperty("jarSha256." + version).matches("[0-9a-f]{64}"));
        }
        assertEquals("dabca2849b46384765542301f96078097d2c14f6",
                evidence.getProperty("tag.7.5.5"));
        assertEquals("97bf405418d0997717d55e0556045ff80945e099",
                evidence.getProperty("tag.7.5.8"));
        assertEquals("e36ee091ad7311a021e1c928ada103a3eab2d890",
                evidence.getProperty("tag.7.5.10"));
    }

    @Test
    void runtimeFixtureExposesAllEightJunrarExtractOverloads() {
        long overloads = java.util.Arrays.stream(Junrar.class.getMethods())
                .filter(method -> "extract".equals(method.getName())).count();
        assertEquals(8, overloads);
    }

    @Test
    void runtimeFixtureExposesCriticalArchiveAndPathApis() throws Exception {
        Method extractFile = Archive.class.getMethod("extractFile", FileHeader.class, OutputStream.class);
        Method getInputStream = Archive.class.getMethod("getInputStream", FileHeader.class);
        Method getFileName = FileHeader.class.getMethod("getFileName");
        assertNotNull(extractFile);
        assertEquals(InputStream.class, getInputStream.getReturnType());
        assertEquals(String.class, getFileName.getReturnType());
        assertNotNull(Archive.class.getConstructor(File.class));
        assertNotNull(Archive.class.getConstructor(InputStream.class));
    }

    private static Properties evidence() throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = UpstreamCompatibilityEvidenceTest.class.getClassLoader()
                .getResourceAsStream("upstream-api-audit.properties")) {
            assertNotNull(stream);
            properties.load(stream);
        }
        return properties;
    }
}
