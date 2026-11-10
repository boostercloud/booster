package com.boostercloud.application.tester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientOptions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicationTesterTest {

  private static final ApplicationOutputs OUTPUTS = new ApplicationOutputs(
      "http://localhost:3000/graphql", "ws://localhost:65529/websocket", "http://localhost:3000/sensor/health/");

  @Test
  @DisplayName("exposes the provider's counters and queries as count and query")
  void countersAndQueries() {
    Counters counters = mock(Counters.class);
    Queries queries = mock(Queries.class);
    when(counters.events()).thenReturn(7L);
    when(counters.readModels("CartReadModel")).thenReturn(2L);
    when(queries.events("Cart-1-event")).thenReturn(List.of(Map.of("kind", "event")));

    ApplicationTester tester = new ApplicationTester(ProviderTestHelper.of(OUTPUTS, counters, queries));

    assertThat(tester.count()).isSameAs(counters);
    assertThat(tester.query()).isSameAs(queries);
    assertThat(tester.count().events()).isEqualTo(7);
    assertThat(tester.count().readModels("CartReadModel")).isEqualTo(2);
    assertThat(tester.query().events("Cart-1-event")).containsExactly(Map.of("kind", "event"));
    verify(counters).events();
  }

  @Test
  @DisplayName("captures counters and queries once, at construction")
  void capturesOnce() {
    Counters first = mock(Counters.class);
    Counters second = mock(Counters.class);
    Queries queries = mock(Queries.class);
    AtomicReference<Counters> current = new AtomicReference<>(first);
    ProviderTestHelper helper = new ProviderTestHelper() {
      @Override
      public ApplicationOutputs outputs() {
        return OUTPUTS;
      }

      @Override
      public Counters counters() {
        return current.get();
      }

      @Override
      public Queries queries() {
        return queries;
      }
    };
    ApplicationTester tester = new ApplicationTester(helper);
    current.set(second);
    assertThat(tester.count()).isSameAs(first);
  }

  @Test
  @DisplayName("wires the HTTP, token and GraphQL helpers to the provider outputs")
  void helpers() {
    ApplicationTester tester =
        new ApplicationTester(ProviderTestHelper.of(OUTPUTS, mock(Counters.class), mock(Queries.class)));

    assertThat(tester.http().getHealthUrl()).isEqualTo("http://localhost:3000/sensor/health/");
    assertThat(TokenHelper.decode(tester.token().forUser("a@b.com", "Admin")).payload())
        .containsEntry("booster:role", "Admin");
    GraphQLClient client = tester.graphql().client();
    assertThat(client.uri()).isEqualTo("http://localhost:3000/graphql");
    assertThat(client.options()).isEqualTo(GraphQLClientOptions.defaults());
  }

  @Test
  @DisplayName("accepts GraphQL client options and a custom token helper")
  void customisation() {
    GraphQLClientOptions options = GraphQLClientOptions.builder().addTypename(false).build();
    TokenHelper tokens = new TokenHelper();
    ApplicationTester tester = new ApplicationTester(
        ProviderTestHelper.of(OUTPUTS, mock(Counters.class), mock(Queries.class)), options, tokens);
    assertThat(tester.graphql().options()).isEqualTo(options);
    assertThat(tester.graphql().client().options().addTypename()).isFalse();
    assertThat(tester.token()).isSameAs(tokens);
  }

  @Test
  @DisplayName("ProviderTestHelper.of requires its three parts")
  void providerTestHelperOf() {
    Counters counters = mock(Counters.class);
    Queries queries = mock(Queries.class);
    ProviderTestHelper helper = ProviderTestHelper.of(OUTPUTS, counters, queries);
    assertThat(helper.outputs()).isEqualTo(OUTPUTS);
    assertThat(helper.counters()).isSameAs(counters);
    assertThat(helper.queries()).isSameAs(queries);
    assertThatThrownBy(() -> ProviderTestHelper.of(null, counters, queries)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("HttpHelper reads the health URL from the outputs on every call")
  void httpHelperReadsOutputsEachTime() {
    AtomicReference<ApplicationOutputs> outputs = new AtomicReference<>(OUTPUTS);
    ProviderTestHelper helper = new ProviderTestHelper() {
      @Override
      public ApplicationOutputs outputs() {
        return outputs.get();
      }

      @Override
      public Counters counters() {
        return mock(Counters.class);
      }

      @Override
      public Queries queries() {
        return mock(Queries.class);
      }
    };
    HttpHelper http = new HttpHelper(helper);
    assertThat(http.getHealthUrl()).isEqualTo("http://localhost:3000/sensor/health/");
    outputs.set(new ApplicationOutputs("g", "w", "http://other/sensor/health/"));
    assertThat(http.getHealthUrl()).isEqualTo("http://other/sensor/health/");
  }
}
