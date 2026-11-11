package com.boostercloud.framework.provider.azure.infrastructure;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.infrastructure.helper.FunctionZip;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketBuilder;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketZipResource;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformApp;
import com.boostercloud.framework.provider.azure.infrastructure.types.ZipResource;
import com.boostercloud.framework.types.BoosterConfig;
import java.nio.file.Path;
import java.util.List;

/**
 * Port of {@code application-builder.ts}: one build of the application, strictly sequential:
 *
 * <ol>
 *   <li>with subscriptions, the WebPubSub bootstrap package ({@code baseWebPubSubBinding.zip});
 *   <li>the stack ({@link AzureStack}: every base resource);
 *   <li>the rockets' {@code mountStack};
 *   <li>the main function app settings (defaults and rocket settings);
 *   <li>the Terraform JSON ({@code cdktf.out/stacks/<appName><environmentName>/cdk.tf.json});
 *   <li>the main package ({@code functionApp.zip}) and, with event streaming, the consumer package
 *       ({@code consumerFunctionApp.zip});
 *   <li>the rocket packages.
 * </ol>
 *
 * TypeScript first writes the {@code cdktf.json} of {@code cdktn-cli}; Java synthesizes and runs Terraform itself, so
 * there is no such file.
 */
public final class ApplicationBuilder {

  /** The main package file name. */
  public static final String FUNCTION_APP_ZIP = "functionApp.zip";

  /** The consumer package file name. */
  public static final String CONSUMER_FUNCTION_APP_ZIP = "consumerFunctionApp.zip";

  private final BoosterConfig config;
  private final List<InfrastructureRocket> rockets;
  private final InfrastructureContext context;

  /**
   * @param config the configuration
   * @param rockets the loaded rockets, or {@code null}
   * @param context the infrastructure context
   */
  public ApplicationBuilder(BoosterConfig config, List<InfrastructureRocket> rockets, InfrastructureContext context) {
    this.config = config;
    this.rockets = rockets;
    this.context = context;
  }

  /**
   * {@code buildApplication()}.
   *
   * @return the build
   */
  public ApplicationBuild buildApplication() {
    FunctionZip functionZip = new FunctionZip(config, context);
    TerraformApp app = new TerraformApp(context.projectRoot());
    String webPubSubBaseFile = null;
    if (config.enableSubscriptions) {
      webPubSubBaseFile = functionZip.copyBaseZip().toString();
    }
    AzureStack azureStack = synthApplication(app, webPubSubBaseFile);
    RocketBuilder rocketBuilder = new RocketBuilder(config, azureStack.applicationStack, rockets, functionZip);
    rocketBuilder.synthRocket();
    // add rocket-related env vars to main function app settings
    azureStack.addAppSettingsToFunctionApp(rockets);
    app.synth();
    Path stackDirectory = app.stackDirectory(azureStack);

    ZipResource zipResource = functionZip.copyZip(FUNCTION_APP_ZIP, null);
    ZipResource consumerZipResource = null;
    if (config.eventStreamConfiguration.enabled()) {
      consumerZipResource = functionZip.copyConsumerZip(CONSUMER_FUNCTION_APP_ZIP, null);
    }
    List<RocketZipResource> rocketsZipResources = rocketBuilder.mountRocketsZipResources();
    return new ApplicationBuild(azureStack, stackDirectory, zipResource, consumerZipResource, rocketsZipResources);
  }

  private AzureStack synthApplication(TerraformApp app, String destinationFile) {
    Loggers.getLogger(config, "ApplicationBuilder#synthApplication").info("Synth...");
    return new AzureStack(app, config.appName + config.environmentName, config, destinationFile,
        context.configuration(), context.applicationClass());
  }
}
