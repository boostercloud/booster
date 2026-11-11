package com.boostercloud.rocket.fileuploads.types;

import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.core.decorators.Decorators;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.EventStreamAuthorizer;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.metadata.ExternalTypes;

/**
 * Java-only: registers the rocket's concepts in a configuration. TypeScript registers {@link UploadedFileEvent},
 * {@link UploadedFileEntity} and its reducer as a side effect of importing this package (their decorators run); Java
 * applies annotations only to the application's scanned packages, so {@code BoosterRocketFiles} calls
 * {@link #register(BoosterConfig)} when it is created (in the application's configuration block).
 *
 * <p>The metadata written is exactly what {@code @Event}, {@code @Entity} and {@code @Reduces} would write. Registering
 * twice is a no-op; a different class registered under one of these names fails with the decorators' messages.
 *
 * <p>The rocket's package is also registered as external ({@link ExternalTypes}), so the rocket's types returned by
 * application commands (for instance {@code List<ListItem>}) are exposed as GraphQL {@code JSON}, as TypeScript does for
 * types imported from a package.
 */
public final class RocketFilesConcepts {

  /** The package prefix of the rocket, registered as external for the GraphQL schema. */
  public static final String ROCKET_PACKAGE_PREFIX = "com.boostercloud.rocket.fileuploads.";

  /** The reducer method of {@link UploadedFileEntity}. */
  public static final String REDUCER_METHOD = "reduceUploadedFileEntity";

  private RocketFilesConcepts() {}

  /**
   * Registers {@code UploadedFileEvent}, {@code UploadedFileEntity} and the reducer.
   *
   * @param config the configuration being built
   * @throws IllegalStateException when other classes are registered under the same names
   */
  public static void register(BoosterConfig config) {
    ExternalTypes.addExternalPackagePrefix(ROCKET_PACKAGE_PREFIX);
    synchronized (config) {
      registerEvent(config);
      registerEntity(config);
      registerReducer(config);
    }
  }

  private static void registerEvent(BoosterConfig config) {
    String name = UploadedFileEvent.class.getSimpleName();
    EventMetadata existing = config.events.get(name);
    if (existing == null) {
      config.events.put(name, new EventMetadata(UploadedFileEvent.class));
    } else if (existing.type() != UploadedFileEvent.class) {
      throw new IllegalStateException("A event called " + name + " is already registered." + Decorators.CLEAN_BUILD_HINT);
    }
  }

  private static void registerEntity(BoosterConfig config) {
    String name = UploadedFileEntity.class.getSimpleName();
    EntityMetadata existing = config.entities.get(name);
    if (existing == null) {
      Entity entity = UploadedFileEntity.class.getAnnotation(Entity.class);
      EventStreamAuthorizer authorizer = BoosterAuthorizer.build(
          BoosterAuthorizer.fromAnnotation(entity.authorizeReadEvents(), EventStreamAuthorizer.class),
          EventStreamAuthorizer.class);
      config.entities.put(name, new EntityMetadata(UploadedFileEntity.class, authorizer));
    } else if (existing.type() != UploadedFileEntity.class) {
      throw new IllegalStateException(
          "An entity called " + name + " is already registered" + Decorators.CLEAN_BUILD_HINT + ".");
    }
  }

  private static void registerReducer(BoosterConfig config) {
    String eventName = UploadedFileEvent.class.getSimpleName();
    ReducerMetadata existing = config.reducers.get(eventName);
    if (existing == null) {
      config.reducers.put(eventName, new ReducerMetadata(UploadedFileEntity.class, REDUCER_METHOD));
    } else if (existing.type() != UploadedFileEntity.class || !REDUCER_METHOD.equals(existing.methodName())) {
      throw new IllegalStateException("Error registering reducer: The event " + eventName
          + " was already registered to be reduced by method " + existing.methodName() + " in the entity "
          + existing.type().getSimpleName() + "." + Decorators.CLEAN_BUILD_HINT);
    }
  }
}
