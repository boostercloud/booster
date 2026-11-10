package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.EventStreamAuthorizer;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchRequest;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Port of {@code test/booster-events-reader.test.ts}, plus by-type searches, custom authorizers and raw parameters. */
class BoosterEventsReaderTest extends BoosterTestSupport {

  public record TestEntity(UUID id) {}

  public record NonRegisteredTestEntity(UUID id) {}

  public record TestEvent(UUID id) {
    public UUID entityID() {
      return id;
    }

    public String getPrefixedId(String prefix) {
      return prefix + "-" + id;
    }
  }

  public record TestEventReducedByNonRegisteredEntity() {}

  public static final class CanReadEventsRole {}

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private BoosterEventsReader eventsReader;
  private List<EventSearchResponse> searchResult;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    EventStreamAuthorizer eventStreamAuthorizer = BoosterAuthorizer.authorizeRoles(List.of(CanReadEventsRole.class));
    Booster.configureCurrentEnv(currentConfig -> {
      ProviderLibrary provider = mock(ProviderLibrary.class);
      events = mock(ProviderEventsLibrary.class);
      when(provider.events()).thenReturn(events);
      currentConfig.setProvider(provider);
      currentConfig.entities.put("TestEntity", new EntityMetadata(TestEntity.class, eventStreamAuthorizer));
      currentConfig.reducers.put("TestEvent", new ReducerMetadata(TestEntity.class, "testReducerMethod"));
      currentConfig.reducers.put("TestEventReducedByNonRegisteredEntity",
          new ReducerMetadata(NonRegisteredTestEntity.class, "testReducerMethod"));
      currentConfig.events.put("TestEvent", new EventMetadata(TestEvent.class));
      config = currentConfig;
      eventsReader = new BoosterEventsReader(currentConfig);
    });
    searchResult = List.of(EventSearchResponse.builder()
        .requestID(UUID.generate())
        .type("TestEvent")
        .entity("TestEntity")
        .entityID(UUID.generate())
        .createdAt("2026-11-10T10:00:00.000Z")
        .value(Map.of("id", "testId"))
        .build());
    when(events.search(any(), any())).thenReturn(searchResult);
  }

  private static EventSearchRequest request(UserEnvelope user, EventSearchParameters parameters) {
    return EventSearchRequest.builder().requestID(UUID.generate()).currentUser(user).parameters(parameters).build();
  }

  private static EventParametersFilterByEntity byEntity(String entity) {
    return new EventParametersFilterByEntity(entity, null, null, null, null);
  }

  private static EventParametersFilterByType byType(String type) {
    return new EventParametersFilterByType(type, null, null, null);
  }

  private static UserEnvelope user(String... roles) {
    return UserEnvelope.builder().username("someone@example.com").roles(List.of(roles)).claims(Map.of()).build();
  }

  // -------------------------------------------------------------------------------------------------------------
  // Validation
  // -------------------------------------------------------------------------------------------------------------

  @Test
  void failsWhenTheEntityOfAByEntitySearchIsNotFound() {
    assertThatThrownBy(() -> eventsReader.fetch(request(null, byEntity("NonExistingEntity"))))
        .isInstanceOf(NotFoundError.class)
        .hasMessage("Could not find entity metadata for \"NonExistingEntity\"");
  }

  @Test
  void failsWhenTheEntityAssociatedToAByTypeSearchIsNotFound() {
    assertThatThrownBy(() -> eventsReader.fetch(request(null, byType("NonExistingEventType"))))
        .isInstanceOf(NotFoundError.class)
        .hasMessage("Could not find the entity associated to event type \"NonExistingEventType\"");
  }

  @Test
  void failsWhenTheEntityThatReducesTheEventIsNotRegistered() {
    assertThatThrownBy(() -> eventsReader.fetch(request(null, byType("TestEventReducedByNonRegisteredEntity"))))
        .isInstanceOf(NotFoundError.class)
        .hasMessage("Could not find entity metadata for \"NonRegisteredTestEntity\"");
  }

  @Test
  void failsWhenTheSearchIsNeitherByEntityNorByType() {
    assertThatThrownBy(() -> eventsReader.fetch(request(null, null)))
        .isInstanceOf(InvalidParameterError.class)
        .hasMessage("Invalid event search request. It should contain either \"type\" or \"entity\" field, but it"
            + " included none");
    assertThatThrownBy(() -> eventsReader.fetch(UUID.generate(), null, Map.of()))
        .isInstanceOf(InvalidParameterError.class)
        .hasMessageContaining("Invalid event search request")
        .hasMessageEndingWith("but it included none");
  }

  @Test
  void failsWhenTheSearchIsBothByEntityAndByType() {
    assertThatThrownBy(() -> eventsReader.fetch(UUID.generate(), null, Map.of("entity", "TestEntity", "type", "TestEvent")))
        .isInstanceOf(InvalidParameterError.class)
        .hasMessage("Invalid event search request. It should contain either \"type\" or \"entity\" field, but it"
            + " included both");
    verify(events, never()).search(any(), any());
  }

  @Test
  void failsWhenTheUserHasNoPermissions() {
    assertThatThrownBy(() -> eventsReader.fetch(request(user("NonValidRole"), byEntity("TestEntity"))))
        .isInstanceOf(NotAuthorizedError.class)
        .hasMessageContaining("Access denied");
    verify(events, never()).search(any(), any());
  }

  @Test
  void failsForAnonymousUsers() {
    assertThatThrownBy(() -> eventsReader.fetch(request(null, byType("TestEvent"))))
        .isInstanceOf(NotAuthorizedError.class)
        .hasMessage("Access denied for this resource");
  }

  // -------------------------------------------------------------------------------------------------------------
  // Fetch
  // -------------------------------------------------------------------------------------------------------------

  @Test
  void callsTheProviderSearchWithTheParametersOfAByEntitySearch() {
    EventSearchRequest request = request(user("CanReadEventsRole"),
        new EventParametersFilterByEntity("TestEntity", null, "fromTime", "toTime", null));

    List<EventSearchResponse> result = eventsReader.fetch(request);

    verify(events, times(1)).search(any(), eq(request.parameters()));
    assertThat(result).hasSize(1);
    assertThat(result.getFirst()).usingRecursiveComparison().ignoringFields("value").isEqualTo(searchResult.getFirst());
    assertThat(result.getFirst().value()).isEqualTo(new TestEvent(UUID.of("testId")));
  }

  @Test
  void authorizesAByTypeSearchWithTheEntityThatReducesTheEvent() {
    EventSearchRequest request = request(user("CanReadEventsRole"), byType("TestEvent"));

    assertThat(eventsReader.fetch(request)).hasSize(1);

    verify(events).search(Booster.config(), request.parameters());
  }

  @Test
  void passesTheUserAndTheRequestToACustomAuthorizer() {
    List<Object> received = new ArrayList<>();
    config.entities.put("TestEntity", new EntityMetadata(TestEntity.class, (currentUser, eventSearchRequest) -> {
      received.add(currentUser);
      received.add(eventSearchRequest);
    }));
    EventSearchRequest request = request(user("Anyone"), byEntity("TestEntity"));

    eventsReader.fetch(request);

    assertThat(received).containsExactly(request.currentUser(), request);
  }

  @Test
  void anEntityWithoutAuthorizerCannotBeRead() {
    config.entities.put("TestEntity", new EntityMetadata(TestEntity.class, null));

    assertThatThrownBy(() -> eventsReader.fetch(request(user("CanReadEventsRole"), byEntity("TestEntity"))))
        .isInstanceOf(NotAuthorizedError.class)
        .hasMessage("Access denied for this resource");
  }

  @Test
  void theEventsOfTheBuiltInDataMigrationEntityCannotBeRead() {
    Booster.configureBoosterConcepts();

    assertThatThrownBy(() -> eventsReader.fetch(request(user("CanReadEventsRole"), byType("BoosterDataMigrationStarted"))))
        .isInstanceOf(NotAuthorizedError.class);
  }

  @Test
  void fetchesWithRawParametersAsGraphQLPassesThem() {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("entity", "TestEntity");
    parameters.put("entityID", "42");
    parameters.put("limit", 10);
    UUID requestID = UUID.generate();
    List<Object> received = new ArrayList<>();
    config.entities.put("TestEntity", new EntityMetadata(TestEntity.class,
        (currentUser, eventSearchRequest) -> received.add(eventSearchRequest)));

    eventsReader.fetch(requestID, user("CanReadEventsRole"), parameters);

    EventParametersFilterByEntity expected = new EventParametersFilterByEntity("TestEntity", "42", null, null, 10);
    verify(events).search(config, expected);
    assertThat(received).singleElement().isEqualTo(EventSearchRequest.builder().requestID(requestID)
        .currentUser(user("CanReadEventsRole")).parameters(expected).build());
  }
}
