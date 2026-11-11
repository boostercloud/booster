package com.boostercloud.rocket.fileuploads.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.framework.types.rockets.RocketFunction;
import com.boostercloud.rocket.fileuploads.azure.FileUploaded;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConfiguration;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import com.boostercloud.rocket.fileuploads.types.RocketProviderPackageType;
import com.boostercloud.framework.types.Logger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** {@code BoosterRocketFiles} (TypeScript has no tests for the core package; behaviour of {@code index.ts}). */
class BoosterRocketFilesTest {

  private static final RocketFilesUserConfiguration CLIENT =
      new RocketFilesUserConfiguration("ClientST", "rocketfiles", List.of("client1", "Client2"));
  private static final RocketFilesUserConfiguration CMS =
      new RocketFilesUserConfiguration("cmsst", "rocketfiles", List.of("cms1", "cms2"));

  private BoosterConfig config;
  private ProviderEventsLibrary events;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = new BoosterConfig("local");
    config.logger = Logger.NOOP;
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    config.setProvider(provider);
    FileUploaded.useLogger(Logger.NOOP);
  }

  @AfterEach
  void tearDown() {
    FileUploaded.resetLogger();
  }

  @Test
  void local_descriptor_with_lower_cased_storage_names() {
    RocketDescriptor descriptor = new BoosterRocketFiles(config, List.of(CLIENT, CMS)).rocketForLocal();

    assertThat(descriptor.packageName()).isEqualTo("com.boostercloud.rocket.fileuploads.local.infrastructure");
    RocketFilesConfiguration parameters = (RocketFilesConfiguration) descriptor.parameters();
    assertThat(parameters.rocketProviderPackage()).isEqualTo(RocketProviderPackageType.LOCAL);
    assertThat(parameters.userConfiguration()).containsExactly(
        new RocketFilesUserConfiguration("clientst", "rocketfiles", List.of("client1", "Client2")), CMS);
    assertThat(CLIENT.storageName()).isEqualTo("ClientST");
  }

  @Test
  void azure_descriptor_with_a_single_configuration() {
    RocketDescriptor descriptor = new BoosterRocketFiles(config, CLIENT).rocketForAzure();

    assertThat(descriptor.packageName()).isEqualTo("com.boostercloud.rocket.fileuploads.azure.infrastructure");
    RocketFilesConfiguration parameters = (RocketFilesConfiguration) descriptor.parameters();
    assertThat(parameters.rocketProviderPackage()).isEqualTo(RocketProviderPackageType.AZURE);
    assertThat(parameters.userConfiguration()).singleElement()
        .isEqualTo(new RocketFilesUserConfiguration("clientst", "rocketfiles", List.of("client1", "Client2")));
  }

  @Test
  void registers_the_rocket_function_and_the_concepts() {
    new BoosterRocketFiles(config, CLIENT).rocketForLocal();

    assertThat(config.getRegisteredRocketFunction("rocket-files")).isPresent();
    assertThat(config.events).containsKey("UploadedFileEvent");
    assertThat(config.entities).containsKey("UploadedFileEntity");
    assertThat(config.reducers).containsKey("UploadedFileEvent");
  }

  @Test
  void a_second_rocket_on_the_same_configuration_fails() {
    BoosterRocketFiles rocketFiles = new BoosterRocketFiles(config, CLIENT);
    rocketFiles.rocketForLocal();

    assertThatThrownBy(rocketFiles::rocketForAzure)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Error registering rocket function with id rocket-files: There is already a rocket function "
            + "registered under the same ID, \"\"");
  }

  @Test
  void a_storage_without_name_fails_like_javascript() {
    BoosterRocketFiles rocketFiles = new BoosterRocketFiles(config,
        new RocketFilesUserConfiguration(null, "rocketfiles", List.of("a")));

    assertThatThrownBy(rocketFiles::rocketForLocal)
        .hasMessage("Cannot read properties of undefined (reading 'toLowerCase')");
  }

  @Test
  void the_local_rocket_function_stores_one_event_per_configuration_with_the_request_as_metadata() {
    new BoosterRocketFiles(config, List.of(CLIENT, CMS)).rocketForLocal();
    RocketFunction function = config.getRegisteredRocketFunction("rocket-files").orElseThrow();
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("BOOSTER_ROCKET_FUNCTION_ID", "rocket-files");
    request.put("uri", "http://localhost:3000/clientst/rocketfiles/client1/myfile.txt");
    request.put("name", "client1/myfile.txt");

    Object result = function.apply(config, request);

    assertThat(result).isNull();
    List<NonPersistedEventEnvelope> stored = storedEnvelopes(2);
    assertThat(stored).extracting(NonPersistedEventEnvelope::value)
        .containsOnly(Map.of("metadata", request));
    assertThat(stored.get(0).entityID()).isNotEqualTo(stored.get(1).entityID());
  }

  @Test
  void the_azure_rocket_function_stores_events_only_for_matching_configurations() {
    RocketFilesUserConfiguration media = new RocketFilesUserConfiguration("mediast", "media-assets", List.of("**"));
    RocketFilesUserConfiguration data = new RocketFilesUserConfiguration("datast", "data-processing",
        List.of("incoming-data"));
    new BoosterRocketFiles(config, List.of(media, data)).rocketForAzure();
    RocketFunction function = config.getRegisteredRocketFunction("rocket-files").orElseThrow();
    Map<String, Object> triggerMetadata = new LinkedHashMap<>();
    triggerMetadata.put("blobTrigger", "data-processing/incoming-data/records.csv");
    triggerMetadata.put("uri", "https://datast.blob.core.windows.net/data-processing/incoming-data/records.csv");
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("blob", new byte[] {1, 2});
    request.put("context", new AzureInvocationContext("id-1", "fileupload_datast", triggerMetadata, null));

    function.apply(config, request);

    assertThat(storedEnvelopes(1)).singleElement().extracting(NonPersistedEventEnvelope::value)
        .isEqualTo(Map.of("metadata", triggerMetadata));
  }

  @Test
  void the_rocket_function_fails_with_the_first_extraction_error() {
    new BoosterRocketFiles(config, List.of(CLIENT, CMS)).rocketForAzure();
    RocketFunction function = config.getRegisteredRocketFunction("rocket-files").orElseThrow();

    assertThatThrownBy(() -> function.apply(config, Map.of("blob", new byte[0])))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("InvocationContext.triggerMetadata is missing for the blob upload request");
  }

  @SuppressWarnings("unchecked")
  private List<NonPersistedEventEnvelope> storedEnvelopes(int calls) {
    ArgumentCaptor<List<NonPersistedEventEnvelope>> captor = ArgumentCaptor.forClass(List.class);
    verify(events, times(calls)).store(captor.capture(), any(BoosterConfig.class));
    List<NonPersistedEventEnvelope> all = new ArrayList<>();
    captor.getAllValues().forEach(all::addAll);
    return all;
  }
}
