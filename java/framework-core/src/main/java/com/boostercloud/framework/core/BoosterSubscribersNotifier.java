package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.core.internal.Expirations;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.services.graphql.GraphQLGenerator;
import com.boostercloud.framework.core.services.graphql.GraphQLResolverContext;
import com.boostercloud.framework.core.services.pubsub.FilteredReadModelPubSub;
import com.boostercloud.framework.core.services.pubsub.ReadModelPubSub;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.graphql.GraphQLData;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import com.boostercloud.framework.core.services.graphql.GraphQLExecution;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Port of {@code booster-subscribers-notifier.ts}: the entry point invoked by the provider when read models change;
 * re-executes every matching subscription against the changed read models and pushes the results to the
 * subscribers' connections. Traced as {@code DISPATCH_SUBSCRIBER_NOTIFIER}.
 *
 * <ol>
 *   <li>{@code provider.readModels().rawToEnvelopes(config, request)}: the changed read models;</li>
 *   <li>the stored subscriptions of every changed read model type ({@code fetchSubscriptions}, one call per type in
 *       first-appearance order, concurrently);</li>
 *   <li>a {@link FilteredReadModelPubSub} with the changed read models as instances of their classes ({@code Could not
 *       get information about read model with name: <name>} for an unregistered one);</li>
 *   <li>every subscription concurrently: its stored GraphQL document is parsed, validated and executed with
 *       {@link GraphQLExecution#execute} (the dispatcher's execution: resolver errors keep their message; variables
 *       from the stored operation, no operation name, as in TypeScript; events kept in order) with a
 *       {@link GraphQLResolverContext} whose {@code pubSub} is the filtered pub-sub and {@code storeSubscriptions} is
 *       {@code false}; every event of the resulting stream is sent to the subscription's connection as
 *       {@code { type: "data", id: operation.id, payload: { data } }}.</li>
 * </ol>
 *
 * <p>{@link #dispatch} never throws: any failure (aggregated across subscriptions) is logged with
 * {@code logger.error}. Java addition: a subscription whose expiration time has passed, or whose user's token
 * ({@code exp} claim) has expired, is skipped and deleted. The authorizer and the stored filters of a subscription are
 * not checked here (the filters are rebuilt by the subscription resolver from the stored document, and the read
 * model's before hooks run again with the stored user).
 *
 * <p>Contract with the subscription resolvers: the {@link GraphQLResolverContext} is the local context and is stored
 * in the {@code GraphQLContext} under the key {@code GraphQLResolverContext.class} (read it with
 * {@code Common.resolverContext(environment)}); a subscription data fetcher returns
 * {@code ReadModelPubSub.toPublisher(context.pubSub().asyncIterator(envelope, config))} (any
 * {@code org.reactivestreams.Publisher} or {@code java.util.concurrent.Flow.Publisher} works).
 */
public class BoosterSubscribersNotifier {

  private final BoosterConfig config;
  private final GraphQLSchema graphQLSchema;

  /**
   * Builds (or reuses) the application's GraphQL schema ({@code GraphQLGenerator.generateSchema(config)}).
   *
   * @param config the configuration
   */
  public BoosterSubscribersNotifier(BoosterConfig config) {
    this(config, GraphQLGenerator.generateSchema(config));
  }

  /**
   * A notifier for a given schema (tests and embedders).
   *
   * @param config the configuration
   * @param graphQLSchema the schema the stored subscription documents are executed against
   */
  public BoosterSubscribersNotifier(BoosterConfig config, GraphQLSchema graphQLSchema) {
    this.config = config;
    this.graphQLSchema = Objects.requireNonNull(graphQLSchema, "graphQLSchema");
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code dispatch(request)}.
   *
   * @param request the provider-specific request
   */
  public void dispatch(Object request) {
    Tracer.trace(TraceActionTypes.DISPATCH_SUBSCRIBER_NOTIFIER.value(), BoosterSubscribersNotifier.class, "dispatch",
        Arrays.asList(request), () -> {
          Logger logger = Loggers.getLogger(config, "BoosterSubscribersNotifier#dispatch");
          try {
            logger.debug("Received the following event for subscription dispatching: ", request);
            List<ReadModelEnvelope> readModelEnvelopes = config.provider().readModels().rawToEnvelopes(config, request);
            logger.debug("[SubsciptionDispatcher] The following ReadModels were updated: ", readModelEnvelopes);
            List<SubscriptionEnvelope> subscriptions = dropExpired(getSubscriptions(readModelEnvelopes));
            logger.debug("Found the following subscriptions for those read models: ", subscriptions);

            ReadModelPubSub pubSub = getPubSub(readModelEnvelopes);
            Promises.mapAllSettledAndFulfilled(subscriptions, subscription -> runSubscriptionAndNotify(pubSub, subscription));
          } catch (Exception e) {
            logger.error(e);
          }
          return null;
        });
  }

  private Object getReadModelInstance(ReadModelEnvelope envelope) {
    ReadModelMetadata readModelMetadata = config.readModels.get(envelope.typeName());
    if (readModelMetadata == null) {
      throw new IllegalStateException("Could not get information about read model with name: " + envelope.typeName());
    }
    return Instances.createInstance(readModelMetadata.type(), envelope.value());
  }

  private List<SubscriptionEnvelope> getSubscriptions(List<ReadModelEnvelope> readModelEnvelopes) {
    LinkedHashSet<String> readModelUniqueNames = new LinkedHashSet<>();
    for (ReadModelEnvelope readModelEnvelope : readModelEnvelopes) {
      readModelUniqueNames.add(readModelEnvelope.typeName());
    }
    List<Callable<List<SubscriptionEnvelope>>> fetches = new ArrayList<>();
    for (String name : readModelUniqueNames) {
      fetches.add(() -> config.provider().readModels().fetchSubscriptions(config, name));
    }
    List<SubscriptionEnvelope> subscriptions = new ArrayList<>();
    for (List<SubscriptionEnvelope> subscriptionSet : Promises.runAll(fetches)) {
      if (subscriptionSet != null) {
        subscriptions.addAll(subscriptionSet);
      }
    }
    return subscriptions;
  }

  /**
   * Java addition: a subscription whose {@code expirationTime} has passed, or whose user's token has expired, is not
   * run and is deleted (best effort). TypeScript notifies every stored subscription and relies on the provider to
   * remove the expired ones, which only DynamoDB's TTL did.
   */
  private List<SubscriptionEnvelope> dropExpired(List<SubscriptionEnvelope> subscriptions) {
    Logger logger = Loggers.getLogger(config, "BoosterSubscribersNotifier#dropExpired");
    List<SubscriptionEnvelope> live = new ArrayList<>();
    for (SubscriptionEnvelope subscription : subscriptions) {
      if (!Expirations.hasPassed(subscription.expirationTime())
          && !Expirations.tokenHasExpired(subscription.currentUser())) {
        live.add(subscription);
        continue;
      }
      String subscriptionID = subscription.operation() == null ? null : subscription.operation().id();
      logger.debug("Deleting the expired subscription " + subscriptionID + " of connection "
          + subscription.connectionID());
      try {
        config.provider().readModels().deleteSubscription(config, subscription.connectionID(), subscriptionID);
      } catch (RuntimeException e) {
        logger.warn("Could not delete the expired subscription " + subscriptionID + " of connection "
            + subscription.connectionID() + ": ", e);
      }
    }
    return live;
  }

  private ReadModelPubSub getPubSub(List<ReadModelEnvelope> readModelEnvelopes) {
    List<Object> readModelInstances = new ArrayList<>();
    for (ReadModelEnvelope envelope : readModelEnvelopes) {
      readModelInstances.add(getReadModelInstance(envelope));
    }
    return new FilteredReadModelPubSub(readModelInstances);
  }

  @SuppressWarnings("unchecked")
  private Object runSubscriptionAndNotify(ReadModelPubSub pubSub, SubscriptionEnvelope subscription) {
    Logger logger = Loggers.getLogger(config, "BoosterSubscribersNotifier#runSubscriptionAndNotify");
    GraphQLResolverContext context = new GraphQLResolverContext(subscription.connectionID(), new LinkedHashMap<>(),
        subscription.operation(), subscription.requestID(), subscription.currentUser(),
        false, // We don't store the subscription again, just get the result now
        pubSub, null);
    String query = subscription.operation().query();
    Document document = parseSubscriptionQuery(query);
    logger.debug("Running subscription with context: ", context);
    Map<String, Object> variables = subscription.operation().variables() == null
        ? new LinkedHashMap<>()
        : subscription.operation().variables();
    ExecutionResult result = GraphQLExecution.execute(graphQLSchema, document, query, context, variables, null);
    if (result.getData() instanceof Publisher<?> iterator) {
      // It is a stream of results
      return processSubscriptionsIterator((Publisher<ExecutionResult>) iterator, subscription);
    }
    // If "subscribe" returns an ExecutionResult (instead of a stream) the subscription failed.
    throw new GraphQLErrors(result.getErrors());
  }

  private Document parseSubscriptionQuery(String query) {
    Document document = Parser.parse(query);
    // We probably don't need to validate this again, as it was validated before storing it. BUT! It's always better to fail early
    List<ValidationError> errors = GraphQLExecution.validate(graphQLSchema, document);
    if (!errors.isEmpty()) {
      throw new GraphQLErrors(new ArrayList<>(errors));
    }
    return document;
  }

  private Object processSubscriptionsIterator(Publisher<ExecutionResult> iterator, SubscriptionEnvelope subscription) {
    List<CompletableFuture<Void>> notificationPromises = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch finished = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    iterator.subscribe(new Subscriber<>() {
      // TypeScript starts every sendMessage in the order of the stream. Here each notification of the subscription
      // starts once the previous one is done (onNext is signalled serially), so the connection gets them in order
      private CompletableFuture<Void> previous = CompletableFuture.completedFuture(null);

      @Override
      public void onSubscribe(Subscription stream) {
        stream.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(ExecutionResult result) {
        CompletableFuture<Void> notification = previous.handle((ignored, error) -> null)
            .thenCompose(ignored -> Promises.<Void>start(() -> {
              notifyWithGraphQLResult(subscription, result);
              return null;
            }));
        previous = notification;
        notificationPromises.add(notification);
      }

      @Override
      public void onError(Throwable error) {
        failure.set(error);
        finished.countDown();
      }

      @Override
      public void onComplete() {
        finished.countDown();
      }
    });
    try {
      finished.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CancellationException("Interrupted while running the subscription " + subscription.operation().id());
    }
    if (failure.get() != null) {
      throw com.boostercloud.framework.core.internal.UserCode.rethrow(failure.get());
    }
    List<CompletableFuture<Void>> notifications;
    synchronized (notificationPromises) {
      notifications = new ArrayList<>(notificationPromises);
    }
    return Promises.all(notifications);
  }

  private void notifyWithGraphQLResult(SubscriptionEnvelope subscription, ExecutionResult result) {
    Logger logger = Loggers.getLogger(config, "BoosterSubscribersNotifier#notifyWithGraphQLResult");
    if (result.getErrors() != null && !result.getErrors().isEmpty()) {
      throw new GraphQLErrors(result.getErrors());
    }
    Object readModel = result.getData();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("data", readModel);
    GraphQLData message = new GraphQLData(subscription.operation().id(), payload);
    logger.debug("Notifying connectionID '" + subscription.connectionID() + "' with the following wrappeed read model: ",
        readModel);
    config.provider().connections().sendMessage(config, subscription.connectionID(), message);
    logger.debug("Notifications sent");
  }

  /**
   * The GraphQL errors of a subscription run (TypeScript throws the {@code GraphQLError} array itself): parse or
   * validation errors of the stored document, errors of the subscription resolver, or errors of one event.
   */
  public static final class GraphQLErrors extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient List<GraphQLError> errors;

    /**
     * @param errors the errors
     */
    public GraphQLErrors(List<? extends GraphQLError> errors) {
      super(message(errors));
      this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * @return the GraphQL errors
     */
    public List<GraphQLError> errors() {
      return errors;
    }

    private static String message(List<? extends GraphQLError> errors) {
      if (errors == null || errors.isEmpty()) {
        return "undefined";
      }
      return String.join(",", errors.stream().map(GraphQLError::getMessage).toList());
    }
  }
}
