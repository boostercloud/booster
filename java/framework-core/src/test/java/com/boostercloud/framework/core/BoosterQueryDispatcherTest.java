package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Decorators;
import com.boostercloud.framework.core.decorators.GlobalErrorHandler;
import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.core.decorators.Role;
import com.boostercloud.framework.core.services.graphql.GraphQLResolverContext;
import com.boostercloud.framework.types.concepts.QueryBeforeFunction;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.errors.NotFoundError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code BoosterQueryDispatcher} (no TypeScript test; behaviour from the source, spec 05 §3.2). */
class BoosterQueryDispatcherTest extends BoosterTestSupport {

  static final AtomicReference<QueryInfo> QUERY_INFO = new AtomicReference<>();
  static final AtomicReference<RuntimeException> FAILURE = new AtomicReference<>();

  public static class Doubler implements QueryBeforeFunction {
    @Override
    public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
      return Map.of("quantity", ((Number) input.get("quantity")).doubleValue() * 2);
    }
  }

  @Query(authorize = All.class, before = Doubler.class)
  public record TotalQuantity(double quantity) {
    public static double handle(TotalQuantity query, QueryInfo queryInfo) {
      QUERY_INFO.set(queryInfo);
      queryInfo.responseHeaders().put("X-Query", "yes");
      if (FAILURE.get() != null) {
        throw FAILURE.get();
      }
      return query.quantity();
    }
  }

  @Role
  public static class Admin {}

  @Query(authorize = Admin.class)
  public record AdminQuery(String id) {
    public static String handle(AdminQuery query) {
      return query.id();
    }
  }

  @GlobalErrorHandler
  public static class Swallow {
    public static Throwable onQueryHandlerError(Throwable error, QueryEnvelope query) {
      return null;
    }
  }

  @BeforeEach
  void setUp() {
    QUERY_INFO.set(null);
    FAILURE.set(null);
    Decorators.registerClass(TotalQuantity.class);
    Decorators.registerClass(AdminQuery.class);
  }

  private static QueryEnvelope envelope(String typeName, int version, Map<String, Object> value, List<String> roles) {
    return QueryEnvelope.builder().requestID(UUID.of("r1")).typeName(typeName).version(version).value(value)
        .currentUser(UserEnvelope.builder().username("u").roles(roles).build())
        .context(ContextEnvelope.of(Map.of(), Map.of(), null)).build();
  }

  private static GraphQLResolverContext context(Map<String, String> headers) {
    return new GraphQLResolverContext(null, headers, null, UUID.of("r1"), null, true, null, null);
  }

  @Test
  void validatesTheVersionAndTheHandler() {
    BoosterQueryDispatcher dispatcher = new BoosterQueryDispatcher(Booster.config());

    assertThatThrownBy(() -> dispatcher.dispatchQuery(envelope("TotalQuantity", 0, Map.of(), List.of()), context(new LinkedHashMap<>())))
        .isInstanceOf(InvalidParameterError.class).hasMessage("The required query \"version\" was not present");
    assertThatThrownBy(() -> dispatcher.dispatchQuery(envelope("Nope", 1, Map.of(), List.of()), context(new LinkedHashMap<>())))
        .isInstanceOf(NotFoundError.class).hasMessage("Could not find a proper handler for Nope");
  }

  @Test
  void authorizesTheQuery() {
    BoosterQueryDispatcher dispatcher = new BoosterQueryDispatcher(Booster.config());

    assertThatThrownBy(() -> dispatcher.dispatchQuery(envelope("AdminQuery", 1, Map.of("id", "a"), List.of("User")),
        context(new LinkedHashMap<>()))).isInstanceOf(NotAuthorizedError.class);
    assertThat(dispatcher.dispatchQuery(envelope("AdminQuery", 1, Map.of("id", "a"), List.of("Admin")),
        context(new LinkedHashMap<>()))).isEqualTo("a");
  }

  @Test
  void runsTheHooksAndPassesTheQueryInfo() {
    Map<String, String> headers = new LinkedHashMap<>();
    QueryEnvelope envelope = envelope("TotalQuantity", 1, Map.of("quantity", 2), List.of());

    Object result = new BoosterQueryDispatcher(Booster.config()).dispatchQuery(envelope, context(headers));

    assertThat(result).isEqualTo(4.0);
    assertThat(QUERY_INFO.get()).isEqualTo(new QueryInfo(UUID.of("r1"), headers, envelope.currentUser(), envelope.context()));
    assertThat(headers).containsEntry("X-Query", "yes");
  }

  @Test
  void routesErrorsToTheGlobalErrorHandler() {
    FAILURE.set(new IllegalStateException("query failed"));
    QueryEnvelope envelope = envelope("TotalQuantity", 1, Map.of("quantity", 2), List.of());

    assertThatThrownBy(() -> new BoosterQueryDispatcher(Booster.config()).dispatchQuery(envelope, context(new LinkedHashMap<>())))
        .isSameAs(FAILURE.get());

    Decorators.registerClass(Swallow.class);
    assertThat(new BoosterQueryDispatcher(Booster.config()).dispatchQuery(envelope, context(new LinkedHashMap<>()))).isNull();
  }
}
