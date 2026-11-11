package com.boostercloud.rocket.fileuploads.local.infrastructure;

import com.boostercloud.framework.provider.local.infrastructure.InfrastructureRocketMetadata;
import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.rocket.fileuploads.local.LocalStorage;
import com.boostercloud.rocket.fileuploads.local.infrastructure.controllers.FileController;
import com.boostercloud.rocket.fileuploads.types.Constants;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConfiguration;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code infra.ts}.
 */
public final class Infra {

  /** The port used when the server does not say ({@code infrastructureRocketMetadata?.port ?? 3000}). */
  public static final int DEFAULT_PORT = 3000;

  private Infra() {}

  /**
   * {@code Infra.mountStack(rocketFilesConfiguration, config, router, infrastructureRocketMetadata?)}: sets
   * {@code ROCKET_FILES_LOCAL_PORT} to the server port (an in-process {@link EnvironmentVariables} override, read by
   * the presigned URLs), then for every user configuration starts a file watcher on
   * {@code <project>/<storage>/<container>} and mounts, for every directory pattern, a {@link FileController} at
   * {@code /<storage>/<container>} (in configuration order, then directory order: the first matching route wins).
   * Java-only: the controllers notify their uploads to the storage's watcher themselves
   * ({@link FsWatch#changed(java.nio.file.Path)}).
   *
   * @param rocketFilesConfiguration the rocket parameters
   * @param config the application configuration
   * @param router the router of the local server
   * @param infrastructureRocketMetadata the server port, or {@code null}
   * @return the started watchers (Java-only, so they can be stopped)
   */
  public static List<FsWatch> mountStack(RocketFilesConfiguration rocketFilesConfiguration, BoosterConfig config,
      Router router, InfrastructureRocketMetadata infrastructureRocketMetadata) {
    int port = infrastructureRocketMetadata != null ? infrastructureRocketMetadata.port() : DEFAULT_PORT;
    EnvironmentVariables.set(Constants.LOCAL_PORT_ENV, Integer.toString(port));
    Path projectDirectory = LocalStorage.projectDirectory(config);
    List<FsWatch> watchers = new ArrayList<>();
    try {
      for (RocketFilesUserConfiguration userConfiguration : rocketFilesConfiguration.userConfiguration()) {
        FsWatch watch = FsWatch.fsWatch(config, projectDirectory, userConfiguration.storageName(),
            userConfiguration.containerName(), port, userConfiguration.directories());
        watchers.add(watch);
        for (String directory : userConfiguration.directories()) {
          router.use("/" + userConfiguration.storageName() + "/" + userConfiguration.containerName(),
              new FileController(projectDirectory, userConfiguration.storageName(), userConfiguration.containerName(),
                  directory, watch).router());
        }
      }
    } catch (RuntimeException e) {
      watchers.forEach(FsWatch::close);
      throw e;
    }
    return watchers;
  }
}
