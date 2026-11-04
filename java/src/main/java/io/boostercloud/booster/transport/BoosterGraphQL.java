package io.boostercloud.booster.transport;

import com.fasterxml.jackson.databind.JsonNode;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphqlErrorBuilder;
import graphql.Scalars;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.schema.*;
import io.boostercloud.booster.core.*;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Predicate;

/** Generated GraphQL API for registered commands, queries and read models. */
public final class BoosterGraphQL {
    private static final String CONTEXT = "booster.request";
    private static final String EXTERNAL_CHANGE = "booster.subscriptionChange";
    private final GraphQL graphQL;
    private final GraphQLSchema schema;

    public BoosterGraphQL(Booster booster) {
        Objects.requireNonNull(booster, "booster");
        GraphQLTypes types = new GraphQLTypes(Json.MAPPER);
        GraphQLObjectType.Builder query = GraphQLObjectType.newObject().name("Query");
        query.field(GraphQLFieldDefinition.newFieldDefinition().name("health").type(Scalars.GraphQLBoolean));
        types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Query", "health"), (DataFetcher<Object>) env -> true);
        GraphQLObjectType.Builder mutation = GraphQLObjectType.newObject().name("Mutation");
        GraphQLObjectType.Builder subscription = GraphQLObjectType.newObject().name("Subscription");
        Set<String> queryNames = new HashSet<>(Set.of("health"));
        Set<String> mutationNames = new HashSet<>();
        Set<String> subscriptionNames = new HashSet<>();

        for (Operation operation : booster.commands()) {
            unique(mutationNames, operation.name());
            mutation.field(operation(operation, types));
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Mutation", operation.name()), (DataFetcher<Object>) env -> {
                RequestContext original = context(env);
                // Distinguish multiple top-level mutation fields while keeping retries idempotent.
                RequestContext request = original.withRequestId(original.requestId() + ":" + env.getExecutionStepInfo().getPath());
                JsonNode result = booster.executeCommand(operation.name(), input(env), request);
                return response(result, operation.outputType());
            });
        }
        for (Operation operation : booster.queries()) {
            unique(queryNames, operation.name());
            query.field(operation(operation, types));
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Query", operation.name()), (DataFetcher<Object>) env ->
                    response(booster.executeQuery(operation.name(), input(env), context(env)), operation.outputType()));
        }
        for (ReadModel model : booster.readModels()) {
            String name = model.name();
            String plural = plural(name);
            String listName = "List" + plural;
            unique(queryNames, name);
            unique(queryNames, listName);
            unique(queryNames, plural);
            unique(subscriptionNames, name);
            unique(subscriptionNames, plural);
            GraphQLOutputType output = types.output(model.type());
            var byId = GraphQLFieldDefinition.newFieldDefinition().name(name)
                    .type(model.sequenceKey() == null ? output : GraphQLList.list(output))
                    .argument(GraphQLArgument.newArgument().name("id").type(GraphQLNonNull.nonNull(Scalars.GraphQLID)));
            if (model.sequenceKey() != null) byId.argument(GraphQLArgument.newArgument().name(model.sequenceKey()).type(Scalars.GraphQLID));
            query.field(byId);
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Query", name), (DataFetcher<Object>) env -> {
                if (model.sequenceKey() == null) return nativeValue(booster.getReadModel(name, env.getArgument("id"), context(env)));
                var filter = Json.MAPPER.createObjectNode(); filter.putObject("id").put("eq", (String) env.getArgument("id"));
                Object sequence = sequence(model, env);
                if (sequence != null) filter.putObject(model.sequenceKey()).set("eq", Json.MAPPER.valueToTree(sequence));
                return allModels(booster, name, filter, context(env));
            });

            GraphQLObjectType page = GraphQLObjectType.newObject().name(name + "Connection")
                    .field(GraphQLFieldDefinition.newFieldDefinition().name("items")
                            .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(output)))))
                    .field(GraphQLFieldDefinition.newFieldDefinition().name("count").type(GraphQLNonNull.nonNull(Scalars.GraphQLInt)))
                    .field(GraphQLFieldDefinition.newFieldDefinition().name("cursor").type(types.json)).build();
            query.field(GraphQLFieldDefinition.newFieldDefinition().name(listName).type(GraphQLNonNull.nonNull(page))
                    .argument(GraphQLArgument.newArgument().name("filter").type(types.filter(model.type(), "List" + name + "Filter", false)))
                    .argument(GraphQLArgument.newArgument().name("sortBy").type(types.sort(model.type(), name + "SortBy")))
                    .argument(GraphQLArgument.newArgument().name("limit").type(Scalars.GraphQLInt))
                    .argument(GraphQLArgument.newArgument().name("afterCursor").type(types.json)));
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Query", listName), (DataFetcher<Object>) env -> {
                Page result = booster.listReadModels(name, new QueryOptions(Json.MAPPER.valueToTree(env.getArgument("filter")),
                        sorts(env.getArgument("sortBy")), env.getArgumentOrDefault("limit", 100), cursor(env.getArgument("afterCursor"))), context(env));
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("items", result.items().stream().map(BoosterGraphQL::nativeValue).toList());
                value.put("count", result.items().size());
                value.put("cursor", result.cursor() == null ? null : Map.of("token", result.cursor()));
                return value;
            });
            query.field(GraphQLFieldDefinition.newFieldDefinition().name(plural)
                    .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(output))))
                    .argument(GraphQLArgument.newArgument().name("filter").type(types.filter(model.type(), name + "Filter", true)))
                    .deprecate("Method is deprecated. Use List* methods"));
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Query", plural), (DataFetcher<Object>) env ->
                    allModels(booster, name, Json.MAPPER.valueToTree(env.getArgument("filter")), context(env)));
            var byIdSubscription = GraphQLFieldDefinition.newFieldDefinition().name(name).type(output)
                    .argument(GraphQLArgument.newArgument().name("id").type(GraphQLNonNull.nonNull(Scalars.GraphQLID)));
            if (model.sequenceKey() != null) byIdSubscription.argument(GraphQLArgument.newArgument().name(model.sequenceKey()).type(Scalars.GraphQLID));
            subscription.field(byIdSubscription);
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Subscription", name), (DataFetcher<Object>) env ->
                    publisher(subscriptionSource(booster, name, env.getArgument("id"), sequence(model, env), null, env), change -> true));
            subscription.field(GraphQLFieldDefinition.newFieldDefinition().name(plural).type(output)
                    .argument(GraphQLArgument.newArgument().name("filter").type(types.filter(model.type(), name + "SubscriptionFilter", true))));
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Subscription", plural), (DataFetcher<Object>) env -> {
                JsonNode filter = Json.MAPPER.valueToTree(env.getArgument("filter"));
                Flow.Publisher<ReadModelChange> source = subscriptionSource(booster, name, null, null, filter, env);
                return publisher(source, change -> change.value() != null && !change.value().isNull());
            });
        }
        addEventQueries(booster, query, types, queryNames);
        GraphQLSchema.Builder builder = GraphQLSchema.newSchema().query(query.build())
                .additionalTypes(types.definitions()).codeRegistry(types.codeRegistry().build());
        if (!mutationNames.isEmpty()) builder.mutation(mutation.build());
        if (!subscriptionNames.isEmpty()) builder.subscription(subscription.build());
        schema = builder.build();
        graphQL = GraphQL.newGraphQL(schema)
                .instrumentation(new ChainedInstrumentation(List.of(
                        new MaxQueryDepthInstrumentation(20), new MaxQueryComplexityInstrumentation(500))))
                .defaultDataFetcherExceptionHandler(parameters -> {
                    Throwable error = parameters.getException();
                    BoosterException.Code code = error instanceof BoosterException boosterError
                            ? boosterError.code() : BoosterException.Code.INTERNAL;
                    String message = switch (code) {
                        case FORBIDDEN -> "Access denied";
                        case NOT_FOUND -> "Resource not found";
                        case CONFLICT -> "Concurrent modification; retry the command";
                        case VALIDATION -> "Invalid request";
                        case INTERNAL -> "Operation failed";
                    };
                    String clientCode = switch (code) {
                        case FORBIDDEN -> "NotAuthorizedError";
                        case NOT_FOUND -> "NotFoundError";
                        case CONFLICT -> "OptimisticConcurrencyUnexpectedVersionError";
                        case VALIDATION -> "InvalidParameterError";
                        case INTERNAL -> "BoosterError";
                    };
                    Map<String, Object> extensions = new LinkedHashMap<>(); extensions.put("code", clientCode);
                    if (error instanceof GraphQLClientException clientError) {
                        message = clientError.getMessage(); extensions.put("code", clientError.clientCode());
                        extensions.put("data", nativeValue(clientError.clientData()));
                    }
                    return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult()
                            .error(GraphqlErrorBuilder.newError(parameters.getDataFetchingEnvironment())
                                    .message(message).extensions(extensions).build()).build());
                }).build();
    }

    public GraphQLSchema schema() { return schema; }

    public ExecutionResult execute(String query, Map<String, Object> variables, String operationName, RequestContext context) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("GraphQL query is required");
        ExecutionInput.Builder input = ExecutionInput.newExecutionInput().query(query)
                .variables(variables == null ? Map.of() : variables)
                .graphQLContext(Map.of(CONTEXT, context == null ? RequestContext.anonymous() : context));
        if (operationName != null) input.operationName(operationName);
        return graphQL.execute(input.build());
    }

    /** Render a persisted provider notification through the same authorized subscription schema and selection set. */
    public ExecutionResult executeSubscriptionChange(String query, Map<String, Object> variables, String operationName,
                                                      RequestContext context, ReadModelChange change) {
        Objects.requireNonNull(change, "change");
        var document = graphql.parser.Parser.parse(query);
        var operations = document.getDefinitionsOfType(graphql.language.OperationDefinition.class);
        var selected = operations.stream().filter(op -> operationName == null || operationName.equals(op.getName())).toList();
        if (selected.size() != 1 || selected.getFirst().getOperation() != graphql.language.OperationDefinition.Operation.SUBSCRIPTION)
            throw new IllegalArgumentException("Exactly one selected subscription operation required");
        var input = ExecutionInput.newExecutionInput().query(query).variables(variables == null ? Map.of() : variables)
                .graphQLContext(Map.of(CONTEXT, context == null ? RequestContext.anonymous() : context, EXTERNAL_CHANGE, change));
        if (operationName != null) input.operationName(operationName);
        return graphQL.execute(input.build());
    }

    private static Flow.Publisher<ReadModelChange> subscriptionSource(Booster booster, String name, String id, Object sequence,
                                                                        JsonNode filter, DataFetchingEnvironment env) {
        ReadModelChange change = env.getGraphQlContext().get(EXTERNAL_CHANGE);
        if (change == null) return booster.subscribe(name, id, sequence, filter, context(env));
        Flow.Publisher<ReadModelChange> supplied = subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private boolean done;
            @Override public synchronized void request(long count) {
                if (done) return;
                done = true;
                if (count <= 0) { subscriber.onError(new IllegalArgumentException("Positive demand required")); return; }
                subscriber.onNext(change); subscriber.onComplete();
            }
            @Override public synchronized void cancel() { done = true; }
        });
        // Core applies the same resource authorization and hook-transformed matching to both sources.
        return booster.subscribe(name, id, sequence, filter, context(env), supplied);
    }

    private static Object sequence(ReadModel model, DataFetchingEnvironment env) {
        if (model.sequenceKey() == null || !env.containsArgument(model.sequenceKey())) return null;
        Object value = env.getArgument(model.sequenceKey());
        if (value == null) return null;
        if (model.type().isRecord()) for (var field : model.type().getRecordComponents())
            if (field.getName().equals(model.sequenceKey())) return Json.MAPPER.convertValue(value, field.getType());
        return value;
    }

    private static List<Object> allModels(Booster booster, String name, JsonNode filter, RequestContext context) {
        List<Object> result = new ArrayList<>(); String cursor = null;
        do {
            Page page = booster.listReadModels(name, new QueryOptions(filter, List.of(), 1000, cursor), context);
            page.items().forEach(item -> result.add(nativeValue(item)));
            String next = page.cursor();
            if (next != null && next.equals(cursor)) throw new IllegalStateException("Provider returned a nonadvancing cursor");
            cursor = next;
        } while (cursor != null);
        return result;
    }

    private static String cursor(Object value) {
        if (value == null) return null;
        if (value instanceof String text) return text;
        if (value instanceof Map<?, ?> map && map.size() == 1 && map.get("token") instanceof String token) return token;
        throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid opaque cursor");
    }

    private static void addEventQueries(Booster booster, GraphQLObjectType.Builder query, GraphQLTypes types, Set<String> names) {
        if (booster.entityNames().isEmpty()) return;
        var user = GraphQLObjectType.newObject().name("User")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("id").type(Scalars.GraphQLString))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("username").type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("roles").type(GraphQLList.list(GraphQLNonNull.nonNull(Scalars.GraphQLString)))).build();
        var event = GraphQLObjectType.newObject().name("EventQueryResponse")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("type").type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("entity").type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("entityID").type(GraphQLNonNull.nonNull(Scalars.GraphQLID)))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("requestID").type(GraphQLNonNull.nonNull(Scalars.GraphQLID)))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("user").type(user))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("createdAt").type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("value").type(GraphQLNonNull.nonNull(types.json)))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("deletedAt").type(Scalars.GraphQLString)).build();
        var entityNames = GraphQLEnumType.newEnum().name("EntityType"); booster.entityNames().forEach(entityNames::value);
        unique(names, "eventsByEntity");
        var byEntity = eventSearchField("eventsByEntity", event)
                .argument(GraphQLArgument.newArgument().name("entity").type(GraphQLNonNull.nonNull(entityNames.build())))
                .argument(GraphQLArgument.newArgument().name("entityID").type(Scalars.GraphQLID));
        query.field(byEntity);
        types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Query", "eventsByEntity"), (DataFetcher<Object>) env ->
                booster.searchEvents(env.getArgument("entity"), null, env.getArgument("entityID"),
                        eventTime(env.getArgument("from")), eventTime(env.getArgument("to")), env.getArgumentOrDefault("limit", 100), context(env))
                        .stream().map(BoosterGraphQL::eventResponse).toList());
        if (!booster.eventNames().isEmpty()) {
            var eventNames = GraphQLEnumType.newEnum().name("EventType"); booster.eventNames().forEach(eventNames::value);
            unique(names, "eventsByType");
            query.field(eventSearchField("eventsByType", event)
                    .argument(GraphQLArgument.newArgument().name("type").type(GraphQLNonNull.nonNull(eventNames.build()))));
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Query", "eventsByType"), (DataFetcher<Object>) env ->
                    booster.searchEvents(null, env.getArgument("type"), null, eventTime(env.getArgument("from")), eventTime(env.getArgument("to")),
                            env.getArgumentOrDefault("limit", 100), context(env)).stream().map(BoosterGraphQL::eventResponse).toList());
        }
    }
    private static GraphQLFieldDefinition.Builder eventSearchField(String name, GraphQLObjectType event) {
        return GraphQLFieldDefinition.newFieldDefinition().name(name).type(GraphQLList.list(event))
                .argument(GraphQLArgument.newArgument().name("from").type(Scalars.GraphQLString))
                .argument(GraphQLArgument.newArgument().name("to").type(Scalars.GraphQLString))
                .argument(GraphQLArgument.newArgument().name("limit").type(Scalars.GraphQLInt));
    }
    private static java.time.Instant eventTime(String value) {
        if (value == null) return null;
        try { return java.time.Instant.parse(value); }
        catch (RuntimeException failure) { throw new BoosterException(BoosterException.Code.VALIDATION, "Expected ISO-8601 event time"); }
    }
    private static Map<String, Object> eventResponse(StoredEvent stored) {
        PendingEvent event = stored.event(); Map<String,Object> result = new LinkedHashMap<>();
        result.put("type", event.type()); result.put("entity", event.stream().entityType()); result.put("entityID", event.stream().entityId());
        result.put("requestID", event.metadata().get("requestId")); result.put("createdAt", event.timestamp().toString());
        result.put("value", nativeValue(event.payload())); result.put("deletedAt", event.metadata().get("deletedAt"));
        UserContext user = event.user();
        if (user.authenticated()) {
            Object email = user.claims().get("email"), phone = user.claims().get("phone_number");
            String username = email instanceof String text && !text.isBlank() ? text
                    : phone instanceof String text && !text.isBlank() ? text : user.subject();
            result.put("user", Map.of("id", user.subject(), "username", username, "roles", user.roles().stream().sorted().toList()));
        } else result.put("user", null);
        return result;
    }

    private static GraphQLFieldDefinition operation(Operation operation, GraphQLTypes types) {
        GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition().name(operation.name())
                .type(types.output(operation.outputSchemaType()));
        if (operation.inputType() != Void.class && operation.inputType() != void.class
                && !(operation.inputType().isRecord() && operation.inputType().getRecordComponents().length == 0)) {
            builder.argument(GraphQLArgument.newArgument().name("input")
                    .type(GraphQLNonNull.nonNull(types.input(operation.inputType()))));
        }
        return builder.build();
    }

    private static RequestContext context(DataFetchingEnvironment env) { return env.getGraphQlContext().get(CONTEXT); }
    private static JsonNode input(DataFetchingEnvironment env) {
        Object input = env.getArgument("input");
        return Json.MAPPER.valueToTree(input == null ? Map.of() : input);
    }
    private static Object response(JsonNode value, Class<?> outputType) {
        if (outputType == void.class || outputType == Void.class) return true;
        return nativeValue(value);
    }
    private static Object nativeValue(JsonNode value) {
        return value == null || value.isNull() ? null : Json.MAPPER.convertValue(value, Object.class);
    }
    private static List<Sort> sorts(Object raw) {
        if (raw == null) return List.of();
        List<Sort> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object value : list) result.addAll(sorts(value));
        } else if (raw instanceof Map<?, ?> map) {
            if (map.containsKey("field")) result.add(new Sort(String.valueOf(map.get("field")),
                    Sort.Direction.valueOf(String.valueOf(map.containsKey("direction") ? map.get("direction") : "ASC"))));
            else flattenSort(map, "", result);
        } else throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid sortBy");
        return result;
    }
    private static void flattenSort(Map<?, ?> fields, String prefix, List<Sort> result) {
        fields.forEach((key, value) -> {
            String path = prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key;
            if (value instanceof Map<?, ?> nested) flattenSort(nested, path, result);
            else result.add(new Sort(path, Sort.Direction.valueOf(String.valueOf(value))));
        });
    }
    private static String plural(String name) { return GraphQLNames.plural(name); }
    private static void unique(Set<String> names, String name) {
        if (!name.matches("[_A-Za-z][_0-9A-Za-z]*") || name.startsWith("__") || !names.add(name))
            throw new IllegalArgumentException("Invalid or duplicate GraphQL field: " + name);
    }

    private static Publisher<Object> publisher(Flow.Publisher<ReadModelChange> source, Predicate<ReadModelChange> predicate) {
        return downstream -> source.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription upstream;
            @Override public void onSubscribe(Flow.Subscription subscription) {
                upstream = subscription;
                downstream.onSubscribe(new Subscription() {
                    @Override public void request(long count) { subscription.request(count); }
                    @Override public void cancel() { subscription.cancel(); }
                });
            }
            @Override public void onNext(ReadModelChange value) {
                try {
                    // Reactive Streams forbids null events; GraphQL unwraps this result's nullable data.
                    if (predicate.test(value)) downstream.onNext(DataFetcherResult.newResult().data(nativeValue(value.value())).build());
                    else upstream.request(1);
                } catch (RuntimeException error) { upstream.cancel(); downstream.onError(error); }
            }
            @Override public void onError(Throwable throwable) { downstream.onError(throwable); }
            @Override public void onComplete() { downstream.onComplete(); }
        });
    }
}
