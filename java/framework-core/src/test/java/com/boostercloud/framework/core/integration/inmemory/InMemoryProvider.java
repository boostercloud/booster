package com.boostercloud.framework.core.integration.inmemory;

import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.ApiResponse;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.GraphQLHttpRequest;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.GraphQLResponse;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.GraphQLSocketRequest;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.HealthRequest;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.RocketRequest;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.ScheduledTrigger;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.TypeLevel;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.EventType;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeError;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError;
import com.boostercloud.framework.types.envelope.GraphQLRequestValue;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;
import com.boostercloud.framework.types.errors.BoosterErrors;
import com.boostercloud.framework.types.graphql.GraphQLClientMessage;
import com.boostercloud.framework.types.provider.ProviderAPIHandling;
import com.boostercloud.framework.types.provider.ProviderGraphQLLibrary;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderRocketLibrary;
import com.boostercloud.framework.types.provider.ProviderSensorLibrary;
import com.boostercloud.framework.types.provider.ScheduledCommandsLibrary;
import com.boostercloud.framework.types.rockets.RocketEnvelope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A complete {@link ProviderLibrary} kept in memory, used by the core integration tests to drive the real framework
 * end to end without a database or a server: an event store with change-feed dispatching, a read model store with
 * optimistic concurrency and subscription notifications, websocket connections, the GraphQL, scheduled, rocket and
 * health adapters. Requests and responses are the records of {@link InMemoryRequests}.
 *
 * <p>The provider of the current test is {@link #current()}; {@link #reset()} replaces it. The core loads it through
 * {@code config.providerPackage} ({@link Provider#provider(List)}).
 */
public final class InMemoryProvider implements ProviderLibrary {

  private static volatile InMemoryProvider current = new InMemoryProvider();

  private final InMemoryEventStore events = new InMemoryEventStore();
  private final InMemoryReadModelStore readModels = new InMemoryReadModelStore();
  private final InMemoryConnections connections = new InMemoryConnections();
  private volatile boolean databaseUp = true;

  /**
   * @return the provider of the running test
   */
  public static InMemoryProvider current() {
    return current;
  }

  /**
   * Starts from an empty provider.
   *
   * @return the new provider
   */
  public static InMemoryProvider reset() {
    current = new InMemoryProvider();
    return current;
  }

  /**
   * @param up whether the databases report themselves as up to the health sensor
   */
  public void databaseUp(boolean up) {
    databaseUp = up;
  }

  @Override
  public InMemoryEventStore events() {
    return events;
  }

  @Override
  public InMemoryReadModelStore readModels() {
    return readModels;
  }

  @Override
  public InMemoryConnections connections() {
    return connections;
  }

  @Override
  public ProviderGraphQLLibrary graphQL() {
    return GRAPHQL;
  }

  @Override
  public ProviderAPIHandling api() {
    return API;
  }

  @Override
  public ScheduledCommandsLibrary scheduled() {
    return (config, rawMessage) -> ScheduledCommandEnvelope.builder()
        .requestID(UUID.generate())
        .typeName(((ScheduledTrigger) rawMessage).typeName())
        .build();
  }

  @Override
  public ProviderInfrastructure infrastructure() {
    return new ProviderInfrastructure() {};
  }

  @Override
  public ProviderRocketLibrary rockets() {
    return (config, request) -> new RocketEnvelope(((RocketRequest) request).rocketId());
  }

  @Override
  public ProviderSensorLibrary sensor() {
    return new Sensor();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // GraphQL
  // ---------------------------------------------------------------------------------------------------------------

  private static final ProviderGraphQLLibrary GRAPHQL = new ProviderGraphQLLibrary() {
    @Override
    public GraphQLRequestEnvelopeOrError rawToEnvelope(BoosterConfig config, Object rawGraphQLRequest) {
      UUID requestID = UUID.generate();
      return switch (rawGraphQLRequest) {
        case GraphQLHttpRequest http -> {
          try {
            GraphQLRequestValue value = http.body() == null ? null
                : BoosterJson.convert(http.body(), GraphQLOperation.class);
            yield GraphQLRequestEnvelope.builder()
                .requestID(requestID)
                .eventType(EventType.MESSAGE)
                .token(header(http.headers(), "Authorization"))
                .value(value)
                .context(new ContextEnvelope(new ContextEnvelope.Request(http.headers(), http.body()), http))
                .build();
          } catch (RuntimeException e) {
            yield new GraphQLRequestEnvelopeError(requestID, EventType.MESSAGE, null, e);
          }
        }
        case GraphQLSocketRequest socket -> {
          try {
            GraphQLRequestValue value = socket.data() == null ? null
                : BoosterJson.convert(socket.data(), GraphQLClientMessage.class);
            yield GraphQLRequestEnvelope.builder()
                .requestID(requestID)
                .eventType(socket.eventType())
                .connectionID(socket.connectionID())
                .token(header(socket.headers(), "Authorization"))
                .value(value)
                .context(new ContextEnvelope(new ContextEnvelope.Request(socket.headers(), socket.data()), socket))
                .build();
          } catch (RuntimeException e) {
            yield new GraphQLRequestEnvelopeError(requestID, socket.eventType(), socket.connectionID(), e);
          }
        }
        default -> throw new IllegalArgumentException("Unexpected GraphQL request " + rawGraphQLRequest);
      };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object handleResult(Object result, Map<String, String> headers) {
      return new GraphQLResponse(result == null ? null : (Map<String, Object>) result,
          headers == null ? null : new LinkedHashMap<>(headers));
    }
  };

  private static String header(Map<String, String> headers, String name) {
    if (headers == null) {
      return null;
    }
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(name)) {
        return entry.getValue();
      }
    }
    return null;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // API, sensor
  // ---------------------------------------------------------------------------------------------------------------

  private static final ProviderAPIHandling API = new ProviderAPIHandling() {
    @Override
    public Object requestSucceeded(Object body, Map<String, Object> headers) {
      return new ApiResponse(200, body, headers);
    }

    @Override
    public Object requestFailed(Throwable error) {
      Map<String, Object> body = new LinkedHashMap<>();
      // Plain TypeScript Errors are IllegalStateException/IllegalArgumentException in Java: their title is "Error"
      boolean plainError = error instanceof IllegalStateException || error instanceof IllegalArgumentException;
      body.put("title", plainError ? "Error" : TypeLevel.toClassTitle(error));
      body.put("reason", error.getMessage());
      return new ApiResponse(BoosterErrors.httpStatusCodeFor(error), body, null);
    }

    @Override
    public Object healthRequestResult(Object body, boolean isHealthy) {
      return new ApiResponse(isHealthy ? 200 : 503, body, null);
    }
  };

  private final class Sensor implements ProviderSensorLibrary {
    @Override
    public Object databaseEventsHealthDetails(BoosterConfig config) {
      return Map.of("file", "memory://events", "count", events.events().size());
    }

    @Override
    public Object databaseReadModelsHealthDetails(BoosterConfig config) {
      return Map.of("file", "memory://read-models");
    }

    @Override
    public boolean isDatabaseEventUp(BoosterConfig config) {
      return databaseUp;
    }

    @Override
    public boolean areDatabaseReadModelsUp(BoosterConfig config) {
      return databaseUp;
    }

    @Override
    public List<String> databaseUrls(BoosterConfig config) {
      return List.of("memory://events", "memory://read-models");
    }

    @Override
    public boolean isGraphQLFunctionUp(BoosterConfig config) {
      return true;
    }

    @Override
    public String graphQLFunctionUrl(BoosterConfig config) {
      return "memory://graphql";
    }

    @Override
    public HealthEnvelope rawRequestToHealthEnvelope(Object rawRequest) {
      HealthRequest request = (HealthRequest) rawRequest;
      return HealthEnvelope.builder()
          .requestID(UUID.generate())
          .token(request.token())
          .componentPath(request.componentPath())
          .build();
    }

    @Override
    public Map<String, Boolean> areRocketFunctionsUp(BoosterConfig config) {
      return Map.of();
    }
  }
}
