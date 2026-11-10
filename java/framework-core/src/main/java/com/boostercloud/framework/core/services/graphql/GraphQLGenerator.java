package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.BoosterCommandDispatcher;
import com.boostercloud.framework.core.BoosterEventsReader;
import com.boostercloud.framework.core.BoosterQueryDispatcher;
import com.boostercloud.framework.core.BoosterReadModelsReader;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.TimeKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchRequest;
import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.validation.InvalidSchemaException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port of {@code services/graphql/graphql-generator.ts}: the process-wide builder of the {@link GraphQLSchema} and the
 * resolver factories (read model search / by id, events, commands, queries, subscriptions).
 *
 * <p>The schema and the four collaborators the resolvers use ({@link BoosterCommandDispatcher},
 * {@link BoosterQueryDispatcher}, {@link BoosterReadModelsReader}, {@link BoosterEventsReader}) are created once per
 * process, on the first {@link #generateSchema(BoosterConfig)} call, with that call's configuration; later calls
 * return the same schema. {@link #reset()} forgets them (the TypeScript tests delete the static field).
 *
 * <p>The resolvers receive the {@link GraphQLResolverContext} of the request (see
 * {@link Common#resolverContext(graphql.schema.DataFetchingEnvironment)}) and build the envelopes of the other
 * layers exactly like TypeScript ({@code version: 1}, {@code filters}/{@code sortBy} default to {@code {}}).
 */
public final class GraphQLGenerator {

  private static final Object LOCK = new Object();

  private static volatile BoosterCommandDispatcher commandsDispatcher;
  private static volatile BoosterQueryDispatcher queriesDispatcher;
  private static volatile BoosterReadModelsReader readModelsReader;
  private static volatile BoosterEventsReader eventsReader;
  private static volatile GraphQLSchema schema;
  private static volatile InvalidSchemaException schemaFailure;

  private GraphQLGenerator() {}

  /**
   * {@code generateSchema(config)}: builds the schema on the first call (query, then mutation, then subscription
   * generation, sharing one {@link GraphQLTypeInformer} and one filter cache) and returns the cached one afterwards.
   *
   * <p>graphql-java validates a schema when it is built, whereas graphql-js only does on the first request. An
   * invalid schema (for example an empty enum or an object type without fields) therefore throws the graphql-java
   * {@link InvalidSchemaException} here, on this and every later call; {@code BoosterGraphQLDispatcher} reports it
   * in the response of every operation, as TypeScript does.
   *
   * @param config the configuration
   * @return the schema
   * @throws InvalidSchemaException when graphql-java rejects the generated schema
   */
  public static GraphQLSchema generateSchema(BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "GraphQLGenerator#generateSchema");
    synchronized (LOCK) {
      if (schema == null) {
        if (schemaFailure != null) {
          throw schemaFailure;
        }
        logger.debug("Generating GraphQL schema...");
        commandsDispatcher = new BoosterCommandDispatcher(config);
        queriesDispatcher = new BoosterQueryDispatcher(config);
        readModelsReader = new BoosterReadModelsReader(config);
        eventsReader = new BoosterEventsReader(config);

        GraphQLTypeInformer typeInformer = new GraphQLTypeInformer(logger);
        Map<String, GraphQLInputObjectType> generatedFiltersByTypeName = new LinkedHashMap<>();
        List<Class<?>> readModels = config.readModels.values().stream()
            .<Class<?>>map(ReadModelMetadata::type)
            .toList();
        Map<String, TargetTypeMetadata> queryHandlers = new LinkedHashMap<>();
        config.queryHandlers.forEach((name, metadata) -> queryHandlers.put(name, TargetTypeMetadata.of(metadata)));
        Map<String, TargetTypeMetadata> commandHandlers = new LinkedHashMap<>();
        config.commandHandlers.forEach((name, metadata) -> commandHandlers.put(name, TargetTypeMetadata.of(metadata)));

        GraphQLQueryGenerator queryGenerator = new GraphQLQueryGenerator(config, readModels, queryHandlers,
            typeInformer, readModelClass -> readModelByIDResolverBuilder(config, readModelClass),
            GraphQLGenerator::queriesResolverBuilder, GraphQLGenerator::readModelResolverBuilder, eventResolver(),
            generatedFiltersByTypeName);
        GraphQLMutationGenerator mutationGenerator = new GraphQLMutationGenerator(commandHandlers, typeInformer,
            GraphQLGenerator::commandResolverBuilder, config);
        GraphQLSubscriptionGenerator subscriptionGenerator = new GraphQLSubscriptionGenerator(readModels,
            typeInformer, readModelClass -> subscriptionByIDResolverBuilder(config, readModelClass),
            readModelClass -> subscriptionResolverBuilder(config, readModelClass), generatedFiltersByTypeName, config);

        GeneratedObjectType query = queryGenerator.generate();
        Optional<GeneratedObjectType> mutation = mutationGenerator.generate();
        Optional<GeneratedObjectType> subscription = subscriptionGenerator.generate();
        try {
          schema = buildSchema(query, mutation, subscription, typeInformer);
        } catch (InvalidSchemaException e) {
          schemaFailure = e;
          throw e;
        }
        logger.debug("GraphQL schema generated");
      }
      return schema;
    }
  }

  private static GraphQLSchema buildSchema(GeneratedObjectType query, Optional<GeneratedObjectType> mutation,
      Optional<GeneratedObjectType> subscription, GraphQLTypeInformer typeInformer) {
    GraphQLCodeRegistry.Builder codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        .defaultDataFetcher(PropertyResolver.FACTORY);
    query.registerResolvers(codeRegistry);
    mutation.ifPresent(type -> type.registerResolvers(codeRegistry));
    subscription.ifPresent(type -> type.registerResolvers(codeRegistry));
    typeInformer.typeResolvers().forEach(codeRegistry::typeResolver);
    GraphQLSchema.Builder builder = GraphQLSchema.newSchema().query(query.type());
    mutation.ifPresent(type -> builder.mutation(type.type()));
    subscription.ifPresent(type -> builder.subscription(type.type()));
    return builder.codeRegistry(codeRegistry.build()).build();
  }

  /**
   * Test hook: forgets the schema, the collaborators and a cached schema failure (TypeScript tests
   * {@code delete (GraphQLGenerator as any).schema}).
   */
  public static void reset() {
    synchronized (LOCK) {
      schema = null;
      schemaFailure = null;
      commandsDispatcher = null;
      queriesDispatcher = null;
      readModelsReader = null;
      eventsReader = null;
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Resolvers
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code readModelResolverBuilder(readModelClass)}: the resolver of the plural filter query and of the paginated
   * {@code List<ReadModels>} query. It runs {@code BoosterReadModelsReader.search} with the {@code filter},
   * {@code sortBy}, {@code limit} and {@code afterCursor} arguments and the selected fields as {@code select} (see
   * {@link #getFields(DataFetchingEnvironment)}). The search is paginated exactly when the field is
   * {@code List<pluralized name>}; then the first segment ({@code items[].}) is removed from every selected path and the
   * empty ones are dropped.
   *
   * @param readModelClass the read model class
   * @return the resolver
   */
  public static GraphQLFieldResolver readModelResolverBuilder(Class<?> readModelClass) {
    return (parent, args, context, info) -> {
      boolean isPaginated = false;
      List<String> fields = getFields(info);
      List<String> select = fields.isEmpty() ? null : fields;
      if (("List" + Inflection.pluralize(readModelClass.getSimpleName())).equals(fieldName(info))) {
        isPaginated = true;
        if (select != null) {
          // In paginated queries, the `items[].` field needs to be removed from the select fields before querying
          // the database
          select = select.stream()
              .map(field -> {
                List<String> segments = Arrays.asList(field.split("\\.", -1));
                return String.join(".", segments.subList(1, segments.size()));
              })
              .filter(path -> !path.trim().isEmpty())
              .toList();
        }
      }
      ReadModelRequestEnvelope readModelEnvelope = toReadModelRequestEnvelope(readModelClass, args, context,
          isPaginated, select);
      return readModelsReader().search(readModelEnvelope);
    };
  }

  /**
   * {@code readModelByIDResolverBuilder(config, readModelClass)}: the resolver of the by-id query, which runs
   * {@code BoosterReadModelsReader.findById} with the key {@code {id}} or, when the read model has a sequence key
   * (read from {@code config.readModelSequenceKeys} when the resolver is built), {@code {id, sequenceKey: {name,
   * value}}} (the value is {@code null} when the argument is omitted).
   *
   * @param config the configuration
   * @param readModelClass the read model class
   * @return the resolver
   */
  public static GraphQLFieldResolver readModelByIDResolverBuilder(BoosterConfig config, Class<?> readModelClass) {
    String sequenceKeyName = config.readModelSequenceKeys.get(readModelClass.getSimpleName());
    return (parent, args, context, info) -> readModelsReader().findById(
        toReadModelByIdRequestEnvelope(readModelClass, args, context, sequenceKeyName));
  }

  /**
   * {@code eventResolver(parent, args, context)}: {@code BoosterEventsReader.fetch} with the GraphQL arguments as the
   * search parameters.
   *
   * @param parent ignored
   * @param args the {@code eventsByEntity}/{@code eventsByType} arguments
   * @param context the resolver context
   * @return the events found
   */
  public static Object eventResolver(Object parent, Map<String, Object> args, GraphQLResolverContext context) {
    EventSearchRequest eventsRequestEnvelope = toEventSearchRequest(args, context);
    return eventsReader().fetch(eventsRequestEnvelope);
  }

  /**
   * The resolver of {@code eventsByEntity}/{@code eventsByType} ({@code this.eventResolver.bind(this)}).
   *
   * @return the resolver
   */
  public static GraphQLFieldResolver eventResolver() {
    return (parent, args, context, info) -> eventResolver(parent, args, context);
  }

  /**
   * {@code commandResolverBuilder(commandClass)}: the mutation resolver, which dispatches the command with the
   * {@code input} argument and returns its result, or {@code true} when the handler returns nothing
   * ({@code result ?? true}).
   *
   * @param commandClass the command class
   * @return the resolver
   */
  public static GraphQLFieldResolver commandResolverBuilder(Class<?> commandClass) {
    return (parent, args, context, info) -> {
      CommandEnvelope commandEnvelope = toCommandEnvelope(commandClass.getSimpleName(), args.get("input"), context);
      Object result = commandsDispatcher().dispatchCommand(commandEnvelope, context);
      // It could be that the command didn't return anything
      // so in that case we return `true`, as GraphQL doesn't have a `null` type
      return result != null ? result : Boolean.TRUE;
    };
  }

  /**
   * {@code queriesResolverBuilder(queryClass)}: the resolver of a user query, which dispatches it with the
   * {@code input} argument and returns its result as is.
   *
   * @param queryClass the query class
   * @return the resolver
   */
  public static GraphQLFieldResolver queriesResolverBuilder(Class<?> queryClass) {
    return (parent, args, context, info) -> {
      QueryEnvelope queryEnvelope = QueryEnvelope.fromCommandEnvelope(
          toCommandEnvelope(queryClass.getSimpleName(), args.get("input"), context));
      return queriesDispatcher().dispatchQuery(queryEnvelope, context);
    };
  }

  /**
   * {@code subscriptionByIDResolverBuilder(config, readModelClass)}: the by-id subscription, which is the filtered
   * subscription with {@code {filter: {id: {eq: <id>}}}}.
   *
   * @param config the configuration
   * @param readModelClass the read model class
   * @return the subscription resolver
   */
  public static GraphQLFieldResolver subscriptionByIDResolverBuilder(BoosterConfig config, Class<?> readModelClass) {
    return (parent, args, context, info) -> {
      Map<String, Object> idFilter = new LinkedHashMap<>();
      idFilter.put("eq", args.get("id"));
      Map<String, Object> filter = new LinkedHashMap<>();
      filter.put("id", idFilter);
      Map<String, Object> filterArgs = new LinkedHashMap<>();
      filterArgs.put("filter", filter);
      return subscriptionResolverBuilder(config, readModelClass).resolve(parent, filterArgs, context, info);
    };
  }

  /**
   * {@code subscriptionResolverBuilder(config, readModelClass)}: the filtered subscription. It requires a
   * connection ({@code Missing "connectionID". It is required for subscriptions}), stores the subscription through
   * {@code BoosterReadModelsReader.subscribe} when {@code context.storeSubscriptions} is set (client requests) and
   * returns the read models of {@code context.pubSub} as a {@link IteratorPublisher publisher} (graphql-java's form of
   * the TypeScript async iterator).
   *
   * @param config the configuration
   * @param readModelClass the read model class
   * @return the subscription resolver
   */
  public static GraphQLFieldResolver subscriptionResolverBuilder(BoosterConfig config, Class<?> readModelClass) {
    return (parent, args, context, info) -> {
      if (context == null || !Js.isTruthy(context.connectionID())) {
        throw new IllegalStateException("Missing \"connectionID\". It is required for subscriptions");
      }
      ReadModelRequestEnvelope readModelRequestEnvelope = toReadModelRequestEnvelope(readModelClass, args, context,
          false, null);
      if (context.storeSubscriptions()) {
        readModelsReader().subscribe(context.connectionID(), readModelRequestEnvelope, context.operation());
      }
      Iterator<Object> iterator = context.pubSub().asyncIterator(readModelRequestEnvelope, config);
      return IteratorPublisher.of(iterator);
    };
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Envelopes
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code toReadModelByIdRequestEnvelope(readModelClass, args, context, sequenceKeyName?)} (private in TypeScript).
   *
   * @param readModelClass the read model class
   * @param args the by-id arguments
   * @param context the resolver context
   * @param sequenceKeyName the sequence key property, or {@code null}
   * @return the request
   */
  static ReadModelRequestEnvelope toReadModelByIdRequestEnvelope(Class<?> readModelClass, Map<String, Object> args,
      GraphQLResolverContext context, String sequenceKeyName) {
    UUID id = args.get("id") == null ? null : UUID.of(args.get("id").toString());
    ReadModelRequestEnvelope.Key key;
    if (Js.isTruthy(sequenceKeyName)) {
      Object value = args.get(sequenceKeyName);
      key = new ReadModelRequestEnvelope.Key(id,
          new SequenceKey(sequenceKeyName, value == null ? null : TimeKey.of(value.toString())));
    } else {
      key = new ReadModelRequestEnvelope.Key(id, null);
    }
    return ReadModelRequestEnvelope.builder()
        .currentUser(context == null ? null : context.user())
        .requestID(context == null ? null : context.requestID())
        .type(readModelClass)
        .className(readModelClass.getSimpleName())
        .key(key)
        .version(1) // TODO: How to pass the version through GraphQL?
        .filters(new LinkedHashMap<>())
        .sortBy(new LinkedHashMap<>())
        .build();
  }

  static ReadModelRequestEnvelope toReadModelRequestEnvelope(Class<?> readModelClass, Map<String, Object> args,
      GraphQLResolverContext context, boolean paginatedVersion, List<String> select) {
    return ReadModelRequestEnvelope.builder()
        .requestID(context == null ? null : context.requestID())
        .currentUser(context == null ? null : context.user())
        .type(readModelClass)
        .className(readModelClass.getSimpleName())
        .filters(rawMapOrEmpty(args.get("filter")))
        .sortBy(rawMapOrEmpty(args.get("sortBy")))
        .limit(args.get("limit") instanceof Number limit ? limit.intValue() : null)
        .afterCursor(args.get("afterCursor"))
        .paginatedVersion(paginatedVersion)
        .version(1) // TODO: How to pass the version through GraphQL?
        .select(select)
        .build();
  }

  private static EventSearchRequest toEventSearchRequest(Map<String, Object> args, GraphQLResolverContext context) {
    return EventSearchRequest.builder()
        .requestID(context == null ? null : context.requestID())
        .currentUser(context == null ? null : context.user())
        .parameters(EventSearchParameters.fromMap(args))
        .build();
  }

  @SuppressWarnings("unchecked")
  private static CommandEnvelope toCommandEnvelope(String typeName, Object value, GraphQLResolverContext context) {
    ContextEnvelope.Request request = context == null || context.context() == null ? null
        : context.context().request();
    return CommandEnvelope.builder()
        .requestID(context == null ? null : context.requestID())
        .currentUser(context == null ? null : context.user())
        .typeName(typeName)
        .value(value instanceof Map<?, ?> map ? (Map<String, Object>) map : null)
        .version(1) // TODO: How to pass the version through GraphQL?
        .context(new ContextEnvelope(
            new ContextEnvelope.Request(request == null ? null : request.headers(), request == null ? null
                : request.body()),
            context))
        .build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> rawMapOrEmpty(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Selected fields
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code getFields(info)}: the paths selected under the resolved field, used as the {@code select} projection. For
   * {@code { CartReadModel { id cartItems { id quantity } } }} it returns
   * {@code ["id", "cartItems[].id", "cartItems[].quantity"]}: a path segment gets the {@code []} suffix when the field
   * is a list of objects; aliases are ignored (the field name is used), unknown fields such as {@code __typename} are
   * skipped, fragment spreads are inlined at the current path, inline fragments switch to the object type of their
   * condition; there is no de-duplication and the order is the document order. The root field is always looked up in
   * the {@code Query} type, as in TypeScript.
   *
   * @param info the environment of the resolver, or {@code null}
   * @return the selected paths (empty when nothing is selected)
   */
  static List<String> getFields(DataFetchingEnvironment info) {
    List<String> fields = new ArrayList<>();
    if (info == null || info.getMergedField() == null || info.getGraphQLSchema() == null) {
      return fields;
    }
    Map<String, FragmentDefinition> fragments = info.getFragmentsByName() == null ? Map.of()
        : info.getFragmentsByName();
    GraphQLSchema graphQLSchema = info.getGraphQLSchema();
    for (Field fieldNode : info.getMergedField().getFields()) {
      String firstField = fieldNode.getName();
      GraphQLFieldDefinition firstFieldDefinition = graphQLSchema.getQueryType().getFieldDefinition(firstField);
      if (firstFieldDefinition == null) {
        continue;
      }
      GraphQLType rootType = GraphQLTypeUtil.unwrapAll(firstFieldDefinition.getType());
      if (fieldNode.getSelectionSet() != null) {
        fields.addAll(extractFields(fieldNode.getSelectionSet(), List.of(), rootType, fragments, graphQLSchema));
      }
    }
    return fields;
  }

  private static boolean isList(GraphQLType type) {
    if (type instanceof GraphQLNonNull nonNull) {
      return nonNull.getWrappedType() instanceof GraphQLList;
    }
    return type instanceof GraphQLList;
  }

  private static List<String> extractFields(SelectionSet selectionSet, List<String> path, GraphQLType parentType,
      Map<String, FragmentDefinition> fragments, GraphQLSchema graphQLSchema) {
    List<String> subFields = new ArrayList<>();
    if (selectionSet == null || selectionSet.getSelections() == null) {
      return subFields;
    }
    for (Selection<?> selection : selectionSet.getSelections()) {
      switch (selection) {
        case Field fieldSelection -> {
          String fieldName = fieldSelection.getName();
          GraphQLFieldDefinition field = parentType instanceof GraphQLFieldsContainer container
              ? container.getFieldDefinition(fieldName)
              : null;
          if (field == null) {
            continue;
          }
          GraphQLType fieldType = GraphQLTypeUtil.unwrapAll(field.getType());
          List<String> currentPath = new ArrayList<>(path);
          currentPath.add(fieldName);
          if (isList(field.getType()) && fieldType instanceof GraphQLObjectType) {
            currentPath.set(currentPath.size() - 1, currentPath.getLast() + "[]");
          }
          if (fieldSelection.getSelectionSet() == null) {
            subFields.add(String.join(".", currentPath));
          } else {
            GraphQLType nextParentType = fieldType instanceof GraphQLObjectType ? fieldType : parentType;
            subFields.addAll(extractFields(fieldSelection.getSelectionSet(), currentPath, nextParentType, fragments,
                graphQLSchema));
          }
        }
        case FragmentSpread spread -> {
          FragmentDefinition fragment = fragments.get(spread.getName());
          if (fragment != null) {
            subFields.addAll(extractFields(fragment.getSelectionSet(), path, parentType, fragments, graphQLSchema));
          }
        }
        case InlineFragment inlineFragment -> {
          GraphQLType inlineFragmentType = inlineFragment.getTypeCondition() != null
              ? graphQLSchema.getType(inlineFragment.getTypeCondition().getName())
              : parentType;
          if (inlineFragmentType instanceof GraphQLObjectType) {
            subFields.addAll(extractFields(inlineFragment.getSelectionSet(), path, inlineFragmentType, fragments,
                graphQLSchema));
          }
        }
        default -> {
          // other selections do not exist in GraphQL
        }
      }
    }
    return subFields;
  }

  private static String fieldName(DataFetchingEnvironment info) {
    if (info == null) {
      return null;
    }
    if (info.getFieldDefinition() != null) {
      return info.getFieldDefinition().getName();
    }
    return info.getMergedField() == null ? null : info.getMergedField().getName();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Collaborators
  // ---------------------------------------------------------------------------------------------------------------

  private static BoosterCommandDispatcher commandsDispatcher() {
    return required(commandsDispatcher);
  }

  private static BoosterQueryDispatcher queriesDispatcher() {
    return required(queriesDispatcher);
  }

  private static BoosterReadModelsReader readModelsReader() {
    return required(readModelsReader);
  }

  private static BoosterEventsReader eventsReader() {
    return required(eventsReader);
  }

  private static <T> T required(T collaborator) {
    if (collaborator == null) {
      throw new IllegalStateException(
          "The GraphQL resolvers need the schema: call GraphQLGenerator.generateSchema(config) first");
    }
    return collaborator;
  }
}
