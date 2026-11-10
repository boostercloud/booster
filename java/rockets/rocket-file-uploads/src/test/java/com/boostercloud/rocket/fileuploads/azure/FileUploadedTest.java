package com.boostercloud.rocket.fileuploads.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code rocket-file-uploads-azure/test/file-uploaded.test.ts}, plus the cases TypeScript leaves out. */
class FileUploadedTest {

  private Logger logger;

  @BeforeEach
  void setUp() {
    logger = mock(Logger.class);
    FileUploaded.useLogger(logger);
  }

  @AfterEach
  void tearDown() {
    FileUploaded.resetLogger();
  }

  private static Map<String, Object> createMetadata(String blobTrigger) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("blobTrigger", blobTrigger);
    metadata.put("invocationId", "test-invocation-id");
    return metadata;
  }

  private static RocketFilesUserConfiguration configuration(String storage, String container, String... directories) {
    return new RocketFilesUserConfiguration(storage, container, List.of(directories));
  }

  @Nested
  class validateMetadata {

    @Nested
    class container_name_validation {
      @Test
      void returns_false_when_container_name_does_not_match_even_with_wildcard_directory_pattern() {
        assertThat(FileUploaded.validateMetadata(configuration("media_storage", "media-assets", "**"),
            createMetadata("data-processing/incoming-data/records.csv"))).isFalse();
      }

      @Test
      void returns_false_when_container_name_is_a_substring_but_not_exact_match() {
        assertThat(FileUploaded.validateMetadata(configuration("test_storage", "data", "**"),
            createMetadata("data-processing/incoming-data/file.csv"))).isFalse();
      }

      @Test
      void returns_true_when_container_name_matches_and_directory_pattern_matches() {
        assertThat(FileUploaded.validateMetadata(configuration("ingestion_storage", "data-processing", "incoming-data"),
            createMetadata("data-processing/incoming-data/records.csv"))).isTrue();
      }

      @Test
      void returns_true_when_container_name_matches_with_wildcard_directory_pattern() {
        assertThat(FileUploaded.validateMetadata(configuration("media_storage", "media-assets", "**"),
            createMetadata("media-assets/some-dir/file.txt"))).isTrue();
      }
    }

    @Nested
    class directory_validation_with_correct_container {
      @Test
      void returns_false_when_container_matches_but_directory_does_not() {
        assertThat(FileUploaded.validateMetadata(configuration("ingestion_storage", "data-processing", "incoming-data"),
            createMetadata("data-processing/other-directory/file.csv"))).isFalse();
      }

      @Test
      void returns_true_for_nested_directory_with_globstar_pattern() {
        assertThat(FileUploaded.validateMetadata(
            configuration("file_upload_storage", "file-upload-processing", "active-pool/**"),
            createMetadata("file-upload-processing/active-pool/sales-fdm/report.csv"))).isTrue();
      }

      @Test
      void returns_true_for_file_in_root_directory_with_single_star_pattern() {
        assertThat(FileUploaded.validateMetadata(configuration("test_storage", "test-container", "*"),
            createMetadata("test-container/any-folder/file.txt"))).isTrue();
      }
    }

    @Nested
    class duplicate_event_prevention_scenarios {
      private final RocketFilesUserConfiguration wildcardConfig = configuration("media_storage", "media-assets", "**");
      private final RocketFilesUserConfiguration specificDirConfig =
          configuration("ingestion_storage", "data-processing", "incoming-data");

      @Test
      void only_one_configuration_matches_for_data_processing_upload() {
        Map<String, Object> metadata = createMetadata("data-processing/incoming-data/records.csv");
        assertThat(FileUploaded.validateMetadata(wildcardConfig, metadata)).isFalse();
        assertThat(FileUploaded.validateMetadata(specificDirConfig, metadata)).isTrue();
      }

      @Test
      void only_one_configuration_matches_for_media_assets_upload() {
        Map<String, Object> metadata = createMetadata("media-assets/documents/report.pdf");
        assertThat(FileUploaded.validateMetadata(wildcardConfig, metadata)).isTrue();
        assertThat(FileUploaded.validateMetadata(specificDirConfig, metadata)).isFalse();
      }
    }

    @Test
    void ignored_blobs_are_logged_like_console_info() {
      FileUploaded.validateMetadata(configuration("s", "media-assets", "docs"), createMetadata("data/x.csv"));
      FileUploaded.validateMetadata(configuration("s", "media-assets", "docs"), createMetadata("media-assets/x/y.csv"));

      verify(logger).info("Ignoring blob trigger data/x.csv - container name does not match media-assets");
      verify(logger).info("Ignoring blob trigger media-assets/x/y.csv - directory does not match configured directories");
    }

    @Test
    void a_blob_at_the_container_root_only_matches_patterns_matching_the_empty_directory() {
      assertThat(FileUploaded.validateMetadata(configuration("s", "c", "**"), createMetadata("c/file.txt"))).isTrue();
      assertThat(FileUploaded.validateMetadata(configuration("s", "c", "*"), createMetadata("c/file.txt"))).isFalse();
    }

    @Test
    void a_missing_blob_trigger_fails_like_javascript() {
      assertThatThrownBy(() -> FileUploaded.validateMetadata(configuration("s", "c", "**"), Map.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Cannot read properties of undefined (reading 'startsWith')");
    }
  }

  @Nested
  class getMetadataFromRequest {
    @Test
    void extracts_triggerMetadata_from_Azure_Functions_v4_InvocationContext() {
      Map<String, Object> triggerMetadata = new LinkedHashMap<>();
      triggerMetadata.put("blobTrigger", "test-container/test-path/file.txt");
      triggerMetadata.put("invocationId", "test-id");
      Map<String, Object> mockRequest = Map.of(
          "blob", "test content".getBytes(StandardCharsets.UTF_8),
          "context", Map.of("triggerMetadata", triggerMetadata));

      assertThat(FileUploaded.getMetadataFromRequest(mockRequest)).isEqualTo(triggerMetadata);
    }

    @Test
    void reads_the_context_built_by_the_java_entry_point() {
      Map<String, Object> triggerMetadata = Map.of("blobTrigger", "c/d/f.txt", "uri", "https://s.blob.core.windows.net/c/d/f.txt");
      Map<String, Object> request = Map.of("blob", new byte[0],
          "context", new AzureInvocationContext("id", "fileupload_s", triggerMetadata, null));

      assertThat(FileUploaded.getMetadataFromRequest(request)).isEqualTo(triggerMetadata);
    }

    @Test
    void fails_without_trigger_metadata() {
      assertThatThrownBy(() -> FileUploaded.getMetadataFromRequest(Map.of("blob", new byte[0])))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("InvocationContext.triggerMetadata is missing for the blob upload request");
      assertThatThrownBy(() -> FileUploaded.getMetadataFromRequest(Map.of("context", Map.of())))
          .hasMessage("InvocationContext.triggerMetadata is missing for the blob upload request");
    }
  }
}
