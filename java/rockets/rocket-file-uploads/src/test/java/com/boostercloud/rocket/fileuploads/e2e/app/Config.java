package com.boostercloud.rocket.fileuploads.e2e.app;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterConfigurator;
import com.boostercloud.rocket.fileuploads.core.BoosterRocketFiles;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.util.List;

/** The rocket configured as in the TypeScript README: two storages on the local provider. */
public class Config implements BoosterConfigurator {

  /** The default storage (upper case on purpose: the rocket lower-cases it). */
  public static final RocketFilesUserConfiguration CLIENT =
      new RocketFilesUserConfiguration("ClientST", "rocketfiles", List.of("client1", "client2"));
  /** The CMS storage. */
  public static final RocketFilesUserConfiguration CMS =
      new RocketFilesUserConfiguration("cmsst", "rocketfiles", List.of("cms1", "cms2"));

  @Override
  public void configure() {
    Booster.configure("local", config -> {
      config.appName = "files-e2e";
      config.providerPackage = "com.boostercloud.framework.provider.local";
      config.rockets = List.of(new BoosterRocketFiles(config, List.of(CLIENT, CMS)).rocketForLocal());
    });
    Booster.configure("production", config -> {
      config.appName = "files-e2e";
      config.providerPackage = "com.boostercloud.framework.provider.azure";
      config.rockets = List.of(new BoosterRocketFiles(config, List.of(CLIENT, CMS)).rocketForAzure());
    });
  }
}
