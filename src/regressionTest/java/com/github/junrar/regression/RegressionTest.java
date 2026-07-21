package com.github.junrar.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junitpioneer.jupiter.DefaultTimeZone;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class RegressionTest {
    private static final Logger logger = LoggerFactory.getLogger(RegressionTest.class);

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * The root of the corpus. Used to compute relative path when storing test output.
     */
    private static final Path root;

    /**
     * The directory containing files under test. Must be a subdirectory of root, or root itself.
     */
    private static final Path testDir;
    private static final String corpus = "corpus";

    /**
     * The directory containing result JSON files.
     */
    private static final Path resourcesDir;

    static {
        String corpusRoot = System.getenv("JUNRAR_REGRESSION_TEST_CORPUS_ROOT");
        if (corpusRoot == null) throw new IllegalArgumentException("Environment variable not set: JUNRAR_REGRESSION_TEST_CORPUS_ROOT");
        root = Paths.get(corpusRoot);
        if (!Files.isDirectory(root)) throw new IllegalArgumentException("Corpus root dir is not a directory: " + root);
        logger.info(() -> "Corpus root dir: " + root);

        String corpusTestDir = System.getenv("JUNRAR_REGRESSION_TEST_CORPUS_DIR");
        if (corpusTestDir == null) throw new IllegalArgumentException("Environment variable not set: JUNRAR_REGRESSION_TEST_CORPUS_DIR");
        testDir = Paths.get(corpusTestDir);
        logger.info(() -> "Corpus test dir: " + testDir);
        if (!testDir.startsWith(root)) throw new IllegalArgumentException("test directory must be within root");

        String resourcesDirProp = System.getProperty("regressionTest.resourcesDir");
        resourcesDir = Paths.get(resourcesDirProp, corpus);
        logger.info(() -> "Resources dir: " + resourcesDir);
    }

    static List<Path> listTestFiles() throws IOException {
        try (var stream =
                 Files.find(testDir, 100, (path, attr) -> attr.isRegularFile() && !path.getFileName().toString().startsWith("."))) {
            return stream.collect(Collectors.toList());
        }
    }

    @Tag("generate")
    @ParameterizedTest
    @MethodSource("listTestFiles")
    @DefaultTimeZone("UTC")
    @Timeout(30)
    void generateData(Path filePath) throws Exception {
        Path relativeFile = root.relativize(filePath.getParent().resolve(filePath.getFileName().toString() + ".json"));
        Path outputFile = resourcesDir.resolve(relativeFile);

        Files.createDirectories(outputFile.getParent());
        try (var archive = new Archive(filePath.toFile())) {
            var record = ArchiveRecord.fromArchive(archive);
            mapper.writeValue(outputFile.toFile(), record);
        } catch (RarException e) {
            var record = ArchiveRecord.fromException(e);
            mapper.writeValue(outputFile.toFile(), record);
        }
    }

    @Tag("check")
    @ParameterizedTest
    @MethodSource("listTestFiles")
    @DefaultTimeZone("UTC")
    @Timeout(30)
    void check(Path filePath) throws Exception {
        var relativeFile = root.relativize(filePath.getParent().resolve(filePath.getFileName().toString() + ".json"));
        var resourceName = Paths.get("/", corpus, relativeFile.toString()).toString();
        var resource = getClass().getResource(resourceName);

        if (resource == null) {
            fail("Could not load resource: " + resourceName);
        } else {
            var inputFile = new File(resource.toURI());

            var inputRecord = mapper.readValue(inputFile, ArchiveRecord.class);

            ArchiveRecord record;
            try (var archive = new Archive(filePath.toFile())) {
                record = ArchiveRecord.fromArchive(archive);
            } catch (RarException e) {
                record = ArchiveRecord.fromException(e);
            }

            assertThat(record).isEqualTo(inputRecord);
        }
    }
}
