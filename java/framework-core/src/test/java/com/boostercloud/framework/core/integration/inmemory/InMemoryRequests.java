package com.boostercloud.framework.core.integration.inmemory;

import com.boostercloud.framework.types.envelope.EventType;
import java.util.Map;

/**
 * The raw requests and responses the in-memory provider exchanges with the core (the role HTTP requests, websocket
 * frames, timers and Azure function contexts play for the real providers).
 */
public final class InMemoryRequests {

  private InMemoryRequests() {}

  /**
   * A GraphQL request received over HTTP.
   *
   * @param headers the request headers ({@code Authorization} carries the token)
   * @param body the JSON body ({@code {query, variables, operationName}})
   */
  public record GraphQLHttpRequest(Map<String, String> headers, Object body) {}

  /**
   * A websocket event of the GraphQL endpoint.
   *
   * @param connectionID the connection
   * @param eventType {@code CONNECT}, {@code MESSAGE} or {@code DISCONNECT}
   * @param headers the headers of the upgrade request
   * @param data the parsed client message (a {@code subscriptions-transport-ws} message), for {@code MESSAGE}
   */
  public record GraphQLSocketRequest(String connectionID, EventType eventType, Map<String, String> headers,
      Object data) {}

  /**
   * What {@code provider.graphQL().handleResult} returns.
   *
   * @param result the result map ({@code {errors?, data?}}), or {@code null}
   * @param headers the response headers, or {@code null}
   */
  public record GraphQLResponse(Map<String, Object> result, Map<String, String> headers) {}

  /**
   * What the API handling methods return.
   *
   * @param status the HTTP status
   * @param body the body
   * @param headers the headers, or {@code null}
   */
  public record ApiResponse(int status, Object body, Map<String, Object> headers) {}

  /**
   * A request to the health endpoint.
   *
   * @param token the bearer token, or {@code null}
   * @param componentPath the requested component ({@code ""} for every root indicator)
   */
  public record HealthRequest(String token, String componentPath) {}

  /**
   * The timer that triggers a scheduled command.
   *
   * @param typeName the scheduled command class name
   */
  public record ScheduledTrigger(String typeName) {}

  /**
   * A request to a rocket function.
   *
   * @param rocketId the id the function was registered with
   * @param body the request body
   */
  public record RocketRequest(String rocketId, Object body) {}
}
