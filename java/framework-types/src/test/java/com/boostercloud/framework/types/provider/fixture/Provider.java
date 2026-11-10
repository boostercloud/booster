package com.boostercloud.framework.types.provider.fixture;

import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.lang.reflect.Proxy;
import java.util.List;

/** A fake provider package used by the provider loading tests. */
public final class Provider {
  /** Rockets received by the last call. */
  public static volatile List<RocketDescriptor> lastRockets;

  /** Number of calls. */
  public static volatile int calls;

  private Provider() {}

  /**
   * @param rockets the rockets
   * @return a provider whose methods all return {@code null}
   */
  public static ProviderLibrary provider(List<RocketDescriptor> rockets) {
    lastRockets = rockets;
    calls++;
    return (ProviderLibrary) Proxy.newProxyInstance(
        Provider.class.getClassLoader(), new Class<?>[] {ProviderLibrary.class}, (proxy, method, args) -> {
          if (method.getName().equals("toString")) {
            return "FixtureProvider";
          }
          return null;
        });
  }
}
