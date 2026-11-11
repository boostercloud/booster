package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.core.integration.app.App;
import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.core.integration.inmemory.InMemoryProvider;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.ApiResponse;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.GraphQLHttpRequest;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.GraphQLResponse;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.GraphQLSocketRequest;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.HealthRequest;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.framework.types.envelope.EventType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base of the core integration tests: every test boots the fixture application ({@code integration.app}) on a fresh
 * {@link InMemoryProvider} and talks to it only through the entry points a provider calls ({@link UserApp}), with the
 * request shapes of {@link com.boostercloud.framework.core.integration.inmemory.InMemoryRequests}. Everything a request
 * triggers (events, snapshots, projections, notifications) has finished when the entry point returns.
 */
public abstract class IntegrationTestSupport extends BoosterTestSupport {

  /** The provider of the running test. */
  protected InMemoryProvider provider;
  /** The configuration of the started application. */
  protected BoosterConfig config;
  /** The application, as the provider sees it. */
  protected UserApp app;

  @BeforeEach
  void startApplication() {
    provider = InMemoryProvider.reset();
    AppState.reset();
    RecordingLogger.INSTANCE.clear();
    Booster.start(App.class);
    config = Booster.config();
    app = UserAppRegistry.get();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // GraphQL over HTTP
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * @param query a GraphQL document
   * @param variables its variables, or {@code null}
   * @param token a bearer token, or {@code null}
   * @return the provider response
   */
  protected GraphQLResponse post(String query, Map<String, Object> variables, String token) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    if (token != null) {
      headers.put("Authorization", "Bearer " + token);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("query", query);
    if (variables != null) {
      body.put("variables", variables);
    }
    return (GraphQLResponse) app.boosterServeGraphQL(new GraphQLHttpRequest(headers, body));
  }

  /**
   * @param query a GraphQL document, run anonymously
   * @return the result map
   */
  protected Map<String, Object> graphQL(String query) {
    return post(query, null, null).result();
  }

  /**
   * @param query a GraphQL document
   * @param token a bearer token, or {@code null}
   * @return the result map
   */
  protected Map<String, Object> graphQL(String query, String token) {
    return post(query, null, token).result();
  }

  /**
   * @param query a GraphQL document
   * @param variables its variables
   * @param token a bearer token, or {@code null}
   * @return the result map
   */
  protected Map<String, Object> graphQL(String query, Map<String, Object> variables, String token) {
    return post(query, variables, token).result();
  }

  /**
   * @param result a result map
   * @return its {@code data}, after checking there are no errors
   */
  @SuppressWarnings("unchecked")
  protected static Map<String, Object> data(Map<String, Object> result) {
    assertThat(result).doesNotContainKey("errors");
    return (Map<String, Object>) result.get("data");
  }

  /**
   * @param result a result map
   * @return its {@code errors}
   */
  @SuppressWarnings("unchecked")
  protected static List<Map<String, Object>> errors(Map<String, Object> result) {
    assertThat(result).containsKey("errors");
    return (List<Map<String, Object>>) result.get("errors");
  }

  /**
   * @param result a result map
   * @return the message of its only error
   */
  protected static String errorMessage(Map<String, Object> result) {
    List<Map<String, Object>> errors = errors(result);
    assertThat(errors).hasSize(1);
    return (String) errors.getFirst().get("message");
  }

  // ---------------------------------------------------------------------------------------------------------------
  // GraphQL over websockets
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * @param connectionID the connection
   * @return the provider response to the {@code CONNECT} event
   */
  protected GraphQLResponse socketConnect(String connectionID) {
    return (GraphQLResponse) app.boosterServeGraphQL(
        new GraphQLSocketRequest(connectionID, EventType.CONNECT, Map.of(), null));
  }

  /**
   * @param connectionID the connection
   * @param message a client message ({@code {type, id?, payload?}})
   * @return the provider response to the {@code MESSAGE} event
   */
  protected GraphQLResponse socketSend(String connectionID, Map<String, Object> message) {
    return (GraphQLResponse) app.boosterServeGraphQL(
        new GraphQLSocketRequest(connectionID, EventType.MESSAGE, Map.of(), message));
  }

  /**
   * @param connectionID the connection
   * @return the provider response to the {@code DISCONNECT} event
   */
  protected GraphQLResponse socketDisconnect(String connectionID) {
    return (GraphQLResponse) app.boosterServeGraphQL(
        new GraphQLSocketRequest(connectionID, EventType.DISCONNECT, Map.of(), null));
  }

  /**
   * @param connectionID the connection
   * @return the messages the connection received, oldest first
   */
  protected List<Map<String, Object>> socketMessages(String connectionID) {
    return provider.connections().messages(connectionID);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Other entry points
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * @param token a bearer token, or {@code null}
   * @param componentPath the component, {@code ""} for all
   * @return the provider response of the health endpoint
   */
  protected ApiResponse health(String token, String componentPath) {
    return (ApiResponse) app.boosterHealth(new HealthRequest(token, componentPath));
  }

  /**
   * @param pairs alternating keys and values
   * @return an insertion-ordered map (values may be {@code null})
   */
  protected static Map<String, Object> map(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  /**
   * @param value a value
   * @param path property names
   * @return the value at the path of nested maps
   */
  @SuppressWarnings("unchecked")
  protected static Object at(Object value, String... path) {
    Object current = value;
    for (String segment : path) {
      current = ((Map<String, Object>) current).get(segment);
    }
    return current;
  }

  /**
   * @param value a value
   * @param path property names
   * @return the map at the path of nested maps
   */
  @SuppressWarnings("unchecked")
  protected static Map<String, Object> mapAt(Object value, String... path) {
    return (Map<String, Object>) at(value, path);
  }

  /**
   * @param value a value
   * @param path property names
   * @return the list at the path of nested maps
   */
  @SuppressWarnings("unchecked")
  protected static List<Map<String, Object>> listAt(Object value, String... path) {
    return (List<Map<String, Object>>) at(value, path);
  }
}
