package com.boostercloud.framework.provider.local.services;

import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.UserAppRegistry;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Hands health requests to the running application. Port of {@code services/health-service.ts}.
 */
public class HealthService {

  private final Supplier<UserApp> userApp;

  /**
   * @param userApp the application ({@code new HealthService(userApp)})
   */
  public HealthService(UserApp userApp) {
    Objects.requireNonNull(userApp, "userApp");
    this.userApp = () -> userApp;
  }

  /**
   * @param userApp resolves the application at call time
   */
  public HealthService(Supplier<UserApp> userApp) {
    this.userApp = Objects.requireNonNull(userApp, "userApp");
  }

  /**
   * @return a service that uses the application registered in {@link UserAppRegistry}
   */
  public static HealthService forRegisteredApp() {
    return new HealthService(UserAppRegistry::get);
  }

  /**
   * {@code handleHealthRequest(request)}: {@code userApp.boosterHealth(request)}.
   *
   * @param request a {@code LocalHttpRequest}
   * @return the provider response (an {@code APIResult})
   */
  public Object handleHealthRequest(Object request) {
    return userApp.get().boosterHealth(request);
  }
}
