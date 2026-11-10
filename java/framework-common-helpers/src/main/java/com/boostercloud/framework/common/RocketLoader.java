package com.boostercloud.framework.common;

import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * Port of {@code rocket-loader.ts}: loads and validates an infrastructure rocket. TypeScript does
 * {@code require(packageName).default(parameters)}; Java loads {@code <packageName>.Rocket}, which must be a
 * {@link RocketBuilder}, and calls it with the descriptor parameters. The result must have a public
 * {@code mountStack} method. The checks and messages are TypeScript's, in the same order.
 *
 * <p>The loading step ({@code requireRocket}) is injectable through {@link #RocketLoader(Function)} so tests can
 * replace it, as the TypeScript tests replace {@code RocketLoader.requireRocket}.
 */
public final class RocketLoader {

  /** Simple name of the rocket entry class inside the rocket package. */
  public static final String ROCKET_CLASS = "Rocket";

  private static final RocketLoader DEFAULT = new RocketLoader(RocketLoader::requireRocket);

  private final Function<String, Object> requireRocket;

  /**
   * @param requireRocket returns the "default export" of a rocket package (a {@link RocketBuilder}), or {@code null}
   *     when it has none
   */
  public RocketLoader(Function<String, Object> requireRocket) {
    this.requireRocket = Objects.requireNonNull(requireRocket, "requireRocket");
  }

  /**
   * {@code RocketLoader.loadRocket(rocketDescriptor)}.
   *
   * @param rocketDescriptor the rocket
   * @return the infrastructure rocket
   */
  public static Object loadRocket(RocketDescriptor rocketDescriptor) {
    return DEFAULT.load(rocketDescriptor);
  }

  /**
   * Loads a rocket and checks it is of the provider's rocket type.
   *
   * @param rocketDescriptor the rocket
   * @param rocketType the provider-specific infrastructure rocket interface
   * @param <T> that type
   * @return the infrastructure rocket
   */
  public static <T> T loadRocket(RocketDescriptor rocketDescriptor, Class<T> rocketType) {
    return DEFAULT.load(rocketDescriptor, rocketType);
  }

  /**
   * @param rocketDescriptor the rocket
   * @return the infrastructure rocket
   * @throws IllegalStateException {@code Could not load the rocket infrastructure package <pkg>. ...} when the package
   *     has no rocket builder, {@code Could not initialize rocket infrastructure package <pkg>. ...} when what it
   *     exports is not a builder, {@code The package <pkg> doesn't seem to be a rocket.} when the built object has no
   *     {@code mountStack}
   */
  public Object load(RocketDescriptor rocketDescriptor) {
    String packageName = rocketDescriptor.packageName();
    Object rocketBuilder = requireRocket.apply(packageName);
    if (rocketBuilder == null) {
      throw new IllegalStateException("Could not load the rocket infrastructure package " + packageName
          + ". Make sure you installed it in your project devDependencies.");
    }
    if (!(rocketBuilder instanceof RocketBuilder builder)) {
      throw new IllegalStateException("Could not initialize rocket infrastructure package " + packageName
          + ". It doesn't seem to implement a rocket builder method as default export.");
    }
    Object rocket = builder.build(rocketDescriptor.parameters());
    if (rocket == null || !hasMountStack(rocket)) {
      throw new IllegalStateException("The package " + packageName + " doesn't seem to be a rocket.");
    }
    return rocket;
  }

  /**
   * @param rocketDescriptor the rocket
   * @param rocketType the expected rocket type
   * @param <T> that type
   * @return the infrastructure rocket
   */
  public <T> T load(RocketDescriptor rocketDescriptor, Class<T> rocketType) {
    Object rocket = load(rocketDescriptor);
    if (!rocketType.isInstance(rocket)) {
      throw new IllegalStateException("The package " + rocketDescriptor.packageName() + " doesn't seem to be a rocket.");
    }
    return rocketType.cast(rocket);
  }

  private static boolean hasMountStack(Object rocket) {
    return Arrays.stream(rocket.getClass().getMethods())
        .anyMatch(method -> method.getName().equals("mountStack") && !Modifier.isStatic(method.getModifiers()));
  }

  /**
   * The default {@code requireRocket}: instantiates {@code <packageName>.Rocket} with its no-argument constructor
   * ({@code null} when the class does not exist).
   *
   * @param packageName the rocket package
   * @return the instance, or {@code null}
   */
  static Object requireRocket(String packageName) {
    String className = packageName + "." + ROCKET_CLASS;
    Class<?> type;
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      type = Class.forName(className, true, loader != null ? loader : RocketLoader.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      return null;
    }
    try {
      return type.getConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not instantiate the rocket class " + className
          + ": it needs a public no-argument constructor", e);
    }
  }
}
