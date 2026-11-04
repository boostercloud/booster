package io.boostercloud.booster.transport;

import graphql.ExecutionResult;
import graphql.schema.*;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.example.ShoppingCartApplication;
import io.boostercloud.booster.store.JdbcEventStore;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLCompatibilityTest {
    @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface SearchResult permits FoundPerson, FoundOrder {}
    @com.fasterxml.jackson.annotation.JsonTypeName("FoundPerson") record FoundPerson(String id, String displayName) implements SearchResult {}
    @com.fasterxml.jackson.annotation.JsonTypeName("FoundOrder") record FoundOrder(String id, int amount) implements SearchResult {}
    record SearchAll() {}
    record SearchOne() {}

    @Test void genericReturnMetadataAndSealedRecordUnionsPreserveTypedSelections() {
        try (var app = Booster.builder(new InMemoryEventStore())
                .query(SearchAll.class, new com.fasterxml.jackson.core.type.TypeReference<List<SearchResult>>() {}, AccessPolicy.publicAccess(),
                        (query, context) -> List.of(new FoundPerson("p", "Person"), new FoundOrder("o", 42)))
                .query(SearchOne.class, new com.fasterxml.jackson.core.type.TypeReference<Optional<FoundPerson>>() {}, AccessPolicy.publicAccess(),
                        (query, context) -> Optional.empty()).build()) {
            var api = new BoosterGraphQL(app);
            assertEquals("[SearchResult!]!", GraphQLTypeUtil.simplePrint(api.schema().getQueryType().getFieldDefinition("SearchAll").getType()));
            var result = api.execute("{SearchAll {__typename ... on FoundPerson {id displayName} ... on FoundOrder {id amount}} SearchOne {id}}", Map.of(), null, RequestContext.anonymous());
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            var json = Json.MAPPER.valueToTree(result.toSpecification());
            assertEquals("FoundPerson", json.at("/data/SearchAll/0/__typename").asText());
            assertEquals(42, json.at("/data/SearchAll/1/amount").asInt()); assertTrue(json.at("/data/SearchOne").isNull());
        }
    }

    @Test void operationResultsAreRequiredUnlessTheirDeclaredReturnTypeIsOptional() {
        record RequiredScalar() {} record NullableScalar() {} record RequiredList() {} record NullableList() {}
        record InvalidRequired() {} record VoidCommand() {} record NullableCommand() {}
        try (var app = Booster.builder(new InMemoryEventStore())
                .query(RequiredScalar.class, String.class, AccessPolicy.publicAccess(), (query, context) -> "present")
                .query(NullableScalar.class, new com.fasterxml.jackson.core.type.TypeReference<Optional<String>>() {}, AccessPolicy.publicAccess(),
                        (query, context) -> Optional.empty())
                .query(RequiredList.class, new com.fasterxml.jackson.core.type.TypeReference<List<Optional<String>>>() {}, AccessPolicy.publicAccess(),
                        (query, context) -> List.of(Optional.of("present"), Optional.empty()))
                .query(NullableList.class, new com.fasterxml.jackson.core.type.TypeReference<Optional<List<String>>>() {}, AccessPolicy.publicAccess(),
                        (query, context) -> Optional.empty())
                .query(InvalidRequired.class, String.class, AccessPolicy.publicAccess(), (query, context) -> null)
                .command(VoidCommand.class, Void.class, AccessPolicy.publicAccess(), (command, register) -> null)
                .command(NullableCommand.class, new com.fasterxml.jackson.core.type.TypeReference<Optional<String>>() {}, AccessPolicy.publicAccess(),
                        (command, register) -> Optional.empty()).build()) {
            var api = new BoosterGraphQL(app);
            for (var entry : Map.of("RequiredScalar", "String!", "NullableScalar", "String", "RequiredList", "[String]!", "NullableList", "[String!]").entrySet())
                assertEquals(entry.getValue(), GraphQLTypeUtil.simplePrint(api.schema().getQueryType().getFieldDefinition(entry.getKey()).getType()));
            assertEquals("Boolean!", GraphQLTypeUtil.simplePrint(api.schema().getMutationType().getFieldDefinition("VoidCommand").getType()));
            assertEquals("String", GraphQLTypeUtil.simplePrint(api.schema().getMutationType().getFieldDefinition("NullableCommand").getType()));
            var result = api.execute("{RequiredScalar NullableScalar RequiredList NullableList}", Map.of(), null, RequestContext.anonymous());
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            var data = Json.MAPPER.valueToTree(result.toSpecification()).path("data");
            assertEquals("present", data.path("RequiredScalar").asText()); assertTrue(data.path("NullableScalar").isNull());
            assertEquals("present", data.at("/RequiredList/0").asText()); assertTrue(data.at("/RequiredList/1").isNull());
            assertTrue(data.path("NullableList").isNull());
            var invalid = api.execute("{RequiredScalar InvalidRequired}", Map.of(), null, RequestContext.anonymous());
            assertNull(invalid.getData()); assertEquals(List.of("InvalidRequired"), invalid.getErrors().getFirst().getPath());
            var command = api.execute("mutation{VoidCommand NullableCommand}", Map.of(), null, RequestContext.anonymous());
            assertTrue(command.getErrors().isEmpty(), command.getErrors().toString());
            assertTrue(Json.MAPPER.valueToTree(command.toSpecification()).at("/data/VoidCommand").asBoolean());
            assertTrue(Json.MAPPER.valueToTree(command.toSpecification()).at("/data/NullableCommand").isNull());
        }
    }

    @Test void pluralizationMatchesCapturedTypeScriptDependencyOutput() throws Exception {
        try (var fixture = getClass().getResourceAsStream("/contracts/inflected-2.1.0-plurals.json")) {
            assertNotNull(fixture);
            for (var item : Json.MAPPER.readTree(fixture).path("cases"))
                assertEquals(item.path("expected").asText(), GraphQLNames.plural(item.path("input").asText()), item.toString());
        }
    }

    @Test void matchesBaselineGeneratedFilterSortConnectionAndCursorSignatures() {
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory())) {
            var api = new BoosterGraphQL(app); var schema = api.schema();
            var listed = schema.getQueryType().getFieldDefinition("ListCartViews");
            assertEquals("CartViewConnection!", GraphQLTypeUtil.simplePrint(listed.getType()));
            assertEquals("ListCartViewFilter", GraphQLTypeUtil.simplePrint(listed.getArgument("filter").getType()));
            assertEquals("CartViewSortBy", GraphQLTypeUtil.simplePrint(listed.getArgument("sortBy").getType()));
            assertEquals("JSON", GraphQLTypeUtil.simplePrint(listed.getArgument("afterCursor").getType()));
            assertEquals("CartViewSubscriptionFilter", GraphQLTypeUtil.simplePrint(schema.getSubscriptionType()
                    .getFieldDefinition("CartViews").getArgument("filter").getType()));
            assertTrue(schema.getQueryType().getFieldDefinition("CartViews").isDeprecated());
            assertEquals("NumberPropertyFilter", GraphQLTypeUtil.simplePrint(((GraphQLInputObjectType) schema.getType("ListCartViewFilter"))
                    .getFieldDefinition("totalCents").getType()));
            var filter = Map.of("and", List.of(Map.of("totalQuantity", Map.of("gte", 0)), Map.of("id", Map.of("beginsWith", "c"))));
            for (String id : List.of("c1", "c2", "c3")) app.executeCommand("OpenCart", Json.MAPPER.valueToTree(Map.of("cartId", id)), RequestContext.anonymous());
            String query = "query ExistingClient($where:ListCartViewFilter,$order:CartViewSortBy,$cursor:JSON) { ListCartViews(filter:$where,sortBy:$order,limit:2,afterCursor:$cursor) { items{id} count cursor } }";
            ExecutionResult first = api.execute(query, Map.of("where", filter, "order", Map.of("id", "ASC")), "ExistingClient", RequestContext.anonymous());
            assertTrue(first.getErrors().isEmpty(), first.getErrors().toString());
            var firstJson = Json.MAPPER.valueToTree(first.toSpecification());
            assertEquals(2, firstJson.at("/data/ListCartViews/count").asInt());
            Object cursor = Json.MAPPER.convertValue(firstJson.at("/data/ListCartViews/cursor"), Object.class);
            ExecutionResult second = api.execute(query, Map.of("where", filter, "order", Map.of("id", "ASC"), "cursor", cursor), "ExistingClient", RequestContext.anonymous());
            assertTrue(second.getErrors().isEmpty(), second.getErrors().toString());
            assertEquals("c3", Json.MAPPER.valueToTree(second.toSpecification()).at("/data/ListCartViews/items/0/id").asText());
            assertFalse(api.execute("{ListCartViews(filter:{typo:{eq:1}}){count}}", Map.of(), null, RequestContext.anonymous()).getErrors().isEmpty());
        }
    }

    @Test void schemaUsesBaselineNumericIdAndNullableFieldsAndIrregularPluralNames() {
        record Person(@GraphQLId String id, UUID uuid, int age, @GraphQLNullable String nickname) {}
        record PersonState(String id) {}
        try (var app = Booster.builder(JdbcEventStore.inMemory()).entity(PersonState.class)
                .readModel(Person.class, PersonState.class, AccessPolicy.publicAccess(), (s,p) -> new Person(s.id(), UUID.randomUUID(), 1, null)).build()) {
            var schema = new BoosterGraphQL(app).schema();
            assertNotNull(schema.getQueryType().getFieldDefinition("ListPeople"));
            assertEquals("Float!", GraphQLTypeUtil.simplePrint(schema.getObjectType("Person").getFieldDefinition("age").getType()));
            assertEquals("ID!", GraphQLTypeUtil.simplePrint(schema.getObjectType("Person").getFieldDefinition("uuid").getType()));
            assertEquals("ID!", GraphQLTypeUtil.simplePrint(schema.getObjectType("Person").getFieldDefinition("id").getType()));
            assertEquals("UUIDPropertyFilter", GraphQLTypeUtil.simplePrint(((GraphQLInputObjectType) schema.getType("ListPersonFilter")).getFieldDefinition("id").getType()));
            assertEquals("String", GraphQLTypeUtil.simplePrint(schema.getObjectType("Person").getFieldDefinition("nickname").getType()));
        }
    }

    @Test void explicitIdentifierMetadataPreservesArbitraryStringIdsInputsListsAndFilters() {
        record EchoIdentifiers(@GraphQLId String id, @GraphQLId Optional<String> parent, @GraphQLId List<String> references) {}
        try (var app = Booster.builder(new InMemoryEventStore())
                .command(EchoIdentifiers.class, EchoIdentifiers.class, AccessPolicy.publicAccess(), (command, register) -> command).build()) {
            var api = new BoosterGraphQL(app);
            assertEquals("EchoIdentifiers!", GraphQLTypeUtil.simplePrint(api.schema().getMutationType().getFieldDefinition("EchoIdentifiers").getType()));
            var input = (GraphQLInputObjectType) api.schema().getType("EchoIdentifiersInput");
            assertEquals("ID!", GraphQLTypeUtil.simplePrint(input.getFieldDefinition("id").getType()));
            assertEquals("ID", GraphQLTypeUtil.simplePrint(input.getFieldDefinition("parent").getType()));
            assertEquals("[ID!]!", GraphQLTypeUtil.simplePrint(input.getFieldDefinition("references").getType()));
            var result = api.execute("mutation{EchoIdentifiers(input:{id:\"cart-1\",parent:123,references:[\"order-a\",456]}){id parent references}}",
                    Map.of(), null, RequestContext.anonymous());
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            var payload = Json.MAPPER.valueToTree(result.toSpecification()).at("/data/EchoIdentifiers");
            assertEquals("cart-1", payload.path("id").asText()); assertEquals("123", payload.path("parent").asText());
            assertEquals("456", payload.at("/references/1").asText());
        }
    }

    @Test void durableNotificationRenderingPreservesSelectionFilteringAndDeletion() throws Exception {
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory())) {
            var api = new BoosterGraphQL(app);
            var change = new ReadModelChange("CartView", "x", Json.MAPPER.valueToTree(Map.of("id", "x", "totalQuantity", 4, "totalCents", 100, "checkedOut", false)), 1);
            var result = api.executeSubscriptionChange("subscription {CartView(id:\"x\"){id}}", Map.of(), null, RequestContext.anonymous(), change);
            List<ExecutionResult> items = collect(result);
            assertEquals(1, items.size());
            var payload = Json.MAPPER.valueToTree(items.getFirst().toSpecification());
            assertEquals("x", payload.at("/data/CartView/id").asText());
            assertFalse(payload.at("/data/CartView").has("totalQuantity"));
            assertTrue(collect(api.executeSubscriptionChange("subscription {CartViews(filter:{totalQuantity:{gt:5}}){id}}", Map.of(), null, RequestContext.anonymous(), change)).isEmpty());
            var deletion = new ReadModelChange("CartView", "x", null, 2);
            assertTrue(Json.MAPPER.valueToTree(collect(api.executeSubscriptionChange("subscription {CartView(id:\"x\"){id}}", Map.of(), null,
                    RequestContext.anonymous(), deletion)).getFirst().toSpecification()).at("/data/CartView").isNull());
            assertThrows(IllegalArgumentException.class, () -> api.executeSubscriptionChange("mutation {OpenCart(input:{cartId:\"bad\"}){id}}",
                    Map.of(), null, RequestContext.anonymous(), change));
            assertEquals(0, app.eventStore().lastPosition());
        }
    }

    @Test void sequenceQueriesKeepTheBaselineListShapeAndOptionalSequenceId() {
        record Revision(String id, String aggregateId, int revision, String title) {}
        record RevisionChanged(String id, String aggregateId, int revision, String title) {}
        record RevisionView(String id, int revision, String title) {}
        try (var app = Booster.builder(new InMemoryEventStore())
                .event(RevisionChanged.class, RevisionChanged::id, Revision.class, (event, previous) -> new Revision(event.id(), event.aggregateId(), event.revision(), event.title()))
                .readModel(RevisionView.class, AccessPolicy.publicAccess()).sequenceKey(RevisionView.class, "revision")
                .projection(RevisionView.class, Revision.class, Join.key(Revision::aggregateId), context ->
                        ProjectionResult.store(new RevisionView(context.id(), context.entity().revision(), context.entity().title()))).build()) {
            for (int revision : List.of(1, 2)) app.executeAction("fixture", Json.MAPPER.valueToTree(revision), RequestContext.anonymous(), AccessPolicy.publicAccess(),
                    (input, register) -> { register.emit(new RevisionChanged("r" + revision, "logical", revision, "Version" + revision)); return null; });
            var api = new BoosterGraphQL(app);
            assertEquals("[RevisionView]", GraphQLTypeUtil.simplePrint(api.schema().getQueryType().getFieldDefinition("RevisionView").getType()));
            var all = api.execute("{RevisionView(id:\"logical\"){id revision title}}", Map.of(), null, RequestContext.anonymous());
            assertTrue(all.getErrors().isEmpty(), all.getErrors().toString());
            assertEquals(2, Json.MAPPER.valueToTree(all.toSpecification()).at("/data/RevisionView").size());
            var one = api.execute("{RevisionView(id:\"logical\",revision:\"2\"){id revision title}}", Map.of(), null, RequestContext.anonymous());
            assertTrue(one.getErrors().isEmpty(), one.getErrors().toString());
            assertEquals("Version2", Json.MAPPER.valueToTree(one.toSpecification()).at("/data/RevisionView/0/title").asText());
        }
    }

    @Test void generatedDateHiddenFieldsListsAndPublicErrorDataFollowExplicitContracts() {
        record Request(java.time.Instant at, @GraphQLNullable String note, @GraphQLHidden String internal, List<String> tags) {}
        record Fail(String value) {}
        try (var app = Booster.builder(new InMemoryEventStore())
                .command(Request.class, Request.class, AccessPolicy.publicAccess(), (request, register) -> request)
                .command(Fail.class, String.class, AccessPolicy.publicAccess(), (request, register) -> {
                    throw new GraphQLClientException("CartUnavailable", "Cart is unavailable", Map.of("reason", "archived"));
                }).build()) {
            var api = new BoosterGraphQL(app); var input = (GraphQLInputObjectType) api.schema().getType("RequestInput");
            assertNull(input.getFieldDefinition("internal"));
            assertEquals("[String!]!", GraphQLTypeUtil.simplePrint(input.getFieldDefinition("tags").getType()));
            var result = api.execute("mutation {Request(input:{at:\"2026-11-04T02:00:00+02:00\",tags:[\"a\"]}){at note tags}}", Map.of(), null, RequestContext.anonymous());
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            assertEquals("2026-11-04T00:00:00.000Z", Json.MAPPER.valueToTree(result.toSpecification()).at("/data/Request/at").asText());
            var denied = api.execute("mutation {Fail(input:{value:\"x\"})}", Map.of(), null, RequestContext.anonymous());
            assertEquals("CartUnavailable", denied.getErrors().getFirst().getExtensions().get("code"));
            assertEquals(Map.of("reason", "archived"), denied.getErrors().getFirst().getExtensions().get("data"));
            assertFalse(api.execute("mutation {Request(input:{at:\"invalid\",tags:[]}){at}}", Map.of(), null, RequestContext.anonymous()).getErrors().isEmpty());
        }
    }

    @Test void historyQueriesDefaultDenyAndHonorEntityAccessAndBaselineEnvelopeNames() {
        record Entity(String id, String value) {}
        record Changed(String id, String value) {}
        for (boolean allowed : List.of(false, true)) {
            var builder = Booster.builder(new InMemoryEventStore())
                    .event(Changed.class, Changed::id, Entity.class, (event, previous) -> new Entity(event.id(), event.value()));
            if (allowed) builder.eventHistoryAccess(Entity.class, AccessPolicy.roles("auditor"));
            try (var app = builder.build()) {
                var identity = new UserContext("u", Set.of("auditor"), Map.of("email", "reader@example.test"));
                app.executeAction("fixture", Json.MAPPER.createObjectNode(), new RequestContext("request-original", identity, Map.of()), AccessPolicy.publicAccess(),
                        (input, register) -> { register.emit(new Changed("e", "created")); return null; });
                var api = new BoosterGraphQL(app);
                var context = new RequestContext("history", identity, Map.of());
                var result = api.execute("{eventsByEntity(entity:Entity,entityID:\"e\",limit:5){type entity entityID requestID createdAt value user{id username roles} deletedAt}}", Map.of(), null, context);
                if (!allowed) {
                    assertEquals("NotAuthorizedError", result.getErrors().getFirst().getExtensions().get("code")); continue;
                }
                assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
                var envelope = Json.MAPPER.valueToTree(result.toSpecification()).at("/data/eventsByEntity/0");
                assertEquals("Changed", envelope.path("type").asText()); assertEquals("request-original", envelope.path("requestID").asText());
                assertEquals("reader@example.test", envelope.at("/user/username").asText());
                assertFalse(api.execute("{eventsByType(type:Changed){type}}", Map.of(), null, RequestContext.anonymous()).getErrors().isEmpty());
                assertTrue(api.execute("{eventsByType(type:Changed){type}}", Map.of(), null, context).getErrors().isEmpty());
            }
        }
    }

    @Test void eventHistoryPreservesVerifiedIssuerOnlyIdentitiesWithoutSubject() throws Exception {
        record HistoryEntity(String id) {} record HistoryCreated(String id) {} record CreateHistory(String id) {}
        var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        var verifier = io.boostercloud.booster.transport.auth.JwtVerifier.boosterCompatible(
                (java.security.interfaces.RSAPublicKey) pair.getPublic(), "https://issuer.example");
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String unsigned = encoder.encodeToString(Json.MAPPER.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(Json.MAPPER.writeValueAsBytes(Map.of("iss", "https://issuer.example", "email", "auditor@example.test", "custom:role", "auditor")));
        var signature = java.security.Signature.getInstance("SHA256withRSA"); signature.initSign(pair.getPrivate());
        signature.update(unsigned.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var identity = AuthenticationProvider.jwt(verifier).authenticate("Bearer " + unsigned + "." + encoder.encodeToString(signature.sign()));
        assertTrue(identity.authenticated()); assertNull(identity.subject());
        try (var app = Booster.builder(new InMemoryEventStore())
                .event(HistoryCreated.class, HistoryCreated::id, HistoryEntity.class, (event, previous) -> new HistoryEntity(event.id()))
                .command(CreateHistory.class, Void.class, AccessPolicy.authenticated(), (command, register) -> {
                    register.emit(new HistoryCreated(command.id())); return null;
                }).eventHistoryAccess(HistoryEntity.class, AccessPolicy.roles("auditor")).build()) {
            var api = new BoosterGraphQL(app);
            var created = api.execute("mutation{CreateHistory(input:{id:\"created\"})}", Map.of(), null,
                    new RequestContext("issuer-only-command", identity, Map.of()));
            assertTrue(created.getErrors().isEmpty(), created.getErrors().toString());
            var history = api.execute("{eventsByEntity(entity:HistoryEntity){entityID user{id username roles}}}", Map.of(), null,
                    new RequestContext("issuer-only-history", identity, Map.of()));
            assertTrue(history.getErrors().isEmpty(), history.getErrors().toString());
            var event = Json.MAPPER.valueToTree(history.toSpecification()).at("/data/eventsByEntity/0");
            assertEquals("created", event.path("entityID").asText()); assertTrue(event.at("/user/id").isNull());
            assertEquals("auditor@example.test", event.at("/user/username").asText()); assertEquals("auditor", event.at("/user/roles/0").asText());
            assertTrue(app.eventStore().readAll(0, 10).getFirst().event().user().verifiedIdentity());
        }
    }

    @Test void durableSubscriptionsUseTheSameHookTransformedFilterAsLocalSubscriptions() throws Exception {
        record HookEntity(String id, String region) {}
        record HookView(String id, String region) {}
        try (var app = Booster.builder(new InMemoryEventStore()).entity(HookEntity.class)
                .readModel(HookView.class, HookEntity.class, AccessPolicy.publicAccess(), (entity, previous) -> new HookView(entity.id(), entity.region()))
                .beforeReadModel("HookView", (input, context) -> {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) input).set("filter", Json.MAPPER.valueToTree(Map.of("region", Map.of("eq", "effective"))));
                    return input;
                }).build()) {
            var api = new BoosterGraphQL(app);
            var effective = new ReadModelChange("HookView", "id", Json.MAPPER.valueToTree(Map.of("id", "id", "region", "effective")), 1);
            String subscription = "subscription {HookViews(filter:{region:{eq:\"original\"}}){id region}}";
            assertEquals(1, collect(api.executeSubscriptionChange(subscription, Map.of(), null, RequestContext.anonymous(), effective)).size());
            var original = new ReadModelChange("HookView", "id", Json.MAPPER.valueToTree(Map.of("id", "id", "region", "original")), 2);
            assertTrue(collect(api.executeSubscriptionChange(subscription, Map.of(), null, RequestContext.anonymous(), original)).isEmpty());
        }
    }

    @SuppressWarnings("unchecked") private static List<ExecutionResult> collect(ExecutionResult initial) throws Exception {
        assertTrue(initial.getErrors().isEmpty(), initial.getErrors().toString());
        var done = new CompletableFuture<List<ExecutionResult>>(); var items = new ArrayList<ExecutionResult>();
        ((Publisher<ExecutionResult>) initial.getData()).subscribe(new Subscriber<>() {
            @Override public void onSubscribe(Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(ExecutionResult item) { items.add(item); }
            @Override public void onError(Throwable t) { done.completeExceptionally(t); }
            @Override public void onComplete() { done.complete(List.copyOf(items)); }
        });
        return done.get(3, TimeUnit.SECONDS);
    }
}
