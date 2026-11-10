package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.decorators.CalculatedField;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.ReadModelBeforeFunction;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderReadModelsLibrary;
import com.boostercloud.framework.types.searcher.Searcher;
import com.boostercloud.metadata.ClassMetadata;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Port of {@code test/booster-read-models-reader.test.ts}, plus the paths the TypeScript tests leave uncovered. */
class BoosterReadModelsReaderTest extends BoosterTestSupport {

  public record TestReadModel(UUID id) {}

  public record SequencedReadModel(UUID id, String salmon) {}

  public static class UserRole {}

  private static final GraphQLOperation NOOP_GRAPHQL_OPERATION = GraphQLOperation.builder().query("").build();
  private static final UserEnvelope AUTHORIZED_USER =
      UserEnvelope.builder().id("666").username("root").roles(List.of("UserRole")).build();

  private BoosterConfig config;
  private ProviderReadModelsLibrary readModels;
  private BoosterReadModelsReader readModelReader;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = mock(Logger.class);
    ProviderLibrary provider = mock(ProviderLibrary.class);
    readModels = mock(ProviderReadModelsLibrary.class);
    when(provider.readModels()).thenReturn(readModels);
    config.setProvider(provider);
    // Why sorting by salmon? Salmons are fun! https://youtu.be/dDj7DuHVV9E
    config.readModelSequenceKeys.put("SequencedReadModel", "salmon");
    register(TestReadModel.class, List.of());
    register(SequencedReadModel.class, List.of());
    readModelReader = new BoosterReadModelsReader(config);
  }

  private void register(Class<?> readModelClass, List<ReadModelBeforeFunction> before) {
    config.readModels.put(readModelClass.getSimpleName(), new ReadModelMetadata(readModelClass, List.of(),
        BoosterAuthorizer.authorizeRoles(List.of(UserRole.class)), before));
  }

  private static Map<String, Object> raw(Object... keysAndValues) {
    Map<String, Object> raw = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      raw.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return raw;
  }

  private void providerSearchReturns(ReadModelListResult<Map<String, Object>> result) {
    when(readModels.search(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(result);
  }

  private static MockedConstruction<ReadModelSchemaMigrator> identityMigrator() {
    return mockConstruction(ReadModelSchemaMigrator.class,
        (mock, context) -> when(mock.migrate(any(), any())).thenAnswer(invocation -> invocation.getArgument(0)));
  }

  private static int migrateCalls(MockedConstruction<ReadModelSchemaMigrator> migrators) {
    return migrators.constructed().stream()
        .mapToInt(migrator -> (int) org.mockito.Mockito.mockingDetails(migrator).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("migrate")).count())
        .sum();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // requests by Id
  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class ValidateByIdRequest {
    @Test
    void throwsAnInvalidParameterErrorWhenTheVersionIsNotPresent() {
      assertThatThrownBy(() -> readModelReader.validateByIdRequest(ReadModelRequestEnvelope.builder().build()))
          .isInstanceOf(InvalidParameterError.class)
          .hasMessageContaining("\"version\" was not present")
          .hasMessage("The required request \"version\" was not present");
    }

    @Test
    void throwsANotFoundErrorWhenItCannotFindTheReadModelMetadata() {
      ReadModelRequestEnvelope request = ReadModelRequestEnvelope.builder().version(1).className("NonexistentReadModel").build();

      assertThatThrownBy(() -> readModelReader.validateByIdRequest(request))
          .isInstanceOf(NotFoundError.class)
          .hasMessage("Could not find read model NonexistentReadModel");
    }

    @Test
    void throwsANotAuthorizedErrorWhenTheCurrentUserIsNotAllowed() {
      ReadModelRequestEnvelope request = ReadModelRequestEnvelope.builder().version(1).type(TestReadModel.class).build();

      assertThatThrownBy(() -> readModelReader.validateByIdRequest(request))
          .isInstanceOf(NotAuthorizedError.class)
          .hasMessageContaining("Access denied");
    }

    @Test
    void throwsAnInvalidParameterErrorWhenTheSequenceKeyIsNotDefinedForTheReadModel() {
      ReadModelRequestEnvelope request = ReadModelRequestEnvelope.builder().version(1).type(TestReadModel.class)
          .currentUser(AUTHORIZED_USER)
          .key(new ReadModelRequestEnvelope.Key(UUID.of("π"), SequenceKey.of("salmon", "sammy"))).build();

      assertThatThrownBy(() -> readModelReader.validateByIdRequest(request))
          .isInstanceOf(InvalidParameterError.class)
          .hasMessage("Could not find a sort key defined for TestReadModel named 'salmon'.");
    }

    @Test
    void acceptsARequestWithoutSequenceKey() {
      readModelReader.validateByIdRequest(ReadModelRequestEnvelope.builder().version(1).type(TestReadModel.class)
          .currentUser(AUTHORIZED_USER).build());
    }

    @Test
    void acceptsAValidSequenceKey() {
      readModelReader.validateByIdRequest(ReadModelRequestEnvelope.builder().version(1).type(SequencedReadModel.class)
          .currentUser(AUTHORIZED_USER).key(new ReadModelRequestEnvelope.Key(UUID.of("§"), SequenceKey.of("salmon", "sammy")))
          .build());
    }

    @Test
    void usesTheClassNameWhenTheRequestHasNoClass() {
      readModelReader.validateByIdRequest(ReadModelRequestEnvelope.builder().version(1).className("TestReadModel")
          .currentUser(AUTHORIZED_USER).build());
    }
  }

  @Nested
  class FindById {
    private final SequenceKey sequenceKey = SequenceKey.of("salmon", "sammy");
    private BoosterReadModelsReader reader;
    private Searcher<?> searcher;

    @BeforeEach
    void setUp() {
      reader = spy(readModelReader);
      doNothing().when(reader).validateByIdRequest(any());
      searcher = mock(Searcher.class);
    }

    private ReadModelRequestEnvelope request() {
      return ReadModelRequestEnvelope.builder().key(new ReadModelRequestEnvelope.Key(UUID.of("42"), sequenceKey))
          .type(TestReadModel.class).className("TestReadModel").currentUser(UserEnvelope.builder().id("a user").build())
          .version(1).requestID(UUID.of("my request!")).build();
    }

    @SuppressWarnings("deprecation")
    private Object findById(Object searcherResult, ReadModelRequestEnvelope request) {
      when(searcher.findById(any(), any())).thenReturn(searcherResult);
      try (MockedStatic<Booster> booster = mockStatic(Booster.class, CALLS_REAL_METHODS)) {
        booster.when(() -> Booster.readModel(any())).thenAnswer(invocation -> searcher);
        return reader.findById(request);
      }
    }

    @Test
    @SuppressWarnings("deprecation")
    void validatesAndUsesTheSearcherToFindAReadModelById() {
      ReadModelRequestEnvelope request = request();
      try (MockedConstruction<ReadModelSchemaMigrator> ignored = identityMigrator()) {
        findById(null, request);
      }

      verify(reader, times(1)).validateByIdRequest(request);
      verify(searcher, times(1)).findById(UUID.of("42"), sequenceKey);
    }

    @Test
    void callsMigrateAfterFindingAReadModelById() {
      try (MockedConstruction<ReadModelSchemaMigrator> migrators = identityMigrator()) {
        Object result = findById(raw("id", "42"), request());

        assertThat(migrateCalls(migrators)).isEqualTo(1);
        assertThat(result).isEqualTo(new TestReadModel(UUID.of("42")));
      }
    }

    @Test
    void callsMigrateOnceWhenItGotAnArray() {
      try (MockedConstruction<ReadModelSchemaMigrator> migrators = identityMigrator()) {
        Object result = findById(List.of(raw("id", "1"), raw("id", "2")), request());

        assertThat(migrateCalls(migrators)).isEqualTo(1);
        assertThat(result).isEqualTo(List.of(new TestReadModel(UUID.of("1"))));
      }
    }

    @Test
    void returnsNothingWhenTheReadModelDoesNotExist() {
      assertThat(findById(null, request())).isNull();
    }

    @Test
    void failsWhenTheTransformedRequestHasNoKey() {
      ReadModelRequestEnvelope request = request().toBuilder().key(null).build();

      assertThatThrownBy(() -> findById(null, request))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Tried to run a findById operation without providing a key. An ID is required to perform this"
              + " operation.");
    }

    @Test
    @SuppressWarnings("deprecation")
    void usesTheKeyOfTheTransformedRequest() {
      ReadModelBeforeFunction changeKey = (envelope, user) ->
          envelope.toBuilder().key(new ReadModelRequestEnvelope.Key(UUID.of("other"), null)).build();
      register(TestReadModel.class, List.of(changeKey));

      findById(null, request());

      verify(searcher).findById(UUID.of("other"), null);
    }

    @Test
    void migratesTheFoundReadModelWithTheRealMigrator() {
      Map<Integer, SchemaMigrationMetadata> migrations = new LinkedHashMap<>();
      migrations.put(2, new SchemaMigrationMetadata(SequencedMigration.class, "addSalmon", 2, TestReadModel.class,
          SequencedReadModel.class));
      config.schemaMigrations.put("TestReadModel", migrations);

      Object result = findById(raw("id", "42", "boosterMetadata", raw("version", 3, "schemaVersion", 1)), request());

      // The migrated value is bound to the read model class again
      assertThat(result).isEqualTo(new TestReadModel(UUID.of("42")));
    }
  }

  /** Migrates a TestReadModel into a SequencedReadModel (only used to exercise the real migrator). */
  public static class SequencedMigration {
    public SequencedReadModel addSalmon(TestReadModel old) {
      return new SequencedReadModel(old.id(), "sammy");
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // validation for search and subscribe
  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class SearchAndSubscribeValidation {
    @Test
    void throwsTheRightErrorWhenTheRequestIsMissingVersion() {
      ReadModelRequestEnvelope envelope = ReadModelRequestEnvelope.builder().className("anyReadModel")
          .requestID(UUID.generate()).build();

      assertThatThrownBy(() -> readModelReader.search(envelope)).isInstanceOf(InvalidParameterError.class);
      assertThatThrownBy(() -> readModelReader.subscribe(envelope.requestID().value(), envelope, NOOP_GRAPHQL_OPERATION))
          .isInstanceOf(InvalidParameterError.class);
    }

    @Test
    void throwsTheRightErrorWhenTheReadModelDoesNotExist() {
      ReadModelRequestEnvelope envelope = ReadModelRequestEnvelope.builder().className("nonExistentReadModel")
          .filters(Map.of()).requestID(UUID.generate()).version(1).build();

      assertThatThrownBy(() -> readModelReader.search(envelope)).isInstanceOf(NotFoundError.class);
      assertThatThrownBy(() -> readModelReader.subscribe(envelope.requestID().value(), envelope, NOOP_GRAPHQL_OPERATION))
          .isInstanceOf(NotFoundError.class);
    }

    @Test
    void throwsTheRightErrorWhenTheUserIsNotAuthorized() {
      ReadModelRequestEnvelope envelope = ReadModelRequestEnvelope.builder().type(TestReadModel.class)
          .className("TestReadModel").requestID(UUID.generate()).filters(Map.of()).version(1)
          .currentUser(UserEnvelope.builder().username("user@example.com").roles(List.of("")).claims(Map.of()).build())
          .build();

      assertThatThrownBy(() -> readModelReader.search(envelope)).isInstanceOf(NotAuthorizedError.class);
      assertThatThrownBy(() -> readModelReader.subscribe(envelope.requestID().value(), envelope, NOOP_GRAPHQL_OPERATION))
          .isInstanceOf(NotAuthorizedError.class);
      verify(readModels, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
      verify(readModels, never()).subscribe(any(), any());
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // the logic of search and subscribe
  // ---------------------------------------------------------------------------------------------------------------

  private final Map<String, Object> filters = raw("id", raw("operation", "eq", "values", List.of("abcde")),
      "field", raw("operation", "lt", "values", List.of(7)));

  private final UserEnvelope currentUser =
      UserEnvelope.builder().username("user@example.com").roles(List.of("UserRole")).claims(Map.of()).build();

  private ReadModelRequestEnvelope readModelRequestEnvelope() {
    return ReadModelRequestEnvelope.builder().type(TestReadModel.class).className("TestReadModel")
        .requestID(UUID.of("request-1")).version(1).filters(filters).currentUser(currentUser).build();
  }

  /** {@code beforeFn}: {@code { ...request, filters: { id: { eq: request.filters.id } } }}. */
  static final class BeforeFn implements ReadModelBeforeFunction {
    final List<List<Object>> calls = new ArrayList<>();
    final List<ReadModelRequestEnvelope> results = new ArrayList<>();

    @Override
    public ReadModelRequestEnvelope apply(ReadModelRequestEnvelope request, UserEnvelope user) {
      calls.add(java.util.Arrays.asList(request, user));
      ReadModelRequestEnvelope result =
          request.toBuilder().filters(Map.of("id", Map.of("eq", request.filters().get("id")))).build();
      results.add(result);
      return result;
    }
  }

  /** {@code beforeFnV2}: {@code { ...request, filters: { id: { eq: request.currentUser?.username } } }}. */
  static final class BeforeFnV2 implements ReadModelBeforeFunction {
    final List<List<Object>> calls = new ArrayList<>();
    final List<ReadModelRequestEnvelope> results = new ArrayList<>();

    @Override
    public ReadModelRequestEnvelope apply(ReadModelRequestEnvelope request, UserEnvelope user) {
      calls.add(java.util.Arrays.asList(request, user));
      ReadModelRequestEnvelope result = request.toBuilder()
          .filters(Map.of("id", Map.of("eq", request.currentUser().username()))).build();
      results.add(result);
      return result;
    }
  }

  @Nested
  class Search {
    @Test
    void callsTheProviderSearchFunctionAndReturnsItsResults() {
      providerSearchReturns(ReadModelListResult.of(List.of(raw("id", "∂"), raw("id", "∂"))));

      try (MockedConstruction<ReadModelSchemaMigrator> migrators = identityMigrator()) {
        Object result = readModelReader.search(readModelRequestEnvelope());

        verify(readModels, times(1)).search(any(), eq("TestReadModel"), eq(filters), eq(Map.of()), isNull(), isNull(),
            eq(false), isNull());
        assertThat(result).isEqualTo(List.of(new TestReadModel(UUID.of("∂")), new TestReadModel(UUID.of("∂"))));
        assertThat(migrateCalls(migrators)).isEqualTo(2);
      }
    }

    @Test
    void callsMigrateForEveryItemOfAPaginatedSearch() {
      providerSearchReturns(new ReadModelListResult<>(List.of(raw("id", "∂"), raw("id", "∂")), 2, Map.of()));

      try (MockedConstruction<ReadModelSchemaMigrator> migrators = identityMigrator()) {
        Object result = readModelReader.search(readModelRequestEnvelope().toBuilder().paginatedVersion(true).build());

        verify(readModels, times(1)).search(any(), eq("TestReadModel"), eq(filters), eq(Map.of()), isNull(), isNull(),
            eq(true), isNull());
        assertThat(result).isEqualTo(new ReadModelListResult<>(
            List.of(new TestReadModel(UUID.of("∂")), new TestReadModel(UUID.of("∂"))), 2, Map.of()));
        assertThat(migrateCalls(migrators)).isEqualTo(2);
      }
    }

    @Test
    void passesTheSelectionAndDoesNotMigrate() {
      providerSearchReturns(ReadModelListResult.of(List.of(raw("id", "∂", "other", 1), raw("id", "∂"))));

      try (MockedConstruction<ReadModelSchemaMigrator> migrators = identityMigrator()) {
        Object result = readModelReader.search(readModelRequestEnvelope().toBuilder().select(List.of("id")).build());

        verify(readModels, times(1)).search(any(), eq("TestReadModel"), eq(filters), eq(Map.of()), isNull(), isNull(),
            eq(false), eq(List.of("id")));
        assertThat(result).isEqualTo(List.of(Map.of("id", UUID.of("∂")), Map.of("id", UUID.of("∂"))));
        assertThat(migrateCalls(migrators)).isZero();
      }
    }

    @Test
    void callsTheBeforeHookFunction() {
      BeforeFn beforeFn = new BeforeFn();
      register(TestReadModel.class, List.of(beforeFn));
      providerSearchReturns(ReadModelListResult.of(List.of()));
      ReadModelRequestEnvelope request = readModelRequestEnvelope();

      readModelReader.search(request);

      assertThat(beforeFn.calls).containsExactly(java.util.Arrays.asList(request, request.currentUser()));
      assertThat(beforeFn.results.get(0)).isEqualTo(request.toBuilder().filters(Map.of("id", Map.of("eq", filters.get("id"))))
          .build());
      verify(readModels).search(any(), eq("TestReadModel"), eq(Map.of("id", Map.of("eq", filters.get("id")))), eq(Map.of()),
          isNull(), isNull(), eq(false), isNull());
    }

    @Test
    void chainsTheBeforeHookFunctions() {
      BeforeFn beforeFn = new BeforeFn();
      BeforeFnV2 beforeFnV2 = new BeforeFnV2();
      register(TestReadModel.class, List.of(beforeFn, beforeFnV2));
      providerSearchReturns(ReadModelListResult.of(List.of()));
      ReadModelRequestEnvelope request = readModelRequestEnvelope();

      readModelReader.search(request);

      assertThat(beforeFn.calls).containsExactly(java.util.Arrays.asList(request, request.currentUser()));
      ReadModelRequestEnvelope returnedEnvelope = beforeFn.results.get(0);
      assertThat(beforeFnV2.calls).containsExactly(java.util.Arrays.asList(returnedEnvelope, returnedEnvelope.currentUser()));
      assertThat(beforeFnV2.results.get(0))
          .isEqualTo(returnedEnvelope.toBuilder().filters(Map.of("id", Map.of("eq", "user@example.com"))).build());
    }

    @Test
    void sendsTheTransformedSortLimitCursorAndPagination() {
      ReadModelBeforeFunction paginate = (envelope, user) -> envelope.toBuilder().sortBy(Map.of("id", "DESC")).limit(5)
          .afterCursor(Map.of("id", "3")).paginatedVersion(true).build();
      register(TestReadModel.class, List.of(paginate));
      providerSearchReturns(new ReadModelListResult<>(List.of(), 0, null));

      Object result = readModelReader.search(readModelRequestEnvelope());

      verify(readModels).search(any(), eq("TestReadModel"), eq(filters), eq(Map.of("id", "DESC")), eq(5),
          eq(Map.of("id", "3")), eq(true), isNull());
      assertThat(result).isEqualTo(new ReadModelListResult<>(List.of(), 0, null));
    }
  }

  @Nested
  class Subscribe {
    @BeforeEach
    void freezeTheClock() {
      Dates.useClock(Clock.fixed(Instant.ofEpochMilli(1_700_000_000_999L), ZoneOffset.UTC));
    }

    @Test
    void callsTheProviderSubscribeFunction() {
      ReadModelRequestEnvelope request = readModelRequestEnvelope();

      assertThat(readModelReader.subscribe("connection-1", request, NOOP_GRAPHQL_OPERATION)).isNull();

      ArgumentCaptor<SubscriptionEnvelope> subscription = ArgumentCaptor.forClass(SubscriptionEnvelope.class);
      verify(readModels, times(1)).subscribe(eq(config), subscription.capture());
      assertThat(subscription.getValue())
          .isEqualTo(SubscriptionEnvelope.of(request, 1_700_000_000L + 172_800L, "connection-1", NOOP_GRAPHQL_OPERATION));
    }

    @Test
    void storesTheFiltersProducedByTheBeforeHooks() {
      register(TestReadModel.class, List.of(new BeforeFn(), new BeforeFnV2()));
      config.subscriptions.maxDurationInSeconds = 10;
      ReadModelRequestEnvelope request = readModelRequestEnvelope();

      readModelReader.subscribe("connection-1", request, NOOP_GRAPHQL_OPERATION);

      ArgumentCaptor<SubscriptionEnvelope> subscription = ArgumentCaptor.forClass(SubscriptionEnvelope.class);
      verify(readModels).subscribe(eq(config), subscription.capture());
      ReadModelRequestEnvelope expected = request.toBuilder().filters(Map.of("id", Map.of("eq", "user@example.com"))).build();
      assertThat(subscription.getValue())
          .isEqualTo(SubscriptionEnvelope.of(expected, 1_700_000_010L, "connection-1", NOOP_GRAPHQL_OPERATION));
    }
  }

  @Test
  void unsubscribeCallsTheProviderDeleteSubscription() {
    readModelReader.unsubscribe("connection-1", "subscription-1");

    verify(readModels, times(1)).deleteSubscription(any(), eq("connection-1"), eq("subscription-1"));
  }

  @Test
  void unsubscribeAllCallsTheProviderDeleteAllSubscriptions() {
    readModelReader.unsubscribeAll("connection-1");

    verify(readModels, times(1)).deleteAllSubscriptions(any(), eq("connection-1"));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // The programmatic path (no TypeScript test)
  // ---------------------------------------------------------------------------------------------------------------

  /** A read model with calculated fields. */
  public record Person(UUID id, String firstName, String lastName, List<Map<String, Object>> friends) {
    @CalculatedField(dependsOn = {"firstName", "lastName"})
    public String fullName() {
      return firstName + " " + lastName;
    }

    @CalculatedField(dependsOn = {"friends[].name"})
    public List<Map<String, Object>> bestFriends() {
      return friends;
    }
  }

  @Nested
  class ReadModelSearch {
    @BeforeEach
    void registerPerson() {
      config.readModels.put("Person", new ReadModelMetadata(Person.class, ClassMetadata.of(Person.class).fields(),
          BoosterAuthorizer.ALLOW_ACCESS, List.of()));
    }

    @Test
    void addsTheDependenciesOfTheSelectedCalculatedFieldsAndReturnsOnlyTheSelection() {
      providerSearchReturns(ReadModelListResult.of(List.of(raw("id", "1", "firstName", "Ada", "lastName", "Lovelace"))));

      Object result = readModelReader.readModelSearch(Person.class, null, null, null, null, false, List.of("id", "fullName"));

      verify(readModels).search(any(), eq("Person"), eq(Map.of()), eq(Map.of()), isNull(), isNull(), eq(false),
          eq(List.of("id", "fullName", "firstName", "lastName")));
      assertThat(result).isEqualTo(List.of(Map.of("id", UUID.of("1"), "fullName", "Ada Lovelace")));
    }

    @Test
    void usesTheTopLevelFieldOfArrayAndNestedPaths() {
      providerSearchReturns(ReadModelListResult.of(List.of()));

      readModelReader.readModelSearch(Person.class, Map.of(), Map.of(), 3, null, true,
          List.of("bestFriends[].name", "fullName.whatever"));

      verify(readModels).search(any(), eq("Person"), eq(Map.of()), eq(Map.of()), eq(3), isNull(), eq(true),
          eq(List.of("bestFriends[].name", "fullName.whatever", "friends[].name", "firstName", "lastName")));
    }

    @Test
    void anEmptySelectionStillSelects() {
      providerSearchReturns(ReadModelListResult.of(List.of(raw("id", "1"))));

      assertThat(readModelReader.readModelSearch(Person.class, null, null, null, null, false, List.of()))
          .isEqualTo(List.of(Map.of()));
    }

    @Test
    void theSearcherOfBoosterReadModelGoesStraightToTheProvider() {
      providerSearchReturns(ReadModelListResult.of(List.of(raw("id", "1", "firstName", "Ada"))));
      config.readModels.put("Person", new ReadModelMetadata(Person.class, List.of(), BoosterAuthorizer.DENY_ACCESS, List.of()));

      List<Person> people = com.boostercloud.framework.core.services.ReadModelSearcher.readModelSearcher(config, Person.class)
          .filter(Map.of("id", Map.of("eq", "1"))).search();

      assertThat(people).containsExactly(new Person(UUID.of("1"), "Ada", null, null));
    }

    @Test
    void readModelSearchDocumentsReturnsTheMigratedRawDocuments() {
      Map<String, Object> stored = raw("id", "1", "boosterMetadata", raw("version", 4));
      providerSearchReturns(ReadModelListResult.of(List.of(stored)));

      List<Map<String, Object>> documents =
          readModelReader.readModelSearchDocuments(Person.class, Map.of("id", Map.of("in", List.of("1"))));

      verify(readModels).search(any(), eq("Person"), eq(Map.of("id", Map.of("in", List.of("1")))), eq(Map.of()), isNull(),
          isNull(), eq(false), isNull());
      assertThat(documents).containsExactly(stored);
    }
  }

  @Nested
  class FinderByIdFunction {
    @Test
    void returnsTheFirstRawDocumentWithoutSequenceKey() {
      when(readModels.fetch(config, "TestReadModel", UUID.of("1"), null)).thenReturn(List.of(raw("id", "1"), raw("id", "2")));

      assertThat(readModelReader.finderByIdFunction(TestReadModel.class, UUID.of("1"), null)).isEqualTo(raw("id", "1"));
    }

    @Test
    void returnsEveryDocumentWithASequenceKey() {
      SequenceKey sequenceKey = SequenceKey.of("salmon", "s");
      when(readModels.fetch(config, "SequencedReadModel", UUID.of("1"), sequenceKey))
          .thenReturn(List.of(raw("id", "1"), raw("id", "1")));

      assertThat(readModelReader.finderByIdFunction(SequencedReadModel.class, UUID.of("1"), sequenceKey))
          .isEqualTo(List.of(raw("id", "1"), raw("id", "1")));
    }

    @Test
    void returnsNullWhenNothingIsFound() {
      when(readModels.fetch(any(), any(), any(), any())).thenReturn(List.of());

      assertThat(readModelReader.finderByIdFunction(TestReadModel.class, UUID.of("1"), null)).isNull();
    }
  }
}
