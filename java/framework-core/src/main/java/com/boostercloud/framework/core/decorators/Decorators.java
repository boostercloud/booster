package com.boostercloud.framework.core.decorators;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.core.sensor.health.healthindicators.DefaultBoosterHealthIndicators;
import com.boostercloud.framework.types.DataMigrationParameters;
import com.boostercloud.framework.types.ScheduleInterface;
import com.boostercloud.framework.types.concepts.AuthMetadata;
import com.boostercloud.framework.types.concepts.CommandAuthorizer;
import com.boostercloud.framework.types.concepts.CommandBeforeFunction;
import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.DataMigrationMetadata;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.EventStreamAuthorizer;
import com.boostercloud.framework.types.concepts.GlobalErrorHandlerMetadata;
import com.boostercloud.framework.types.concepts.JoinKey;
import com.boostercloud.framework.types.concepts.NotificationMetadata;
import com.boostercloud.framework.types.concepts.ProjectionMetadata;
import com.boostercloud.framework.types.concepts.QueryAuthorizer;
import com.boostercloud.framework.types.concepts.QueryBeforeFunction;
import com.boostercloud.framework.types.concepts.QueryMetadata;
import com.boostercloud.framework.types.concepts.ReadModelAuthorizer;
import com.boostercloud.framework.types.concepts.ReadModelBeforeFunction;
import com.boostercloud.framework.types.concepts.ReadModelJoinKeyFunction;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.RoleMetadata;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.concepts.SignUpMethod;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfiguration;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.MethodMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The registration logic of every annotation: the bodies of the TypeScript decorators ({@code src/decorators/*.ts}),
 * one static method per decorator, each writing into the global configuration through
 * {@link Booster#configureCurrentEnv(java.util.function.Consumer)} with the TypeScript checks and error messages
 * (plain TypeScript {@code Error}s are {@link IllegalStateException}s). Duplicate-registration messages keep the
 * TypeScript new line followed by eight spaces.
 *
 * <p>{@link #registerClass(Class)} is the Java equivalent of importing a file: it applies every annotation of one
 * class in the TypeScript decorator order, member annotations first (methods in declaration order, then the
 * properties the class declares, in declaration order) and class annotations last. The {@code Importer} calls it for
 * every scanned class; tests call it (or a single registrar) directly, like the TypeScript tests apply decorators.
 *
 * <p>Java additions, because Java has no compile-time check of the TypeScript decorator signatures: handler classes
 * must declare their static {@code handle}/{@code start} method, reducers and projections must be static, schema
 * migration methods must be instance methods, {@code @Projects} must name exactly one join key, and an un-projection
 * must name a static method of the read model.
 */
public final class Decorators {

  /** The second line of every duplicate-registration message ({@code \n} plus eight spaces). */
  public static final String CLEAN_BUILD_HINT = "\n        If you think that this is an error, try performing a clean build.";

  /** Message of {@code @ToVersion} with a version lower than 2. */
  public static final String INVALID_MIGRATION_VERSION_MESSAGE = "Migration versions must always be greater than 1";

  /** Message of {@code @SchemaMigration} on a class without {@code @ToVersion} methods. */
  public static final String NO_MIGRATION_METHODS_MESSAGE =
      "No migration methods found in this class. Define at least one migration and annotate it with @ToVersion()";

  /** What {@code @Returns} prints (TypeScript {@code console.error}). */
  public static final String RETURNS_DEPRECATION_MESSAGE = "\n    The usage of the '@Returns' annotation is deprecated,"
      + "\n    You may return any type without annotating the method\n\n    For more information, check out the docs:\n\n"
      + "    https://docs.boosterframework.com/architecture/command#returning-a-value\n  ";

  private static final Set<Class<? extends Annotation>> MEMBER_ANNOTATIONS = Set.of(
      Reduces.class, Reduces.List.class, Projects.class, Projects.List.class, ToVersion.class, NonExposed.class,
      SequencedBy.class, PartitionKey.class, Returns.class);

  private Decorators() {}

  // ---------------------------------------------------------------------------------------------------------------
  // A whole class
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Applies every Booster annotation of a class (the equivalent of importing its TypeScript file): method
   * annotations ({@link Reduces}, {@link Projects}, {@link ToVersion}, {@link NonExposed}, {@link Returns}) in
   * declaration order, then property annotations ({@link NonExposed}, {@link SequencedBy}, {@link PartitionKey}) of
   * the properties the class declares, then the class annotations.
   *
   * @param type the class
   */
  public static void registerClass(Class<?> type) {
    if (hasMemberAnnotations(type)) {
      ClassMetadata metadata = ClassMetadata.of(type);
      for (MethodMetadata method : metadata.methods()) {
        registerMethodAnnotations(type, method.method());
      }
      for (PropertyMetadata property : metadata.fields()) {
        if (declaredBy(property, type)) {
          registerPropertyAnnotations(type, property);
        }
      }
    }
    registerClassAnnotations(type);
  }

  private static void registerMethodAnnotations(Class<?> type, Method method) {
    if (method.isAnnotationPresent(Returns.class)) {
      warnReturnsDeprecated();
    }
    if (method.isAnnotationPresent(ToVersion.class)) {
      toVersionMetadata(type, method);
    }
    for (Reduces reduces : method.getAnnotationsByType(Reduces.class)) {
      requireStatic(method, "@Reduces");
      registerReducer(reduces.value().getSimpleName(), new ReducerMetadata(type, method.getName()));
    }
    for (Projects projects : method.getAnnotationsByType(Projects.class)) {
      registerProjects(type, method, projects);
    }
    if (method.isAnnotationPresent(NonExposed.class)) {
      registerNonExposed(type, method.getName());
    }
  }

  private static void registerPropertyAnnotations(Class<?> type, PropertyMetadata property) {
    if (propertyAnnotation(type, property, NonExposed.class) != null) {
      registerNonExposed(type, property.name());
    }
    if (propertyAnnotation(type, property, SequencedBy.class) != null) {
      registerSequenceKey(type, property.name());
    }
    if (propertyAnnotation(type, property, PartitionKey.class) != null) {
      registerPartitionKey(type, property.name());
    }
  }

  private static void registerClassAnnotations(Class<?> type) {
    Role role = type.getAnnotation(Role.class);
    if (role != null) {
      registerRole(type, role);
    }
    if (type.isAnnotationPresent(Event.class)) {
      registerEvent(type);
    }
    Notification notification = type.getAnnotation(Notification.class);
    if (notification != null) {
      registerNotification(type, notification);
    }
    Entity entity = type.getAnnotation(Entity.class);
    if (entity != null) {
      registerEntity(type, entity);
    }
    Command command = type.getAnnotation(Command.class);
    if (command != null) {
      registerCommand(type, command);
    }
    Query query = type.getAnnotation(Query.class);
    if (query != null) {
      registerQuery(type, query);
    }
    ReadModel readModel = type.getAnnotation(ReadModel.class);
    if (readModel != null) {
      registerReadModel(type, readModel);
    }
    ScheduledCommand scheduledCommand = type.getAnnotation(ScheduledCommand.class);
    if (scheduledCommand != null) {
      registerScheduledCommand(type, scheduledCommand);
    }
    for (EventHandler eventHandler : type.getAnnotationsByType(EventHandler.class)) {
      registerEventHandler(eventHandler.value(), type);
    }
    if (type.isAnnotationPresent(GlobalEventHandler.class)) {
      registerGlobalEventHandler(type);
    }
    if (type.isAnnotationPresent(GlobalErrorHandler.class)) {
      registerGlobalErrorHandler(type);
    }
    DataMigration dataMigration = type.getAnnotation(DataMigration.class);
    if (dataMigration != null) {
      registerDataMigration(type, dataMigration);
    }
    SchemaMigration schemaMigration = type.getAnnotation(SchemaMigration.class);
    if (schemaMigration != null) {
      registerSchemaMigration(type, schemaMigration.value());
    }
    HealthSensor healthSensor = type.getAnnotation(HealthSensor.class);
    if (healthSensor != null) {
      registerHealthSensor(type, healthSensor);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // command.ts, query.ts
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code @Command(attributes)}.
   *
   * @param commandClass the command class
   * @param attributes the annotation
   */
  public static void registerCommand(Class<?> commandClass, Command attributes) {
    Booster.configureCurrentEnv(config -> {
      String name = commandClass.getSimpleName();
      if (config.commandHandlers.containsKey(name)) {
        throw new IllegalStateException("A command called " + name + " is already registered." + CLEAN_BUILD_HINT);
      }
      requireStaticMethod(commandClass, "handle", "command");
      ClassMetadata metadata = ClassMetadata.of(commandClass);
      CommandAuthorizer authorizer = BoosterAuthorizer.build(
          BoosterAuthorizer.fromAnnotation(attributes.authorize(), CommandAuthorizer.class), CommandAuthorizer.class);
      List<CommandBeforeFunction> before = instances(attributes.before());
      config.commandHandlers.put(name, new CommandMetadata(commandClass, metadata.fields(), metadata.methods(), authorizer, before));
    });
  }

  /**
   * {@code @Query(attributes)}.
   *
   * @param queryClass the query class
   * @param attributes the annotation
   */
  public static void registerQuery(Class<?> queryClass, Query attributes) {
    Booster.configureCurrentEnv(config -> {
      String name = queryClass.getSimpleName();
      if (config.queryHandlers.containsKey(name)) {
        throw new IllegalStateException("A query called " + name + " is already registered." + CLEAN_BUILD_HINT);
      }
      requireStaticMethod(queryClass, "handle", "query");
      ClassMetadata metadata = ClassMetadata.of(queryClass);
      QueryAuthorizer authorizer = BoosterAuthorizer.build(
          BoosterAuthorizer.fromAnnotation(attributes.authorize(), QueryAuthorizer.class), QueryAuthorizer.class);
      List<QueryBeforeFunction> before = instances(attributes.before());
      config.queryHandlers.put(name, new QueryMetadata(queryClass, metadata.fields(), metadata.methods(), authorizer, before));
    });
  }

  /**
   * {@code @Returns(returnClass)}: prints the deprecation notice to the standard error.
   */
  public static void warnReturnsDeprecated() {
    System.err.println(RETURNS_DEPRECATION_MESSAGE);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // event.ts, notification.ts
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code @Event}.
   *
   * @param eventClass the event class
   */
  public static void registerEvent(Class<?> eventClass) {
    Booster.configureCurrentEnv(config -> {
      String name = eventClass.getSimpleName();
      if (config.events.containsKey(name)) {
        throw new IllegalStateException("A event called " + name + " is already registered." + CLEAN_BUILD_HINT);
      }
      config.events.put(name, new EventMetadata(eventClass));
    });
  }

  /**
   * {@code @Notification(options)}.
   *
   * @param notificationClass the notification class
   * @param options the annotation
   */
  public static void registerNotification(Class<?> notificationClass, Notification options) {
    Booster.configureCurrentEnv(config -> {
      String name = notificationClass.getSimpleName();
      if (config.notifications.containsKey(name) || config.events.containsKey(name)) {
        throw new IllegalStateException("A notification called " + name + " is already registered." + CLEAN_BUILD_HINT);
      }
      String topic = options == null ? Notification.DEFAULT_TOPIC : options.topic();
      if (!topic.isEmpty()) {
        config.eventToTopic.put(name, topic);
        config.topicToEvent.put(topic, name);
      }
      config.notifications.put(name, new NotificationMetadata(notificationClass));
    });
  }

  /**
   * {@code @partitionKey}.
   *
   * @param notificationClass the notification class
   * @param propertyName the partition-key property
   */
  public static void registerPartitionKey(Class<?> notificationClass, String propertyName) {
    Booster.configureCurrentEnv(config -> {
      String name = notificationClass.getSimpleName();
      String existing = config.partitionKeys.get(name);
      if (existing != null && !existing.equals(propertyName)) {
        throw new IllegalStateException("Error trying to register a partition key named `" + propertyName + "` for class `"
            + name + "`. It already had the partition key `" + existing
            + "` defined and only one partition key is allowed for each notification event.");
      }
      config.partitionKeys.put(name, propertyName);
    });
  }

  // ---------------------------------------------------------------------------------------------------------------
  // entity.ts
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code @Entity} / {@code @Entity({ authorizeReadEvents })}.
   *
   * @param entityClass the entity class
   * @param attributes the annotation, or {@code null} for the default (deny)
   */
  public static void registerEntity(Class<?> entityClass, Entity attributes) {
    Booster.configureCurrentEnv(config -> {
      String name = entityClass.getSimpleName();
      if (config.entities.containsKey(name)) {
        throw new IllegalStateException("An entity called " + name + " is already registered" + CLEAN_BUILD_HINT + ".");
      }
      Class<?>[] authorizeReadEvents = attributes == null ? new Class<?>[0] : attributes.authorizeReadEvents();
      EventStreamAuthorizer eventStreamAuthorizer = BoosterAuthorizer.build(
          BoosterAuthorizer.fromAnnotation(authorizeReadEvents, EventStreamAuthorizer.class), EventStreamAuthorizer.class);
      config.entities.put(name, new EntityMetadata(entityClass, eventStreamAuthorizer));
    });
  }

  /**
   * {@code registerReducer(eventName, reducerMetadata)} of {@code @Reduces}.
   *
   * @param eventName the event class name
   * @param reducerMetadata the entity class and the reducer method name
   */
  public static void registerReducer(String eventName, ReducerMetadata reducerMetadata) {
    Booster.configureCurrentEnv(config -> {
      ReducerMetadata existing = config.reducers.get(eventName);
      if (existing != null) {
        throw new IllegalStateException("Error registering reducer: The event " + eventName
            + " was already registered to be reduced by method " + existing.methodName() + " in the entity "
            + existing.type().getSimpleName() + "." + CLEAN_BUILD_HINT);
      }
      config.reducers.put(eventName, reducerMetadata);
    });
  }

  // ---------------------------------------------------------------------------------------------------------------
  // event-handler.ts, global-event-handler.ts, global-error-handler.ts
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code @EventHandler(Event)}.
   *
   * @param eventClass the handled event or notification class
   * @param eventHandlerClass the handler class
   */
  public static void registerEventHandler(Class<?> eventClass, Class<?> eventHandlerClass) {
    requireStaticMethod(eventHandlerClass, "handle", "event handler");
    registerEventHandler(eventClass.getSimpleName(), eventHandlerClass);
  }

  /**
   * {@code registerEventHandler(eventName, eventHandlerClass)}: appends the class to the event's handlers unless the
   * same class is already there.
   *
   * @param eventName the event name (or {@link GlobalEventHandler#BOOSTER_GLOBAL_EVENT_HANDLERS})
   * @param eventHandlerClass the handler class
   */
  public static void registerEventHandler(String eventName, Class<?> eventHandlerClass) {
    Booster.configureCurrentEnv(config -> {
      List<Class<?>> registeredEventHandlers = config.eventHandlers.getOrDefault(eventName, new ArrayList<>());
      if (registeredEventHandlers.stream().anyMatch(type -> type == eventHandlerClass)) {
        return;
      }
      registeredEventHandlers.add(eventHandlerClass);
      config.eventHandlers.put(eventName, registeredEventHandlers);
    });
  }

  /**
   * {@code @GlobalEventHandler}.
   *
   * @param eventHandlerClass the handler class
   */
  public static void registerGlobalEventHandler(Class<?> eventHandlerClass) {
    requireStaticMethod(eventHandlerClass, "handle", "global event handler");
    registerEventHandler(GlobalEventHandler.BOOSTER_GLOBAL_EVENT_HANDLERS, eventHandlerClass);
  }

  /**
   * {@code @GlobalErrorHandler()}.
   *
   * @param errorHandlerClass the error handler class
   */
  public static void registerGlobalErrorHandler(Class<?> errorHandlerClass) {
    Booster.configureCurrentEnv(config -> {
      if (config.globalErrorsHandler != null) {
        throw new IllegalStateException(
            "An error handler called " + errorHandlerClass.getSimpleName() + " is already registered." + CLEAN_BUILD_HINT);
      }
      config.globalErrorsHandler = new GlobalErrorHandlerMetadata(errorHandlerClass);
    });
  }

  // ---------------------------------------------------------------------------------------------------------------
  // read-model.ts, projects.ts, sequenced-by.ts, non-exposed.ts
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code @ReadModel(attributes)}: the properties are the class metadata fields, whose {@code dependencies} are the
   * {@link CalculatedField#dependsOn()} values (empty for non-calculated properties).
   *
   * @param readModelClass the read model class
   * @param attributes the annotation
   */
  public static void registerReadModel(Class<?> readModelClass, ReadModel attributes) {
    Booster.configureCurrentEnv(config -> {
      String name = readModelClass.getSimpleName();
      if (config.readModels.containsKey(name)) {
        throw new IllegalStateException("A read model called " + name + " is already registered." + CLEAN_BUILD_HINT);
      }
      ReadModelAuthorizer authorizer = BoosterAuthorizer.build(
          BoosterAuthorizer.fromAnnotation(attributes.authorize(), ReadModelAuthorizer.class), ReadModelAuthorizer.class);
      List<PropertyMetadata> properties = ClassMetadata.of(readModelClass).fields();
      List<ReadModelBeforeFunction> before = instances(attributes.before());
      config.readModels.put(name, new ReadModelMetadata(readModelClass, properties, authorizer, before));
    });
  }

  private static void registerProjects(Class<?> readModelClass, Method method, Projects projects) {
    requireStatic(method, "@Projects");
    String location = "@Projects on " + readModelClass.getSimpleName() + "." + method.getName();
    boolean hasFunction = projects.joinKeyFunction() != Projects.NoJoinKeyFunction.class;
    if (hasFunction && !projects.joinKey().isEmpty()) {
      throw new IllegalStateException(location + " defines both joinKey and joinKeyFunction; use only one");
    }
    if (!hasFunction && projects.joinKey().isEmpty()) {
      throw new IllegalStateException(location + " must define either joinKey or joinKeyFunction");
    }
    JoinKey joinKey;
    if (hasFunction) {
      ReadModelJoinKeyFunction<?> function = UserCode.newInstance(projects.joinKeyFunction());
      joinKey = JoinKey.query(function);
    } else {
      joinKey = JoinKey.property(projects.joinKey());
    }
    String entityName = projects.entity().getSimpleName();
    registerProjection(entityName, new ProjectionMetadata(readModelClass, method.getName(), joinKey));
    if (!projects.unProject().isEmpty()) {
      if (!UserCode.hasStaticMethod(readModelClass, projects.unProject())) {
        throw new IllegalStateException(location + " names the un-projection method \"" + projects.unProject()
            + "\", but " + readModelClass.getSimpleName() + " has no static method with that name");
      }
      registerUnProjection(entityName, new ProjectionMetadata(readModelClass, projects.unProject(), joinKey));
    }
  }

  /**
   * {@code registerProjection(originName, projectionMetadata)}: appends (TypeScript's duplicate check compares a fresh
   * object by identity, so it always appends).
   *
   * @param entityName the projected entity class name
   * @param projectionMetadata the projection
   */
  public static void registerProjection(String entityName, ProjectionMetadata projectionMetadata) {
    Booster.configureCurrentEnv(config ->
        config.projections.computeIfAbsent(entityName, ignored -> new ArrayList<>()).add(projectionMetadata));
  }

  /**
   * {@code registerUnProjection(originName, projectionMetadata)}: appends.
   *
   * @param entityName the projected entity class name
   * @param projectionMetadata the un-projection
   */
  public static void registerUnProjection(String entityName, ProjectionMetadata projectionMetadata) {
    Booster.configureCurrentEnv(config ->
        config.unProjections.computeIfAbsent(entityName, ignored -> new ArrayList<>()).add(projectionMetadata));
  }

  /**
   * {@code @sequencedBy}.
   *
   * @param readModelClass the read model class
   * @param propertyName the sort-key property
   */
  public static void registerSequenceKey(Class<?> readModelClass, String propertyName) {
    Booster.configureCurrentEnv(config -> {
      String name = readModelClass.getSimpleName();
      String existing = config.readModelSequenceKeys.get(name);
      if (existing != null && !existing.equals(propertyName)) {
        throw new IllegalStateException("Error trying to register a sort key named `" + propertyName + "` for class `"
            + name + "`. It already had the sort key `" + existing
            + "` defined and only one sort key is allowed for each read model.");
      }
      config.readModelSequenceKeys.put(name, propertyName);
    });
  }

  /**
   * {@code @NonExposed}: appends the name (a new list every time, no de-duplication).
   *
   * @param type the class declaring the property
   * @param fieldName the property or method name
   */
  public static void registerNonExposed(Class<?> type, String fieldName) {
    Booster.configureCurrentEnv(config -> {
      String className = type.getSimpleName();
      List<String> value = new ArrayList<>(config.nonExposedGraphQLMetadataKey.getOrDefault(className, List.of()));
      value.add(fieldName);
      config.nonExposedGraphQLMetadataKey.put(className, value);
    });
  }

  // ---------------------------------------------------------------------------------------------------------------
  // role.ts, scheduled-command.ts, data-migration.ts, health-sensor.ts
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code @Role(roleMetadata)}: no duplicate check (last wins).
   *
   * @param roleClass the role class
   * @param role the annotation, or {@code null} for {@code { auth: {} }}
   */
  public static void registerRole(Class<?> roleClass, Role role) {
    RoleMetadata metadata = role == null ? RoleMetadata.defaults() : new RoleMetadata(authMetadata(role.auth()));
    Booster.configureCurrentEnv(config -> config.roles.put(roleClass.getSimpleName(), metadata));
  }

  private static AuthMetadata authMetadata(RoleAuth auth) {
    List<SignUpMethod> signUpMethods = auth.signUpMethods().length == 0
        ? null
        : Arrays.stream(auth.signUpMethods()).map(SignUpMethod::fromValue).toList();
    return new AuthMetadata(signUpMethods, auth.skipConfirmation() ? Boolean.TRUE : null);
  }

  /**
   * {@code @ScheduledCommand(attributes)}: empty fields are absent ({@code null}).
   *
   * @param scheduledCommandClass the scheduled command class
   * @param attributes the annotation
   */
  public static void registerScheduledCommand(Class<?> scheduledCommandClass, ScheduledCommand attributes) {
    Booster.configureCurrentEnv(config -> {
      String name = scheduledCommandClass.getSimpleName();
      if (config.scheduledCommandHandlers.containsKey(name)) {
        throw new IllegalStateException("A command called " + name + " is already registered." + CLEAN_BUILD_HINT);
      }
      requireStaticMethod(scheduledCommandClass, "handle", "scheduled command");
      ScheduleInterface scheduledOn = new ScheduleInterface(absentIfEmpty(attributes.minute()), absentIfEmpty(attributes.hour()),
          absentIfEmpty(attributes.day()), absentIfEmpty(attributes.month()), absentIfEmpty(attributes.weekDay()),
          absentIfEmpty(attributes.year()));
      config.scheduledCommandHandlers.put(name, new ScheduledCommandMetadata(scheduledCommandClass, scheduledOn));
    });
  }

  /**
   * {@code @DataMigration(attributes)}.
   *
   * @param migrationClass the data migration class
   * @param attributes the annotation
   */
  public static void registerDataMigration(Class<?> migrationClass, DataMigration attributes) {
    Booster.configureCurrentEnv(config -> {
      String name = migrationClass.getSimpleName();
      if (config.dataMigrationHandlers.containsKey(name)) {
        throw new IllegalStateException("A data migration called " + name + " is already registered." + CLEAN_BUILD_HINT);
      }
      requireStaticMethod(migrationClass, "start", "data migration");
      config.dataMigrationHandlers.put(name,
          new DataMigrationMetadata(migrationClass, new DataMigrationParameters(attributes.order())));
    });
  }

  /**
   * {@code @HealthSensor(attributes)}: the first registration seeds the registry with the built-in indicators;
   * {@code showChildren} defaults to {@code true}; same ids overwrite.
   *
   * @param healthIndicatorClass the indicator class
   * @param attributes the annotation
   */
  public static void registerHealthSensor(Class<?> healthIndicatorClass, HealthSensor attributes) {
    boolean hasHealthMethod = Arrays.stream(healthIndicatorClass.getMethods())
        .anyMatch(method -> method.getName().equals("health") && !Modifier.isStatic(method.getModifiers()));
    if (!hasHealthMethod) {
      throw new IllegalStateException("The health indicator " + healthIndicatorClass.getSimpleName()
          + " must declare a public instance method \"health(BoosterConfig config, HealthIndicatorMetadata metadata)\"");
    }
    Booster.configureCurrentEnv(config -> {
      if (config.userHealthIndicators.isEmpty()) {
        config.userHealthIndicators = DefaultBoosterHealthIndicators.defaultBoosterHealthIndicators(config);
      }
      HealthIndicatorConfiguration configuration = new HealthIndicatorConfiguration(attributes.id(), attributes.name(),
          attributes.enabled(), attributes.details(), attributes.showChildren());
      config.userHealthIndicators.put(attributes.id(), new HealthIndicatorMetadata(healthIndicatorClass, configuration));
    });
  }

  // ---------------------------------------------------------------------------------------------------------------
  // schema-migration.ts
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * The {@code @ToVersion} methods of a migration class, in declaration order (TypeScript metadata key
   * {@code booster:migrationsMethods}).
   *
   * @param migrationClass the migration class
   * @return their metadata
   * @throws IllegalStateException {@value #INVALID_MIGRATION_VERSION_MESSAGE} when a version is lower than 2
   */
  public static List<SchemaMigrationMetadata> migrationMethods(Class<?> migrationClass) {
    List<SchemaMigrationMetadata> migrations = new ArrayList<>();
    for (MethodMetadata method : ClassMetadata.of(migrationClass).methods()) {
      if (method.method().isAnnotationPresent(ToVersion.class)) {
        migrations.add(toVersionMetadata(migrationClass, method.method()));
      }
    }
    return migrations;
  }

  private static SchemaMigrationMetadata toVersionMetadata(Class<?> migrationClass, Method method) {
    ToVersion toVersion = method.getAnnotation(ToVersion.class);
    if (toVersion.toVersion() <= 1) {
      throw new IllegalStateException(INVALID_MIGRATION_VERSION_MESSAGE);
    }
    if (Modifier.isStatic(method.getModifiers())) {
      throw new IllegalStateException(
          "@ToVersion methods must be instance methods: " + migrationClass.getSimpleName() + "." + method.getName());
    }
    return new SchemaMigrationMetadata(migrationClass, method.getName(), toVersion.toVersion(), toVersion.fromSchema(),
        toVersion.toSchema());
  }

  /**
   * {@code @SchemaMigration(conceptClass)}. As in TypeScript the concept's (insertion-ordered) map is created before
   * the checks, and versions registered before a duplicate stay registered.
   *
   * @param schemaMigrationClass the migration class
   * @param conceptClass the migrated concept
   */
  public static void registerSchemaMigration(Class<?> schemaMigrationClass, Class<?> conceptClass) {
    Booster.configureCurrentEnv(config -> {
      String conceptName = conceptClass.getSimpleName();
      Map<Integer, SchemaMigrationMetadata> conceptMigrations =
          config.schemaMigrations.computeIfAbsent(conceptName, ignored -> new LinkedHashMap<>());
      List<SchemaMigrationMetadata> migrationMethodsMetadata = migrationMethods(schemaMigrationClass);
      if (migrationMethodsMetadata.isEmpty()) {
        throw new IllegalStateException(NO_MIGRATION_METHODS_MESSAGE);
      }
      for (SchemaMigrationMetadata schemaMigrationMetadata : migrationMethodsMetadata) {
        if (conceptMigrations.containsKey(schemaMigrationMetadata.toVersion())) {
          throw new IllegalStateException("Found duplicated migration for '" + conceptName + "' in migration class '"
              + schemaMigrationClass.getSimpleName() + "': There is an already defined migration for version "
              + schemaMigrationMetadata.toVersion());
        }
        conceptMigrations.put(schemaMigrationMetadata.toVersion(), schemaMigrationMetadata);
      }
    });
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static <T> List<T> instances(Class<? extends T>[] classes) {
    List<T> result = new ArrayList<>();
    for (Class<? extends T> type : classes) {
      result.add((T) UserCode.newInstance(type));
    }
    return result;
  }

  private static String absentIfEmpty(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static void requireStaticMethod(Class<?> type, String methodName, String kind) {
    if (!UserCode.hasStaticMethod(type, methodName)) {
      throw new IllegalStateException(
          "The " + kind + " " + type.getSimpleName() + " must declare a static method called \"" + methodName + "\"");
    }
  }

  private static void requireStatic(Method method, String annotation) {
    if (!Modifier.isStatic(method.getModifiers())) {
      throw new IllegalStateException(
          annotation + " methods must be static: " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
    }
  }

  private static boolean hasMemberAnnotations(Class<?> type) {
    for (Method method : type.getDeclaredMethods()) {
      if (hasAny(method)) {
        return true;
      }
    }
    for (Field field : type.getDeclaredFields()) {
      if (hasAny(field)) {
        return true;
      }
    }
    RecordComponent[] components = type.getRecordComponents();
    if (components != null) {
      for (RecordComponent component : components) {
        if (hasAny(component)) {
          return true;
        }
      }
    }
    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      for (Parameter parameter : constructor.getParameters()) {
        if (hasAny(parameter)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasAny(AnnotatedElement element) {
    for (Annotation annotation : element.getDeclaredAnnotations()) {
      if (MEMBER_ANNOTATIONS.contains(annotation.annotationType())) {
        return true;
      }
    }
    return false;
  }

  private static boolean declaredBy(PropertyMetadata property, Class<?> type) {
    return switch (property.element()) {
      case RecordComponent component -> component.getDeclaringRecord() == type;
      case Field field -> field.getDeclaringClass() == type;
      case Method method -> method.getDeclaringClass() == type;
      case null, default -> false;
    };
  }

  /**
   * An annotation on a property: on any element related to it (record component, field, accessor, canonical
   * constructor parameter) or, for class fields, on a constructor parameter with the same name (TypeScript parameter
   * properties; needs {@code -parameters}).
   */
  private static <A extends Annotation> A propertyAnnotation(Class<?> type, PropertyMetadata property, Class<A> annotationType) {
    A annotation = property.getAnnotation(annotationType);
    if (annotation != null || property.kind() != PropertyMetadata.Kind.FIELD) {
      return annotation;
    }
    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      for (Parameter parameter : constructor.getParameters()) {
        if (parameter.isNamePresent() && parameter.getName().equals(property.name())
            && parameter.isAnnotationPresent(annotationType)) {
          return parameter.getAnnotation(annotationType);
        }
      }
    }
    return null;
  }
}
