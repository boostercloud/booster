package com.boostercloud.rocket.fileuploads.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.rocket.fileuploads.support.RecordingUserApp;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The Azure path of an upload notification: the Java entry point the rocket's {@code function.json} files point at
 * ({@code BoosterAzureFunctions.rocketBlob}) builds the request the Azure library reads.
 */
class BlobTriggerEntryPointTest {

  @AfterEach
  void tearDown() {
    UserAppRegistry.clear();
    FileUploaded.resetLogger();
  }

  @Test
  void the_rocket_blob_entry_point_request_carries_the_metadata_the_library_validates() {
    BoosterConfig config = new BoosterConfig("production");
    RecordingUserApp app = new RecordingUserApp(config);
    UserAppRegistry.register(app);
    FileUploaded.useLogger(Logger.NOOP);

    new BoosterAzureFunctions().rocketBlob("content".getBytes(StandardCharsets.UTF_8),
        "rocketfiles/client1/myfile.txt", "https://clientst.blob.core.windows.net/rocketfiles/client1/myfile.txt",
        null);

    Object request = app.rocketRequests.getFirst();
    RocketFilesLibrary library = new RocketFilesLibrary();
    Object metadata = library.getMetadataFromRequest(request);
    assertThat(metadata).isEqualTo(Map.of("blobTrigger", "rocketfiles/client1/myfile.txt",
        "uri", "https://clientst.blob.core.windows.net/rocketfiles/client1/myfile.txt"));
    assertThat(library.validateMetadata(
        new RocketFilesUserConfiguration("clientst", "rocketfiles", List.of("client1")), metadata)).isTrue();
    assertThat(library.validateMetadata(
        new RocketFilesUserConfiguration("cmsst", "rocketfiles", List.of("cms1")), metadata)).isFalse();
  }
}
