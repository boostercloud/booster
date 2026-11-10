package com.boostercloud.rocket.fileuploads.local.infrastructure;

import com.boostercloud.framework.provider.local.infrastructure.InfrastructureRocketMetadata;
import com.boostercloud.framework.provider.local.infrastructure.LocalInfrastructureRocket;
import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The local infrastructure rocket ({@code { mountStack: Infra.mountStack.bind(Infra, configuration) }} of
 * {@code src/index.ts}): mounts the file routes and starts the file watchers ({@link Infra}). Java-only:
 * {@link #unmountStack()} stops the watchers when the local server stops.
 */
public final class LocalRocketFiles implements LocalInfrastructureRocket {

  private final RocketFilesConfiguration configuration;
  private final List<FsWatch> watchers = new ArrayList<>();

  /**
   * @param configuration the rocket parameters
   */
  public LocalRocketFiles(RocketFilesConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
  }

  /**
   * @return the rocket parameters
   */
  public RocketFilesConfiguration configuration() {
    return configuration;
  }

  @Override
  public void mountStack(BoosterConfig config, Router router, InfrastructureRocketMetadata metadata) {
    List<FsWatch> started = Infra.mountStack(configuration, config, router, metadata);
    synchronized (watchers) {
      watchers.addAll(started);
    }
  }

  @Override
  public void unmountStack() {
    List<FsWatch> toClose;
    synchronized (watchers) {
      toClose = new ArrayList<>(watchers);
      watchers.clear();
    }
    toClose.forEach(FsWatch::close);
  }
}
