package com.boostercloud.framework.types;

import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.DataMigrationMetadata;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.EventStreamConfiguration;
import com.boostercloud.framework.types.concepts.GlobalErrorHandlerMetadata;
import com.boostercloud.framework.types.concepts.NotificationMetadata;
import com.boostercloud.framework.types.concepts.ProjectionMetadata;
import com.boostercloud.framework.types.concepts.QueryMetadata;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.RoleMetadata;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.concepts.TokenVerifier;
import com.boostercloud.framework.types.injectable.Injectable;
import com.boostercloud.framework.types.instrumentation.TraceConfiguration;
import com.boostercloud.framework.types.provider.AzureConfiguration;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderLoader;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.framework.types.rockets.RocketFunction;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.SensorConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The configuration and runtime registry of a Booster application. Port of the TypeScript {@code BoosterConfig}: one
 * mutable instance per process ({@code Booster.config}), changed by {@code Booster.configure(env, config -> ...)}
 * blocks and filled by the annotation registrars during {@code Booster.start}.
 *
 * <p>Settings are public fields, as in TypeScript ({@code config.appName = "my-store"}). Registries are
 * insertion-ordered maps keyed by <b>simple class names</b>; they are written during start-up (single-threaded) and
 * only read afterwards, so they need no synchronisation. The lazily loaded {@link #provider()} is thread-safe.
 *
 * <p>Errors thrown here are plain {@link IllegalStateException}s with the TypeScript messages (TypeScript throws plain
 * {@code Error}s).
 */
public class BoosterConfig {

  /** Name of the environment this configuration is for ({@code BOOSTER_ENV}). */
  public final String environmentName;

  /** Minimum level of the messages emitted by {@code getLogger} (default {@link Level#DEBUG}). */
  public Level logLevel = Level.DEBUG;

  /** Prefix of log lines; {@code getLogger} falls back to {@code Booster}. */
  public String logPrefix;

  /** Custom log sink; {@code getLogger} falls back to {@link ConsoleLogger}. */
  public Logger logger;

  private volatile ProviderLibrary provider;

  /** Java package of the provider (e.g. {@code com.boostercloud.framework.provider.local}); see {@link #provider()}. */
  public String providerPackage;

  /** Rockets of the application, passed to the provider and to its infrastructure. */
  public List<RocketDescriptor> rockets;

  /** Base of every resource name (default {@code new-booster-app}). */
  public String appName = "new-booster-app";

  /** Extra files or directories copied into the deployment, relative to the project root. */
  public List<String> assets;

  /** Initial response headers of every GraphQL request (copied per request). */
  public Map<String, String> defaultResponseHeaders = new LinkedHashMap<>();

  /** Commands contributed to the {@code boost} CLI (TypeScript {@code injectable: unknown}). */
  public Injectable injectable;

  /** Subscription and connection lifetimes. */
  public final SubscriptionsConfiguration subscriptions = new SubscriptionsConfiguration();

  /** When {@code false}, GraphQL introspection queries are rejected. */
  public boolean enableGraphQLIntrospection = true;

  private volatile String userProjectRootPath;

  /** Folder of the compiled user code in the TypeScript layout ({@code dist}); kept for parity. */
  public final String codeRelativePath = "dist";

  /** Node/Lambda handler identifier of the event dispatcher ({@code dist/index.boosterEventDispatcher}). */
  public final String eventDispatcherHandler = codeRelativePath + "/index.boosterEventDispatcher";

  /** Handler identifier of the event stream consumer. */
  public final String eventStreamConsumer = codeRelativePath + "/index.boosterConsumeEventStream";

  /** Handler identifier of the event stream producer. */
  public final String eventStreamProducer = codeRelativePath + "/index.boosterProduceEventStream";

  /** Handler identifier of the GraphQL endpoint. */
  public final String serveGraphQLHandler = codeRelativePath + "/index.boosterServeGraphQL";

  /** Handler identifier of the health sensor. */
  public final String sensorHealthHandler = codeRelativePath + "/index.boosterHealth";

  /** Handler identifier of scheduled commands (the deprecated singular name, as in TypeScript). */
  public final String scheduledTaskHandler = codeRelativePath + "/index.boosterTriggerScheduledCommand";

  /** Handler identifier of the subscribers notifier. */
  public final String notifySubscribersHandler = codeRelativePath + "/index.boosterNotifySubscribers";

  /** Handler identifier of the rocket dispatcher. */
  public final String rocketDispatcherHandler = codeRelativePath + "/index.boosterRocketDispatcher";

  /** Azure Functions {@code scriptFile} of the TypeScript layout ({@code ../dist/index.js}); kept for parity. */
  public final String functionRelativePath = "../" + codeRelativePath + "/index.js";

  /** Event class name to metadata. */
  public final Map<String, EventMetadata> events = new LinkedHashMap<>();

  /** Notification class name to metadata. */
  public final Map<String, NotificationMetadata> notifications = new LinkedHashMap<>();

  /** Notification class name to the name of its partition-key property. */
  public final Map<String, String> partitionKeys = new LinkedHashMap<>();

  /** Topic to notification class name (last registration wins). */
  public final Map<String, String> topicToEvent = new LinkedHashMap<>();

  /** Notification class name to topic. */
  public final Map<String, String> eventToTopic = new LinkedHashMap<>();

  /** Entity class name to metadata. */
  public final Map<String, EntityMetadata> entities = new LinkedHashMap<>();

  /** Event class name to the reducer of the entity it belongs to. */
  public final Map<String, ReducerMetadata> reducers = new LinkedHashMap<>();

  /** Command class name to metadata. */
  public final Map<String, CommandMetadata> commandHandlers = new LinkedHashMap<>();

  /** Query class name to metadata. */
  public final Map<String, QueryMetadata> queryHandlers = new LinkedHashMap<>();

  /** Event class name to the event handler classes, in registration order. */
  public final Map<String, List<Class<?>>> eventHandlers = new LinkedHashMap<>();

  /** Read model class name to metadata. */
  public final Map<String, ReadModelMetadata> readModels = new LinkedHashMap<>();

  /** Entity class name to its projections, in registration order. */
  public final Map<String, List<ProjectionMetadata>> projections = new LinkedHashMap<>();

  /** Entity class name to its un-projections, in registration order. */
  public final Map<String, List<ProjectionMetadata>> unProjections = new LinkedHashMap<>();

  /** <b>Read model</b> class name to the name of its sequence-key property. */
  public final Map<String, String> readModelSequenceKeys = new LinkedHashMap<>();

  /** Role class name to metadata. */
  public final Map<String, RoleMetadata> roles = new LinkedHashMap<>();

  /** Concept class name to its schema migrations by {@code toVersion}. */
  public final Map<String, Map<Integer, SchemaMigrationMetadata>> schemaMigrations = new LinkedHashMap<>();

  /** Scheduled command class name to metadata. */
  public final Map<String, ScheduledCommandMetadata> scheduledCommandHandlers = new LinkedHashMap<>();

  /** Data migration class name to metadata. */
  public final Map<String, DataMigrationMetadata> dataMigrationHandlers = new LinkedHashMap<>();

  /** User health indicator id to metadata (reassignable, as in TypeScript). */
  public Map<String, HealthIndicatorMetadata> userHealthIndicators = new LinkedHashMap<>();

  /** Sensor settings (own copy of the defaults). */
  public final SensorConfiguration sensorConfiguration = new SensorConfiguration();

  /** Azure provider tuning. */
  public final AzureConfiguration azureConfiguration = new AzureConfiguration();

  /** The global error handler, or {@code null}. */
  public GlobalErrorHandlerMetadata globalErrorsHandler;

  /** Whether websocket/subscription infrastructure is created. */
  public boolean enableSubscriptions = true;

  /** Class name to the names of its properties hidden from GraphQL ({@code @NonExposed}). */
  public final Map<String, List<String>> nonExposedGraphQLMetadataKey = new LinkedHashMap<>();

  private final Map<String, RocketFunction> rocketFunctionMap = Collections.synchronizedMap(new LinkedHashMap<>());

  /** Time to live of the dispatched-events records, in seconds (default 5 minutes). */
  public int dispatchedEventsTtl = 300;

  /** Tracing settings (disabled by default). */
  public TraceConfiguration traceConfiguration = new TraceConfiguration();

  /** Event stream settings (disabled by default). */
  public EventStreamConfiguration eventStreamConfiguration = EventStreamConfiguration.disabled();

  /** Environment variables set on the deployed functions. */
  public final Map<String, String> env = new LinkedHashMap<>();

  /** JWT verifiers tried for bearer tokens, in order. */
  public List<TokenVerifier> tokenVerifiers = new ArrayList<>();

  /** Retry settings of event store writes. */
  public final RetryConfig eventStoreRetry = RetryConfig.eventStoreDefaults();

  /**
   * @param environmentName the environment this configuration is for (no validation)
   */
  public BoosterConfig(String environmentName) {
    this.environmentName = environmentName;
  }

  /**
   * Cloud resource names, computed on every call.
   *
   * @return the names
   * @throws IllegalStateException {@code Application name cannot be empty} when {@link #appName} is empty
   */
  public ResourceNames resourceNames() {
    if (appName == null || appName.isEmpty()) {
      throw new IllegalStateException("Application name cannot be empty");
    }
    String streamTopic = eventStreamConfiguration != null && eventStreamConfiguration.parameters() != null
            && eventStreamConfiguration.parameters().streamTopic() != null
        ? eventStreamConfiguration.parameters().streamTopic()
        : ResourceNames.DEFAULT_STREAM_TOPIC;
    return ResourceNames.of(appName, streamTopic);
  }

  /**
   * Strips the leading {@code <appName>-app-} from a resource name (unchanged when absent). TypeScript builds an
   * unescaped regular expression from the app name; Java compares the literal prefix.
   *
   * @param resourceName a resource name
   * @return the read model name
   */
  public String readModelNameFromResourceName(String resourceName) {
    String prefix = resourceNames().applicationStack() + "-";
    return resourceName.startsWith(prefix) ? resourceName.substring(prefix.length()) : resourceName;
  }

  /**
   * @return whether at least one role is registered
   */
  public boolean thereAreRoles() {
    return !roles.isEmpty();
  }

  /**
   * The current schema version of a concept: 1 without migrations, else the highest {@code toVersion}. (TypeScript
   * returns {@code -Infinity} for a registered but empty migration map; Java returns 1.)
   *
   * @param className a concept class name
   * @return the version
   */
  public int currentVersionFor(String className) {
    Map<Integer, SchemaMigrationMetadata> migrations = schemaMigrations.get(className);
    if (migrations == null || migrations.isEmpty()) {
      return 1;
    }
    return Collections.max(migrations.keySet());
  }

  /**
   * Validates the configuration: every concept with schema migrations must have one for every version in
   * {@code [2..current]}. Throws on the first gap found, in registration order.
   *
   * @throws IllegalStateException {@code Schema Migrations for '<concept>' are invalid: ...}
   */
  public void validate() {
    for (Map.Entry<String, Map<Integer, SchemaMigrationMetadata>> entry : schemaMigrations.entrySet()) {
      validateConceptSchemaMigrations(entry.getKey(), entry.getValue());
    }
  }

  private void validateConceptSchemaMigrations(String conceptName, Map<Integer, SchemaMigrationMetadata> migrations) {
    int currentVersion = currentVersionFor(conceptName);
    for (int toVersion = 2; toVersion <= currentVersion; toVersion++) {
      if (!migrations.containsKey(toVersion)) {
        throw new IllegalStateException(
            "Schema Migrations for '" + conceptName + "' are invalid: they are missing a migration with toVersion="
                + toVersion + ". There must be a migration for '" + conceptName + "' for every version in the range [2.."
                + currentVersion + "]");
      }
    }
  }

  /**
   * The provider library. On first access, when none was set with {@link #setProvider(ProviderLibrary)} and
   * {@link #providerPackage} is set, it is loaded with {@link ProviderLoader#loadProvider(String, List)} and cached;
   * later changes of {@code providerPackage} or {@code rockets} have no effect. A failed load is not cached.
   *
   * @return the provider
   * @throws IllegalStateException {@code It is required to set a valid provider runtime in your configuration files}
   *     when there is no provider, or the loading error
   */
  public ProviderLibrary provider() {
    ProviderLibrary current = provider;
    if (current != null) {
      return current;
    }
    synchronized (this) {
      if (provider == null && providerPackage != null && !providerPackage.isEmpty()) {
        provider = ProviderLoader.loadProvider(providerPackage, rockets == null ? List.of() : rockets);
      }
      if (provider == null) {
        throw new IllegalStateException("It is required to set a valid provider runtime in your configuration files");
      }
      return provider;
    }
  }

  /**
   * Sets the provider directly. Deprecated in TypeScript (prints a warning on every call), and still the way tests
   * inject a provider.
   *
   * @param provider the provider
   * @deprecated use {@link #providerPackage}
   */
  @Deprecated
  public synchronized void setProvider(ProviderLibrary provider) {
    System.err.println("""

              The usage of the 'config.provider' field is deprecated,
              please use 'config.providerPackage' instead.

              For more information, check out the docs:

              https://docs.boosterframework.com/going-deeper/environment-configuration
            """);
    this.provider = provider;
  }

  /**
   * @return the project root path set by {@code Booster.start}
   * @throws IllegalStateException when it was not set (or is empty)
   */
  public String userProjectRootPath() {
    String path = userProjectRootPath;
    if (path == null || path.isEmpty()) {
      throw new IllegalStateException("Property \"userProjectRootPath\" is not set. Ensure you have called \"Booster.start\"");
    }
    return path;
  }

  /**
   * @param userProjectRootPath the project root path (no validation)
   */
  public void setUserProjectRootPath(String userProjectRootPath) {
    this.userProjectRootPath = userProjectRootPath;
  }

  /**
   * Reads a process environment variable (through {@link EnvironmentVariables}). An empty value is valid.
   *
   * @param varName the variable name
   * @return its value
   * @throws IllegalStateException {@code Missing environment variable '<name>'} when unset
   */
  public String mustGetEnvironmentVar(String varName) {
    return EnvironmentVariables.get(varName)
        .orElseThrow(() -> new IllegalStateException("Missing environment variable '" + varName + "'"));
  }

  /**
   * Registers a rocket function.
   *
   * @param id the function id
   * @param function the function
   * @throws IllegalStateException when another function is registered under the same id
   */
  public void registerRocketFunction(String id, RocketFunction function) {
    Objects.requireNonNull(function, "function");
    synchronized (rocketFunctionMap) {
      RocketFunction current = rocketFunctionMap.get(id);
      if (current != null) {
        throw new IllegalStateException("Error registering rocket function with id " + id
            + ": There is already a rocket function registered under the same ID, \"" + functionName(current) + "\"");
      }
      rocketFunctionMap.put(id, function);
    }
  }

  /**
   * @param id a function id
   * @return the function registered under that id
   */
  public Optional<RocketFunction> getRegisteredRocketFunction(String id) {
    return Optional.ofNullable(rocketFunctionMap.get(id));
  }

  /**
   * @return a snapshot of the registered rocket functions by id
   */
  public Map<String, RocketFunction> registeredRocketFunctions() {
    synchronized (rocketFunctionMap) {
      return Map.copyOf(rocketFunctionMap);
    }
  }

  /** Name of a function for messages: the class name, empty for lambdas (JavaScript's anonymous functions). */
  private static String functionName(RocketFunction function) {
    Class<?> type = function.getClass();
    return type.isHidden() || type.isSynthetic() || type.isAnonymousClass() ? "" : type.getSimpleName();
  }
}
