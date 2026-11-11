package com.boostercloud.framework.provider.local.services;

import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Hands GraphQL requests and read-model change notifications to the running application. Port of
 * {@code services/graphql-service.ts}.
 */
public class GraphQLService {

  private final Supplier<UserApp> userApp;

  /**
   * @param userApp the application ({@code new GraphQLService(userApp)})
   */
  public GraphQLService(UserApp userApp) {
    Objects.requireNonNull(userApp, "userApp");
    this.userApp = () -> userApp;
  }

  /**
   * @param userApp resolves the application at call time (the provider uses {@code UserAppRegistry::get}, because the
   *     provider can be created before {@code Booster.start} registers the application)
   */
  public GraphQLService(Supplier<UserApp> userApp) {
    this.userApp = Objects.requireNonNull(userApp, "userApp");
  }

  /**
   * @return a service that uses the application registered in {@link UserAppRegistry}
   */
  public static GraphQLService forRegisteredApp() {
    return new GraphQLService(UserAppRegistry::get);
  }

  /**
   * @return the application
   */
  public UserApp userApp() {
    return userApp.get();
  }

  /**
   * {@code handleGraphQLRequest(request)}: {@code userApp.boosterServeGraphQL(request)}.
   *
   * @param request a {@code LocalHttpRequest} or an {@code ExpressWebSocketMessage}
   * @return the provider response (an {@code APIResult})
   */
  public Object handleGraphQLRequest(Object request) {
    return userApp.get().boosterServeGraphQL(request);
  }

  /**
   * {@code handleNotificationSubscription(request)}: {@code userApp.boosterNotifySubscribers(request)}.
   *
   * @param request the changed read models
   */
  public void handleNotificationSubscription(List<ReadModelEnvelope> request) {
    userApp.get().boosterNotifySubscribers(request);
  }
}
