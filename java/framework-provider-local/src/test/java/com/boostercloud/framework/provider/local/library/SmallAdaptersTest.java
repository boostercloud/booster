package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;
import com.boostercloud.framework.types.errors.BoosterError;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.framework.types.rockets.Rockets;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@code api-adapter.ts}, {@code scheduled-adapter.ts}, {@code rocket-adapter.ts} and
 * {@code health-adapter.ts} (not covered by the TypeScript tests).
 */
class SmallAdaptersTest {

  private BoosterConfig config;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = mock(Logger.class);
  }

  @AfterEach
  void tearDown() {
    EnvironmentVariables.clearOverrides();
  }

  @Nested
  class Api {
    @Test
    void successesAlwaysHaveHeaders() {
      assertThat(ApiAdapter.requestSucceeded(null, null)).isEqualTo(new ApiAdapter.Success(null, Map.of()));
      assertThat(ApiAdapter.requestSucceeded(map("data", 1), Map.of("a", "b")).headers()).isEqualTo(Map.of("a", "b"));
      assertThat(ApiAdapter.requestSucceeded("x", null).status()).isEqualTo("success");
      assertThat(com.boostercloud.framework.types.BoosterJson.toJson(ApiAdapter.requestSucceeded(map("data", 1), null)))
          .isEqualTo("{\"status\":\"success\",\"result\":{\"data\":1},\"headers\":{}}");
    }

    @Test
    void failuresCarryTheStatusTitleAndReason() {
      assertThat(ApiAdapter.requestFailed(new IllegalStateException("x")))
          .isEqualTo(new ApiAdapter.Failure(500, "Illegal State Exception", "x"));
      assertThat(ApiAdapter.requestFailed(new NotFoundError("missing")))
          .isEqualTo(new ApiAdapter.Failure(404, "Not Found Error", "missing"));
      assertThat(ApiAdapter.requestFailed(new InvalidParameterError("bad")).code()).isEqualTo(400);
      assertThat(ApiAdapter.requestFailed(new BoosterError("custom")).code()).isEqualTo(500);
      assertThat(com.boostercloud.framework.types.BoosterJson.toJson(ApiAdapter.requestFailed(new NotFoundError("m"))))
          .isEqualTo("{\"status\":\"failure\",\"code\":404,\"title\":\"Not Found Error\",\"reason\":\"m\"}");
    }

    @Test
    void healthResultsCarryTheStatusCodeHeader() {
      assertThat(ApiAdapter.healthRequestResult(list(), true).headers()).isEqualTo(Map.of("status-code", 200));
      assertThat(ApiAdapter.healthRequestResult(list(), false).headers()).isEqualTo(Map.of("status-code", 503));
    }
  }

  @Nested
  class Scheduled {
    @Test
    void buildsTheEnvelope() {
      ScheduledCommandEnvelope envelope = ScheduledAdapter.rawScheduledInputToEnvelope(config, map("typeName", "Check"));
      assertThat(envelope.typeName()).isEqualTo("Check");
      assertThat(envelope.requestID()).isNotNull();
      assertThat(ScheduledAdapter.rawScheduledInputToEnvelope(config,
          new ScheduledAdapter.LocalScheduleCommandEnvelope("Other")).typeName()).isEqualTo("Other");
      verify(config.logger).debug("[Booster]|rawScheduledInputToEnvelope: ", "Received LocalScheduleCommand request: ",
          map("typeName", "Check"));
    }

    @Test
    void rejectsInputsWithoutTypeName() {
      assertThatThrownBy(() -> ScheduledAdapter.rawScheduledInputToEnvelope(config, map("typeName", "")))
          .hasMessage("typeName is not defined or empty, scheduled command envelope should have the structure "
              + "{typeName: string }, but you gave {\"typeName\":\"\"}");
      assertThatThrownBy(() -> ScheduledAdapter.rawScheduledInputToEnvelope(config, map()))
          .hasMessageEndingWith("but you gave {}");
    }
  }

  @Nested
  class Rocket {
    @Test
    void theRequestPropertyWinsOverTheEnvironment() {
      EnvironmentVariables.set(Rockets.ROCKET_FUNCTION_ID_ENV_VAR, "from-env");
      assertThat(RocketAdapter.rawRocketInputToEnvelope(config, map(Rockets.ROCKET_FUNCTION_ID_ENV_VAR, "from-request"))
          .rocketId()).isEqualTo("from-request");
      assertThat(RocketAdapter.rawRocketInputToEnvelope(config, map(Rockets.ROCKET_FUNCTION_ID_ENV_VAR, ""))
          .rocketId()).isEqualTo("");
      assertThat(RocketAdapter.rawRocketInputToEnvelope(config, map()).rocketId()).isEqualTo("from-env");
      EnvironmentVariables.unset(Rockets.ROCKET_FUNCTION_ID_ENV_VAR);
      assertThat(RocketAdapter.rawRocketInputToEnvelope(config, map()).rocketId()).isNull();
      assertThatThrownBy(() -> RocketAdapter.rawRocketInputToEnvelope(config, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class Health {
    @Test
    void componentPathsAreRelativeToTheHealthEndpoint() {
      HealthEnvelope root = HealthAdapter.rawRequestToSensorHealth(LocalHttpRequest.of("GET", "/", Map.of(), null));
      assertThat(root.componentPath()).isEmpty();
      assertThat(root.token()).isNull();
      assertThat(root.context().request().body()).isEqualTo(map());
      HealthEnvelope nested = HealthAdapter.rawRequestToSensorHealth(LocalHttpRequest.of("GET",
          "/booster/database/events?x=1", Map.of("Authorization", "Bearer t"), null));
      assertThat(nested.componentPath()).isEqualTo("booster/database/events?x=1");
      assertThat(nested.token()).isEqualTo("Bearer t");
      assertThat(HealthAdapter.rawRequestToSensorHealth(map("url", "", "headers", map())).componentPath()).isEmpty();
    }

    @Test
    void rocketsAreUpWhenConfigured() {
      config.rockets = List.of(
          new RocketDescriptor("a", map("rocketProviderPackage", "@boostercloud/rocket-webhook-local")),
          new RocketDescriptor("b", map("other", 1)),
          new RocketDescriptor("c", null),
          new RocketDescriptor("d", map("rocketProviderPackage", "com.example.rocket")));
      assertThat(HealthAdapter.areRocketFunctionsUp(config))
          .isEqualTo(Map.of("rocket-webhook", true, "com.example.rocket", true));
      config.rockets = null;
      assertThat(HealthAdapter.areRocketFunctionsUp(config)).isEmpty();
    }

    @Test
    void graphQLFunctionUrlAndLiveness() throws IOException {
      AtomicReference<String> body = new AtomicReference<>();
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/graphql", exchange -> {
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
      });
      server.start();
      try {
        InternalInfo.setBoosterLocalPort(server.getAddress().getPort());
        assertThat(HealthAdapter.graphqlFunctionUrl())
            .isEqualTo("http://localhost:" + server.getAddress().getPort() + "/graphql");
        assertThat(HealthAdapter.isGraphQLFunctionUp()).isTrue();
        assertThat(body.get()).isEqualTo("{\"query\":\"query { __typename }\"}");
      } finally {
        server.stop(0);
      }
      assertThat(HealthAdapter.isGraphQLFunctionUp()).isFalse();
    }

    @Test
    void databaseUrls() {
      assertThat(HealthAdapter.databaseUrl()).containsExactly(".booster/events.json", ".booster/read_models.json");
    }
  }
}
