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
                RequestContext request = new RequestContext(original.requestId() + ":" + env.getExecutionStepInfo().getPath(),
                        original.user(), original.metadata());
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
            unique(subscriptionNames, name);
            unique(subscriptionNames, plural);
            GraphQLOutputType output = types.output(model.type());
            query.field(GraphQLFieldDefinition.newFieldDefinition().name(name).type(output)
                    .argument(GraphQLArgument.newArgument().name("id").type(GraphQLNonNull.nonNull(Scalars.GraphQLID))));
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Query", name), (DataFetcher<Object>) env ->
                    nativeValue(booster.getReadModel(name, env.getArgument("id"), context(env))));

            GraphQLObjectType page = GraphQLObjectType.newObject().name(name + "Page")
                    .field(GraphQLFieldDefinition.newFieldDefinition().name("items")
                            .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(output)))))
                    .field(GraphQLFieldDefinition.newFieldDefinition().name("count").type(GraphQLNonNull.nonNull(Scalars.GraphQLInt)))
                    .field(GraphQLFieldDefinition.newFieldDefinition().name("cursor").type(Scalars.GraphQLString)).build();
            query.field(GraphQLFieldDefinition.newFieldDefinition().name(listName).type(page)
                    .argument(GraphQLArgument.newArgument().name("filter").type(types.json))
                    .argument(GraphQLArgument.newArgument().name("sortBy").type(types.json))
                    .argument(GraphQLArgument.newArgument().name("limit").type(Scalars.GraphQLInt))
                    .argument(GraphQLArgument.newArgument().name("afterCursor").type(Scalars.GraphQLString)));
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Query", listName), (DataFetcher<Object>) env -> {
                Page result = booster.listReadModels(name, new QueryOptions(Json.MAPPER.valueToTree(env.getArgument("filter")),
                        sorts(env.getArgument("sortBy")), env.getArgumentOrDefault("limit", 100), env.getArgument("afterCursor")), context(env));
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("items", result.items().stream().map(BoosterGraphQL::nativeValue).toList());
                value.put("count", result.count());
                value.put("cursor", result.cursor());
                return value;
            });
            subscription.field(GraphQLFieldDefinition.newFieldDefinition().name(name).type(output)
                    .argument(GraphQLArgument.newArgument().name("id").type(GraphQLNonNull.nonNull(Scalars.GraphQLID))));
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Subscription", name), (DataFetcher<Object>) env ->
                    publisher(booster.subscribe(name, env.getArgument("id"), context(env)), change -> true));
            subscription.field(GraphQLFieldDefinition.newFieldDefinition().name(plural).type(output)
                    .argument(GraphQLArgument.newArgument().name("filter").type(types.json)));
            types.codeRegistry().dataFetcher(FieldCoordinates.coordinates("Subscription", plural), (DataFetcher<Object>) env -> {
                JsonNode filter = Json.MAPPER.valueToTree(env.getArgument("filter"));
                Flow.Publisher<ReadModelChange> source = booster.subscribe(name, null, context(env));
                Filters.matches(Json.MAPPER.createObjectNode(), filter); // Reject malformed filters before opening a stream.
                return publisher(source, change ->
                        change.value() != null && !change.value().isNull() && Filters.matches(change.value(), filter));
            });
        }
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
                    return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult()
                            .error(GraphqlErrorBuilder.newError(parameters.getDataFetchingEnvironment())
                                    .message(message).extensions(Map.of("code", code.name())).build()).build());
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

    private static GraphQLFieldDefinition operation(Operation operation, GraphQLTypes types) {
        GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition().name(operation.name())
                .type(types.output(operation.outputType()));
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
            else map.forEach((key, value) -> result.add(new Sort(String.valueOf(key), Sort.Direction.valueOf(String.valueOf(value)))));
        } else throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid sortBy");
        return result;
    }
    private static String plural(String name) {
        if (name.endsWith("y") && name.length() > 1 && "aeiou".indexOf(Character.toLowerCase(name.charAt(name.length()-2))) < 0)
            return name.substring(0, name.length()-1) + "ies";
        if (name.endsWith("s") || name.endsWith("x") || name.endsWith("ch") || name.endsWith("sh")) return name + "es";
        return name + "s";
    }
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
