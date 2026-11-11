package com.boostercloud.rocket.fileuploads.azure.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.provider.azure.infrastructure.helper.JsonText;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.azure.infrastructure.functions.RocketFilesFileUploadedFunction;
import org.junit.jupiter.api.Test;

/** {@code functions/rocket-files-file-uploaded-function.ts}: the blob trigger definitions and the name checks. */
class FunctionsTest {

  private final BoosterConfig config = configured();

  private static BoosterConfig configured() {
    BoosterConfig config = new BoosterConfig("production");
    config.appName = "test";
    return config;
  }

  @Test
  void one_blob_trigger_function_per_storage() {
    FunctionDefinition definition =
        RocketFilesFileUploadedFunction.generateFunctionDefinition(config, "rocketfiles", "clientst");

    assertThat(definition.name()).isEqualTo("fileupload_clientst");
    assertThat(definition.config().entryPoint()).isEqualTo(BoosterAzureFunctions.ROCKET_BLOB_ENTRY_POINT)
        .isEqualTo("com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions.rocketBlob");
    assertThat(JsonText.stringify(definition.config().toRaw(), 2)).isEqualTo("""
        {
          "bindings": [
            {
              "type": "blobTrigger",
              "direction": "in",
              "name": "blob",
              "path": "rocketfiles/{name}",
              "connection": "clientst",
              "dataType": "binary"
            }
          ],
          "scriptFile": "../test.jar",
          "entryPoint": "com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions.rocketBlob"
        }""");
  }

  @Test
  void names_are_validated_container_first() {
    assertThatThrownBy(() -> RocketFilesFileUploadedFunction.generateFunctionDefinition(config, "My-Container", "St"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid containerName: \"My-Container\". Must match /^[a-z0-9_-]+$/.");
    assertThatThrownBy(() -> RocketFilesFileUploadedFunction.generateFunctionDefinition(config, "c", "Bad St"))
        .hasMessage("Invalid storageName: \"Bad St\". Must match /^[a-z0-9_-]+$/.");
    assertThatThrownBy(() -> RocketFilesFileUploadedFunction.generateFunctionDefinition(config, "", "s"))
        .hasMessage("Invalid containerName: \"\". Must match /^[a-z0-9_-]+$/.");
    assertThatThrownBy(() -> RocketFilesFileUploadedFunction.generateFunctionDefinition(config, "c\n", "s"))
        .hasMessage("Invalid containerName: \"c\n\". Must match /^[a-z0-9_-]+$/.");
    assertThat(RocketFilesFileUploadedFunction.generateFunctionDefinition(config, "my_container-2", "st_1").name())
        .isEqualTo("fileupload_st_1");
  }
}
