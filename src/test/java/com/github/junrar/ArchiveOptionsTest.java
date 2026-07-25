package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

/**
 * P0.8 acceptance rows for the {@link ArchiveOptions} construction-time
 * configuration path (docs/porting/PARITY_PLAN.md P0.8).
 */
public class ArchiveOptionsTest {

    private static final String ENCRYPTED_FIXTURE = "password/rar4-encrypted-junrar.rar";

    @Test
    public void givenCharArrayPassword_whenOpeningEncryptedRar3Fixture_thenExtractsContent()
            throws Exception {
        char[] password = "junrar".toCharArray();
        ArchiveOptions options = ArchiveOptions.builder().password(password).build();
        try (InputStream is = getClass().getResourceAsStream(ENCRYPTED_FIXTURE);
                Archive archive = new Archive(is, options)) {
            assertThat(archive.isEncrypted()).isTrue();
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            assertThat(fileHeaders).hasSize(1);
            FileHeader fileHeader = fileHeaders.get(0);
            assertThat(fileHeader.getFileName()).isEqualTo("file1.txt");

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                archive.extractFile(fileHeader, baos);
                assertThat(baos.toString()).isEqualTo("file1\n");
            }
        }
    }

    @Test
    public void givenArchiveWithPassword_whenClosed_thenInternalPasswordCopyIsWiped()
            throws Exception {
        char[] password = "junrar".toCharArray();
        ArchiveOptions options = ArchiveOptions.builder().password(password).build();
        char[] internalCopy;
        try (InputStream is = getClass().getResourceAsStream(ENCRYPTED_FIXTURE);
                Archive archive = new Archive(is, options)) {
            internalCopy = archive.getPasswordChars();
            // Before-state: the live internal copy still holds the real password.
            assertThat(internalCopy).isEqualTo(password);
        }
        // After try-with-resources closes the archive: the SAME array reference is zeroed in place.
        assertThat(internalCopy).containsOnly('\0');
    }

    @Test
    public void givenBuilderPasswordArray_whenCallerMutatesAfterBuild_thenExtractionStillWorks()
            throws Exception {
        char[] password = "junrar".toCharArray();
        ArchiveOptions options = ArchiveOptions.builder().password(password).build();
        // Mutate the caller's array after build(): the builder must already hold its own copy.
        Arrays.fill(password, 'X');

        try (InputStream is = getClass().getResourceAsStream(ENCRYPTED_FIXTURE);
                Archive archive = new Archive(is, options)) {
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            FileHeader fileHeader = fileHeaders.get(0);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                archive.extractFile(fileHeader, baos);
                assertThat(baos.toString()).isEqualTo("file1\n");
            }
        }
    }

    @Test
    public void
            givenBuilderPasswordArray_whenCallerMutatesBetweenPasswordAndBuild_thenExtractionStillWorks()
                    throws Exception {
        char[] password = "junrar".toCharArray();
        ArchiveOptions.Builder builder = ArchiveOptions.builder().password(password);
        // Mutate the caller's array between Builder.password(char[]) and build(): only the
        // Builder's OWN defensive copy (taken at password(char[]) time) can protect against
        // this. The existing mutate-after-build() test cannot discriminate this layer, because
        // by then the Builder is no longer consulted.
        Arrays.fill(password, 'X');
        ArchiveOptions options = builder.build();

        try (InputStream is = getClass().getResourceAsStream(ENCRYPTED_FIXTURE);
                Archive archive = new Archive(is, options)) {
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            FileHeader fileHeader = fileHeaders.get(0);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                archive.extractFile(fileHeader, baos);
                assertThat(baos.toString()).isEqualTo("file1\n");
            }
        }
    }

    @Test
    public void givenMaxDictionarySizeNotPositive_whenBuild_thenThrowsIllegalArgumentException() {
        ArchiveOptions.Builder builder = ArchiveOptions.builder().maxDictionarySize(0);

        assertThat(catchThrowable(builder::build)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void givenNoOptionsConfigured_whenBuild_thenDefaultMaxDictionarySizeIsFourGiB() {
        ArchiveOptions options = ArchiveOptions.builder().build();

        assertThat(options.getMaxDictionarySize()).isEqualTo(4L * 1024 * 1024 * 1024);
        assertThat(options.getMaxDictionarySize())
                .isEqualTo(ArchiveOptions.DEFAULT_MAX_DICTIONARY_SIZE);
    }

    @Test
    public void givenJunrarExtractWithOptions_whenPasswordProtectedArchive_thenExtractsContent()
            throws Exception {
        File tempFolder = TestCommons.createTempDir();
        try {
            ArchiveOptions options =
                    ArchiveOptions.builder().password("junrar".toCharArray()).build();
            try (InputStream rarStream = getClass().getResourceAsStream(ENCRYPTED_FIXTURE)) {
                Junrar.extract(rarStream, tempFolder, options);
            }

            File unpackFile = new File(tempFolder, "file1.txt");
            assertThat(unpackFile).exists();
            assertThat(unpackFile.length()).isEqualTo(6);
        } finally {
            FileUtils.deleteDirectory(tempFolder);
        }
    }
}
