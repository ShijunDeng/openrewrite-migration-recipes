package com.huawei.clouds.openrewrite.junrar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindJunrarSourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJunrar7510SourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath("junrar"));
    }

    @ParameterizedTest(name = "Junrar.extract overload {0}")
    @MethodSource("extractOverloads")
    void marksAllJunrarExtractDestinations(String label, String imports, String call) {
        rewriteRun(markedJava(imports + """

                class Extraction {
                    void run() throws Exception {
                        %s;
                    }
                }
                """.formatted(call), label + ".java", FindJunrar7510SourceRisks.DESTINATION));
    }

    static Stream<Arguments> extractOverloads() {
        return Stream.of(
                Arguments.of("StringPaths", "import com.github.junrar.Junrar;",
                        "Junrar.extract(\"input.rar\", \"out\")"),
                Arguments.of("StringPassword", "import com.github.junrar.Junrar;",
                        "Junrar.extract(\"input.rar\", \"out\", \"secret\")"),
                Arguments.of("FilePaths",
                        "import java.io.File;\nimport com.github.junrar.Junrar;",
                        "Junrar.extract(new File(\"input.rar\"), new File(\"out\"))"),
                Arguments.of("FilePassword",
                        "import java.io.File;\nimport com.github.junrar.Junrar;",
                        "Junrar.extract(new File(\"input.rar\"), new File(\"out\"), \"secret\")"),
                Arguments.of("InputStream",
                        "import java.io.ByteArrayInputStream;\nimport java.io.File;\n" +
                        "import com.github.junrar.Junrar;",
                        "Junrar.extract(new ByteArrayInputStream(new byte[0]), new File(\"out\"))"),
                Arguments.of("InputStreamPassword",
                        "import java.io.ByteArrayInputStream;\nimport java.io.File;\n" +
                        "import com.github.junrar.Junrar;",
                        "Junrar.extract(new ByteArrayInputStream(new byte[0]), new File(\"out\"), \"secret\")"),
                Arguments.of("VolumeManager",
                        "import java.io.File;\nimport com.github.junrar.Junrar;\n" +
                        "import com.github.junrar.volume.VolumeManager;",
                        "Junrar.extract((VolumeManager) null, new File(\"out\"))"),
                Arguments.of("VolumeManagerPassword",
                        "import java.io.File;\nimport com.github.junrar.Junrar;\n" +
                        "import com.github.junrar.volume.VolumeManager;",
                        "Junrar.extract((VolumeManager) null, new File(\"out\"), \"secret\")")
        );
    }

    @Test
    void marksExtractArchiveBusinessWrapperAndExactCall() {
        rewriteRun(markedJava("""
                import java.io.File;
                import com.github.junrar.Junrar;

                class ArchiveService {
                    void extractArchive(File archive, File destination) throws Exception {
                        Junrar.extract(archive, destination);
                    }
                }
                """, "ArchiveService.java", FindJunrar7510SourceRisks.DESTINATION));
    }

    @ParameterizedTest(name = "custom extraction path API {0}")
    @MethodSource("pathApis")
    void marksCustomExtractionPathApis(String label, String expression) {
        rewriteRun(markedJava("""
                import com.github.junrar.rarfile.FileHeader;

                class EntryPath {
                    Object path(FileHeader header) {
                        return %s;
                    }
                }
                """.formatted(expression), label + ".java",
                FindJunrar7510SourceRisks.CUSTOM_EXTRACTION));
    }

    static Stream<Arguments> pathApis() {
        return Stream.of(
                Arguments.of("FileName", "header.getFileName()"),
                Arguments.of("FileNameW", "header.getFileNameW()"),
                Arguments.of("FileNameString", "header.getFileNameString()"),
                Arguments.of("FileNameBytes", "header.getFileNameByteArray()")
        );
    }

    @Test
    void marksArchiveExtractFileAsCustomDestinationResponsibility() {
        rewriteRun(markedJava("""
                import java.io.OutputStream;
                import com.github.junrar.Archive;
                import com.github.junrar.rarfile.FileHeader;

                class CustomExtractor {
                    void write(Archive archive, FileHeader header, OutputStream output) throws Exception {
                        archive.extractFile(header, output);
                    }
                }
                """, "CustomExtractor.java", FindJunrar7510SourceRisks.CUSTOM_EXTRACTION));
    }

    @ParameterizedTest(name = "Archive constructor source {0}")
    @MethodSource("archiveConstructors")
    void marksArchiveParsingAndFormatBoundary(String label, String imports, String expression) {
        rewriteRun(markedJava(imports + """

                class ArchiveReader {
                    Object open() throws Exception {
                        return %s;
                    }
                }
                """.formatted(expression), label + ".java",
                FindJunrar7510SourceRisks.ARCHIVE_FORMAT));
    }

    static Stream<Arguments> archiveConstructors() {
        return Stream.of(
                Arguments.of("ArchiveFile",
                        "import java.io.File;\nimport com.github.junrar.Archive;",
                        "new Archive(new File(\"input.rar\"))"),
                Arguments.of("ArchiveFilePassword",
                        "import java.io.File;\nimport com.github.junrar.Archive;",
                        "new Archive(new File(\"input.rar\"), \"secret\")"),
                Arguments.of("ArchiveStream",
                        "import java.io.ByteArrayInputStream;\nimport com.github.junrar.Archive;",
                        "new Archive(new ByteArrayInputStream(new byte[0]))"),
                Arguments.of("ArchiveStreamPassword",
                        "import java.io.ByteArrayInputStream;\nimport com.github.junrar.Archive;",
                        "new Archive(new ByteArrayInputStream(new byte[0]), \"secret\")")
        );
    }

    @Test
    void marksArchiveGetInputStreamFormatAndResourceBoundary() {
        rewriteRun(markedJava("""
                import java.io.InputStream;
                import com.github.junrar.Archive;
                import com.github.junrar.rarfile.FileHeader;

                class Streaming {
                    InputStream open(Archive archive, FileHeader header) throws Exception {
                        return archive.getInputStream(header);
                    }
                }
                """, "Streaming.java", FindJunrar7510SourceRisks.ARCHIVE_FORMAT));
    }

    @Test
    void marksInputStreamVolumeLengthSemanticChange() {
        rewriteRun(markedJava("""
                import com.github.junrar.volume.InputStreamVolume;

                class Progress {
                    long total(InputStreamVolume volume) {
                        return volume.getLength();
                    }
                }
                """, "Progress.java", FindJunrar7510SourceRisks.STREAM));
    }

    @ParameterizedTest(name = "RarException boundary {0}")
    @ValueSource(strings = {
            "RarException", "CorruptHeaderException", "CrcErrorException",
            "BadRarArchiveException", "UnsupportedRarEncryptedException",
            "UnsupportedRarV5Exception"
    })
    void marksRarExceptionAndSubclasses(String exception) {
        rewriteRun(markedJava("""
                import com.github.junrar.exception.%s;

                class FailureBoundary {
                    void run() {
                        try {
                            throw new %s();
                        } catch (%s failure) {
                            System.err.println(failure.getMessage());
                        }
                    }
                }
                """.formatted(exception, exception, exception), exception + ".java",
                FindJunrar7510SourceRisks.EXCEPTION));
    }

    @Test
    void ordinaryFileAndZipCodeIsNotMarked() {
        rewriteRun(java("""
                import java.io.File;
                import java.util.zip.ZipFile;

                class Unrelated {
                    String extractArchive(File file) throws Exception {
                        return new ZipFile(file).getName();
                    }
                }
                """));
    }

    @Test
    void sameNamedBusinessMethodsAreNotMarkedWithoutJunrarTypes() {
        rewriteRun(java("""
                class Archive {
                    void extractFile(Object header, Object output) {}
                    Object getInputStream(Object header) { return null; }
                }
                class Junrar {
                    static void extract(String source, String target) {}
                }
                class Business {
                    void run(Archive archive) {
                        archive.extractFile(null, null);
                        archive.getInputStream(null);
                        Junrar.extract("a", "b");
                    }
                }
                """));
    }

    @ParameterizedTest(name = "generated Java source {0}")
    @ValueSource(strings = {
            "target", "build", "generated", "generatedSources", "install", ".gradle", ".m2",
            ".idea", "node_modules", "vendor", "reports", "test-results", "tmp", "TEMP"
    })
    void generatedSourceIsNeverMarked(String parent) {
        rewriteRun(java("""
                import com.github.junrar.Junrar;
                class Generated {
                    void run() throws Exception {
                        Junrar.extract("input.rar", "out");
                    }
                }
                """, source -> source.path(parent + "/Generated.java")));
    }

    private static org.openrewrite.test.SourceSpecs markedJava(
            String sourceCode, String path, String marker) {
        return java(sourceCode, source -> source.path(path).after(actual -> actual)
                .afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(marker), printed);
                }));
    }
}
