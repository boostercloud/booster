package com.boostercloud.framework.core.integration.inmemory;

import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.List;

/**
 * The entry class the core loads for {@code config.providerPackage =
 * "com.boostercloud.framework.core.integration.inmemory"} (ARCHITECTURE.md §6).
 */
public final class Provider {

  private Provider() {}

  /**
   * @param rockets the application's rockets (ignored)
   * @return the in-memory provider of the running test
   */
  public static ProviderLibrary provider(List<RocketDescriptor> rockets) {
    return InMemoryProvider.current();
  }
}
