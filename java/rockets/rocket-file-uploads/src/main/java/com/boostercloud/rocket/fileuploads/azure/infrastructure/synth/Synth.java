package com.boostercloud.rocket.fileuploads.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.azure.storage.BlobStorageContext;
import com.boostercloud.rocket.fileuploads.types.Constants;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConfiguration;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Port of {@code synth/synth.ts}.
 */
public final class Synth {

  private Synth() {}

  /** One storage of the rocket: the account resource and its name. */
  private record RocketStorage(TerraformResource resource, String name) {}

  /**
   * {@code Synth.mountStack(configuration, config, applicationSynthStack, utils)}, in TypeScript's order:
   *
   * <ol>
   *   <li>the application's function app gets a system-assigned identity ({@code addOverride('identity', ...)});</li>
   *   <li>per user configuration, a storage account ({@link TerraformStorageAccount}) and a private container
   *       ({@link TerraformStorageContainer}), and the identity-based connection setting
   *       {@code <account>__serviceUri = https://<account>.blob.core.windows.net} the blob triggers use;</li>
   *   <li>the rocket's function app ({@link TerraformFunctionApp});</li>
   *   <li>per storage account, the two role assignments ({@link TerraformRoleAssignment}) for the rocket's function app,
   *       then for the application's one (which creates the presigned URLs).</li>
   * </ol>
   *
   * Every resource is also added to {@code applicationSynthStack.rocketStack} when the bag has one.
   *
   * @param configuration the rocket parameters
   * @param config the application configuration
   * @param applicationSynthStack the stack bag
   * @param utils the rocket utilities
   * @return the stack bag
   */
  public static ApplicationSynthStack mountStack(RocketFilesConfiguration configuration, BoosterConfig config,
      ApplicationSynthStack applicationSynthStack, RocketUtils utils) {
    List<TerraformResource> rocketStack = applicationSynthStack.rocketStack != null
        ? applicationSynthStack.rocketStack : new ArrayList<>();
    TerraformResource mainFunctionApp = Objects.requireNonNull(applicationSynthStack.functionApp,
        "Cannot read properties of undefined (reading 'addOverride')");
    mainFunctionApp.addOverride("identity", Map.of("type", "SystemAssigned"));

    Map<String, String> accountsConnections = new LinkedHashMap<>();
    List<RocketStorage> rocketStorages = new ArrayList<>();
    for (RocketFilesUserConfiguration userConfiguration : configuration.userConfiguration()) {
      TerraformResource rocketStorage = TerraformStorageAccount.build(applicationSynthStack, utils, config,
          userConfiguration.storageName());
      String storageAccountName = Constants.azureStorageName(config.appName, config.environmentName,
          userConfiguration.storageName());
      rocketStorages.add(new RocketStorage(rocketStorage, storageAccountName));

      TerraformResource blobContainer = TerraformStorageContainer.build(applicationSynthStack, rocketStorage,
          storageAccountName, userConfiguration.containerName(), utils);

      String connectionName = storageAccountName + "__serviceUri";
      accountsConnections.put(connectionName,
          "https://" + rocketStorage.ref("name") + "." + BlobStorageContext.STORAGE_ACCOUNT_ENDPOINT);

      rocketStack.add(blobContainer);
      rocketStack.add(rocketStorage);
    }

    TerraformResource rocketFunctionApp = TerraformFunctionApp.build(applicationSynthStack, config, utils,
        accountsConnections);

    for (RocketStorage storage : rocketStorages) {
      List<TerraformResource> rocketRoles = TerraformRoleAssignment.build(applicationSynthStack, utils,
          storage.resource(), storage.name(), rocketFunctionApp);
      List<TerraformResource> appRoles = TerraformRoleAssignment.build(applicationSynthStack, utils,
          storage.resource(), storage.name(), mainFunctionApp);
      rocketStack.addAll(rocketRoles);
      rocketStack.addAll(appRoles);
    }
    rocketStack.add(rocketFunctionApp);
    return applicationSynthStack;
  }
}
