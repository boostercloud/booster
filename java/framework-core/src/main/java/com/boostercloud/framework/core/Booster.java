package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.core.coreconcepts.datamigration.entities.BoosterDataMigrationEntity;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationStarted;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterEntityMigrated;
import com.boostercloud.framework.core.coreconcepts.touchentity.events.BoosterEntityTouched;
import com.boostercloud.framework.core.services.EventStore;
import com.boostercloud.framework.core.services.ReadModelSearcher;
import com.boostercloud.framework.core.services.tokenverifiers.JwksUriTokenVerifier;
import com.boostercloud.framework.core.services.tokenverifiers.JwtEnvVars;
import com.boostercloud.framework.types.BoosterApp;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.searcher.Searcher;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Port of {@code booster.ts}: the global facade of a Booster application. It holds the one {@link BoosterConfig} of
 * the process, lets the application configure it, boots the application ({@link #start(Class)}), runs the
 * command-line actions ({@link #run(Class, String[])}) and offers read helpers.
 *
 * <p>The configuration is created on first use for the environment named by the {@code booster.env} system property
 * or the {@code BOOSTER_ENV} variable ({@link EnvironmentVariables#boosterEnvironment()}); without one, every access
 * fails with {@value #MISSING_ENVIRONMENT_MESSAGE} (TypeScript fails when the module is loaded). All members are
 * static and thread-safe; registration happens during {@link #start(Class)} and the registries are only read
 * afterwards.
 */
public final class Booster {

  /** Error message when no environment is selected. */
  public static final String MISSING_ENVIRONMENT_MESSAGE =
      "Booster environment is missing. You need to provide an environment to configure your Booster project";

  /** The warning printed when the JWT verifier is loaded from the legacy environment variables. */
  public static final String TOKEN_VERIFIER_FROM_ENV_DEPRECATION =
      "Deprecation notice: Implicitly loading the JWT token verifier options from default environment variables is"
          + " deprecated. Please set your application's `config.tokenVerifiers` options explicitly in your"
          + " `src/config/config.ts` file.";

  private static final Object LOCK = new Object();
  private static final Set<String> CONFIGURED_ENVIRONMENTS = Collections.synchronizedSet(new LinkedHashSet<>());
  private static volatile BoosterConfig config;

  private Booster() {}

  // ---------------------------------------------------------------------------------------------------------------
  // Configuration
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code Booster.config}: the configuration of the current environment, created on first use.
   *
   * @return the configuration
   * @throws IllegalStateException {@value #MISSING_ENVIRONMENT_MESSAGE} when no environment is selected
   */
  public static BoosterConfig config() {
    BoosterConfig current = config;
    if (current != null) {
      return current;
    }
    synchronized (LOCK) {
      if (config == null) {
        String environment = EnvironmentVariables.boosterEnvironment()
            .orElseThrow(() -> new IllegalStateException(MISSING_ENVIRONMENT_MESSAGE));
        config = new BoosterConfig(environment);
      }
      return config;
    }
  }

  /**
   * The configuration when an environment is selected, without failing otherwise (used by tracing, which TypeScript
   * skips when there is no configuration).
   *
   * @return the configuration, or empty when no environment is selected
   */
  public static Optional<BoosterConfig> findConfig() {
    BoosterConfig current = config;
    if (current != null) {
      return Optional.of(current);
    }
    if (EnvironmentVariables.boosterEnvironment().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(config());
  }

  /**
   * {@code configureCurrentEnv(configurator)}: runs the configurator on the configuration, unconditionally.
   *
   * @param configurator the configurator
   */
  public static void configureCurrentEnv(Consumer<BoosterConfig> configurator) {
    configurator.accept(config());
  }

  /**
   * {@code configure(environment, configurator)}: records the environment as configured and runs the configurator
   * when it is the current environment. Several blocks for one environment run in call order.
   *
   * @param environment an environment name
   * @param configurator the configurator
   */
  public static void configure(String environment, Consumer<BoosterConfig> configurator) {
    CONFIGURED_ENVIRONMENTS.add(environment);
    BoosterConfig current = config();
    if (current.environmentName.equals(environment)) {
      configurator.accept(current);
    }
  }

  /**
   * {@code configuredEnvironments}.
   *
   * @return the environments passed to {@link #configure(String, Consumer)}, in order (a snapshot)
   */
  public static Set<String> configuredEnvironments() {
    synchronized (CONFIGURED_ENVIRONMENTS) {
      return Collections.unmodifiableSet(new LinkedHashSet<>(CONFIGURED_ENVIRONMENTS));
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Start-up
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code start(codeRootPath)}: boots the application whose classes live in the package of {@code rootClass} (and
   * its sub-packages).
   *
   * <ol>
   *   <li>sets {@code config.userProjectRootPath} (the project directory, see {@link #projectRootPath(Class)});</li>
   *   <li>{@link Importer#importUserProjectFiles(Class)}: runs every {@link BoosterConfigurator}, then registers every
   *       annotated class;</li>
   *   <li>registers the built-in concepts (data migrations, touched entities), overwriting user entries with the same
   *       names;</li>
   *   <li>adds the legacy JWKS token verifier when {@code BOOSTER_JWT_ISSUER}, {@code BOOSTER_JWKS_URI} and
   *       {@code BOOSTER_ROLES_CLAIM} are set (deprecated);</li>
   *   <li>{@code config.validate()};</li>
   *   <li>registers the application ({@link BoosterUserApp}) in {@link UserAppRegistry} for the providers.</li>
   * </ol>
   *
   * <p>Classes are imported once per process (TypeScript's {@code require} cache): calling {@code start} again does
   * not register them twice. The {@code BOOSTER_CLI_HOOK} commands are run by {@link #run(Class, String[])}.
   *
   * @param rootClass a class of the application's root package, usually the main class
   */
  public static void start(Class<?> rootClass) {
    BoosterConfig current = config();
    current.setUserProjectRootPath(projectRootPath(rootClass));
    Importer.importUserProjectFiles(rootClass);
    configureBoosterConcepts();
    loadTokenVerifierFromEnv();
    current.validate();
    UserAppRegistry.register(new BoosterUserApp());
  }

  /**
   * The entry point of a Booster application ({@code public static void main(String[] args)}): starts it and runs
   * the requested action ({@code start [--port N]}, {@code synth}, {@code deploy}, {@code nuke}, or the injected CLI
   * commands when {@code BOOSTER_CLI_HOOK=true}), following the contract of the {@code boost} CLI. Exits the JVM with
   * the action's exit code, except after a successful {@code start}, which returns when the server stops.
   *
   * @param mainClass the main class (its package is scanned)
   * @param args the command-line arguments
   */
  public static void run(Class<?> mainClass, String[] args) {
    BoosterRunner.Outcome outcome = BoosterRunner.run(mainClass, args, System.out, System.err);
    if (outcome.exitJvm()) {
      System.exit(outcome.exitCode());
    }
  }

  /**
   * The project directory of the application: the directory of the compiled classes of {@code rootClass} (or of its
   * jar) without a trailing {@code target/classes} (TypeScript strips the trailing {@code dist}), with a trailing
   * separator as in TypeScript; the working directory when the location is unknown.
   *
   * @param rootClass the root class
   * @return the project root path
   */
  static String projectRootPath(Class<?> rootClass) {
    Path location = codeLocation(rootClass).orElse(Path.of(System.getProperty("user.dir")));
    if (Files.isRegularFile(location)) {
      location = location.getParent();
    }
    Path normalized = location.toAbsolutePath().normalize();
    if (normalized.endsWith(Path.of("target", "classes")) || normalized.endsWith(Path.of("target", "test-classes"))) {
      normalized = normalized.getParent().getParent();
    } else if (normalized.endsWith("target")) {
      normalized = normalized.getParent();
    }
    String path = normalized.toString();
    return path.endsWith(File.separator) ? path : path + File.separator;
  }

  private static Optional<Path> codeLocation(Class<?> rootClass) {
    CodeSource codeSource = rootClass.getProtectionDomain().getCodeSource();
    URL url = codeSource == null ? null : codeSource.getLocation();
    if (url == null || !"file".equals(url.getProtocol())) {
      return Optional.empty();
    }
    try {
      return Optional.of(Path.of(url.toURI()));
    } catch (URISyntaxException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** {@code configureBoosterConcepts()}: {@code configureDataMigrations()} then {@code configureTouchEntities()}. */
  static void configureBoosterConcepts() {
    BoosterConfig current = config();
    current.events.put(BoosterEntityMigrated.class.getSimpleName(), new EventMetadata(BoosterEntityMigrated.class));
    current.events.put(BoosterDataMigrationStarted.class.getSimpleName(), new EventMetadata(BoosterDataMigrationStarted.class));
    current.reducers.put(BoosterDataMigrationStarted.class.getSimpleName(),
        new ReducerMetadata(BoosterDataMigrationEntity.class, "started"));
    current.events.put(BoosterDataMigrationFinished.class.getSimpleName(), new EventMetadata(BoosterDataMigrationFinished.class));
    current.reducers.put(BoosterDataMigrationFinished.class.getSimpleName(),
        new ReducerMetadata(BoosterDataMigrationEntity.class, "finished"));
    current.entities.put(BoosterDataMigrationEntity.class.getSimpleName(),
        new EntityMetadata(BoosterDataMigrationEntity.class, BoosterAuthorizer.DENY_ACCESS));
    current.events.put(BoosterEntityTouched.class.getSimpleName(), new EventMetadata(BoosterEntityTouched.class));
  }

  /**
   * {@code loadTokenVerifierFromEnv()}: when the three legacy variables are set (non-empty), prints a deprecation
   * notice to the standard error and appends a {@link JwksUriTokenVerifier} to {@code config.tokenVerifiers}.
   *
   * @deprecated [EOL v3] set {@code config.tokenVerifiers} explicitly
   */
  @Deprecated
  static void loadTokenVerifierFromEnv() {
    String issuer = EnvironmentVariables.getOrDefault(JwtEnvVars.BOOSTER_JWT_ISSUER, "");
    String jwksUri = EnvironmentVariables.getOrDefault(JwtEnvVars.BOOSTER_JWKS_URI, "");
    String rolesClaim = EnvironmentVariables.getOrDefault(JwtEnvVars.BOOSTER_ROLES_CLAIM, "");
    if (!issuer.isEmpty() && !jwksUri.isEmpty() && !rolesClaim.isEmpty()) {
      System.err.println(TOKEN_VERIFIER_FROM_ENV_DEPRECATION);
      BoosterConfig current = config();
      if (current.tokenVerifiers == null) {
        current.tokenVerifiers = new ArrayList<>();
      }
      current.tokenVerifiers.add(new JwksUriTokenVerifier(issuer, jwksUri, rolesClaim));
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Read helpers
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code readModel(readModelClass)}: a {@link Searcher} for the read model (no I/O until a terminal method runs):
   * {@code Booster.readModel(CartReadModel.class).filter(Map.of("id", Map.of("eq", id))).search()}.
   *
   * @param readModelClass the read model class
   * @param <T> the read model type
   * @return a new searcher
   */
  public static <T> Searcher<T> readModel(Class<T> readModelClass) {
    return ReadModelSearcher.readModelSearcher(config(), readModelClass);
  }

  /**
   * {@code events(request)}: searches events; values are instances of the registered event/notification classes.
   *
   * @param request the search parameters
   * @return the events
   */
  public static List<EventSearchResponse> events(EventSearchParameters request) {
    return BoosterEventSearch.eventSearch(config(), request);
  }

  /**
   * {@code entitiesIDs(entityTypeName, limit, afterCursor)}: one page of the ids of an entity type (note the
   * provider's parameter order).
   *
   * @param entityTypeName the entity class name
   * @param limit the page size
   * @param afterCursor the cursor of the previous page, or {@code null}
   * @return the page
   */
  public static PaginatedEntitiesIdsResult entitiesIDs(String entityTypeName, int limit, Map<String, String> afterCursor) {
    BoosterConfig current = config();
    return current.provider().events().searchEntitiesIDs(current, limit, afterCursor, entityTypeName);
  }

  /**
   * {@code deleteEvent(parameters)}.
   *
   * @param parameters which event
   * @return whether something was deleted
   */
  public static boolean deleteEvent(EventDeleteParameters parameters) {
    return BoosterDeleteEventDispatcher.deleteEvent(config(), parameters);
  }

  /**
   * {@code entity(entityClass, entityID)}: the current state of an entity (no authorization).
   *
   * @param entityClass the entity class
   * @param entityID the entity id
   * @param <T> the entity type
   * @return the entity, empty when it has no events
   */
  public static <T> Optional<T> entity(Class<T> entityClass, UUID entityID) {
    EventStore eventStore = new EventStore(config());
    return eventStore.fetchEntitySnapshot(entityClass.getSimpleName(), entityID)
        .map(envelope -> Instances.createInstance(entityClass, envelope.value()));
  }

  /**
   * The facade as a {@link BoosterApp} instance (TypeScript's {@code Booster} class satisfies that interface
   * structurally).
   *
   * @return a view delegating to the static members
   */
  public static BoosterApp app() {
    return BoosterAppView.INSTANCE;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Test hook: forgets the configuration (the next access creates a new one from the current environment), the
   * configured environments, the imported classes and the registered application. The TypeScript tests delete and
   * reassign fields of the singleton instead.
   */
  public static void reset() {
    synchronized (LOCK) {
      config = null;
      CONFIGURED_ENVIRONMENTS.clear();
      Importer.reset();
      UserAppRegistry.clear();
    }
  }

  private enum BoosterAppView implements BoosterApp {
    INSTANCE;

    @Override
    public void start(Class<?> applicationClass) {
      Booster.start(applicationClass);
    }

    @Override
    public BoosterConfig config() {
      return Booster.config();
    }

    @Override
    public void configure(String environment, Consumer<BoosterConfig> configurator) {
      Booster.configure(environment, configurator);
    }

    @Override
    public void configureCurrentEnv(Consumer<BoosterConfig> configurator) {
      Booster.configureCurrentEnv(configurator);
    }

    @Override
    public <T> Optional<T> entity(Class<T> entityClass, UUID entityID) {
      return Booster.entity(entityClass, entityID);
    }

    @Override
    public <T> Searcher<T> readModel(Class<T> readModelClass) {
      return Booster.readModel(readModelClass);
    }

    @Override
    public List<EventSearchResponse> events(EventSearchParameters request) {
      return Booster.events(request);
    }

    @Override
    public PaginatedEntitiesIdsResult entitiesIDs(String entityTypeName, int limit, Map<String, String> afterCursor) {
      return Booster.entitiesIDs(entityTypeName, limit, afterCursor);
    }

    @Override
    public boolean deleteEvent(EventDeleteParameters parameters) {
      return Booster.deleteEvent(parameters);
    }

    @Override
    public Set<String> configuredEnvironments() {
      return Booster.configuredEnvironments();
    }
  }
}
