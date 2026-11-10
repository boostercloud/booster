package com.boostercloud.rocket.fileuploads.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.rocket.fileuploads.types.RocketFileUploadedLibrary;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code file-uploaded.ts} of the core package. */
class FileUploadedTest {

  private static final RocketFilesUserConfiguration CONFIGURATION =
      new RocketFilesUserConfiguration("clientst", "rocketfiles", List.of("client1"));

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private Logger logger;
  private RocketFileUploadedLibrary library;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = new BoosterConfig("local");
    logger = mock(Logger.class);
    config.logger = logger;
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    config.setProvider(provider);
    library = mock(RocketFileUploadedLibrary.class);
  }

  @Test
  void the_envelope_is_an_uploaded_file_event_with_one_random_id() {
    NonPersistedEventEnvelope envelope = FileUploaded.toEventEnvelope(Map.of("name", "a.txt"));

    assertThat(envelope.entityID()).isEqualTo(envelope.requestID());
    assertThat(envelope.entityID()).isNotEqualTo(FileUploaded.toEventEnvelope(Map.of()).entityID());
    assertThat(envelope.typeName()).isEqualTo("UploadedFileEvent");
    assertThat(envelope.entityTypeName()).isEqualTo("UploadedFileEntity");
    assertThat(envelope.superKind()).isEqualTo(SuperKindType.DOMAIN);
    assertThat(envelope.version()).isEqualTo(1);
    assertThat(envelope.currentUser()).isNull();
    assertThat(envelope.context()).isNull();
    assertThat(BoosterJson.toRawMap(envelope)).containsEntry("kind", "event")
        .containsEntry("value", Map.of("metadata", Map.of("name", "a.txt")))
        .doesNotContainKeys("currentUser", "context", "id");
  }

  @Test
  void stores_the_event_when_the_library_accepts_the_metadata() {
    Map<String, Object> request = Map.of("name", "client1/a.txt");
    when(library.getMetadataFromRequest(request)).thenReturn(request);
    when(library.validateMetadata(CONFIGURATION, request)).thenReturn(true);

    FileUploaded.fileUploaded(config, request, CONFIGURATION, library);

    verify(events).store(anyList(), any(BoosterConfig.class));
  }

  @Test
  void stores_nothing_when_the_library_rejects_the_metadata() {
    when(library.getMetadataFromRequest(any())).thenReturn(Map.of());
    when(library.validateMetadata(any(), any())).thenReturn(false);

    FileUploaded.fileUploaded(config, Map.of(), CONFIGURATION, library);

    verify(events, never()).store(anyList(), any(BoosterConfig.class));
  }

  @Test
  void store_failures_are_logged_and_swallowed() {
    RuntimeException failure = new IllegalStateException("store down");
    when(library.getMetadataFromRequest(any())).thenReturn(Map.of());
    when(library.validateMetadata(any(), any())).thenReturn(true);
    when(events.store(anyList(), any(BoosterConfig.class))).thenThrow(failure);

    FileUploaded.fileUploaded(config, Map.of(), CONFIGURATION, library);

    verify(logger).info("[ROCKET#files] An error occurred while performing a PutItem operation: ", failure);
  }

  @Test
  void metadata_errors_propagate() {
    when(library.getMetadataFromRequest(any())).thenThrow(new IllegalStateException("no metadata"));

    assertThatThrownBy(() -> FileUploaded.fileUploaded(config, Map.of(), CONFIGURATION, library))
        .hasMessage("no metadata");
    verify(events, never()).store(anyList(), any(BoosterConfig.class));
  }
}
