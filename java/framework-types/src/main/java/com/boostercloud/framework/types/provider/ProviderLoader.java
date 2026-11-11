package com.boostercloud.framework.types.provider;

import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;

/**
 * Loads providers and their infrastructure by package name, the Java replacement of
 * {@code require(providerPackage).Provider(rockets)} and {@code require(pkg + '-infrastructure').Infrastructure}.
 *
 * <ul>
 *   <li>a provider is the class {@code <providerPackage>.Provider} with
 *       {@code public static ProviderLibrary provider(List<RocketDescriptor> rockets)};
 *   <li>its infrastructure is the class {@code <providerPackage>.infrastructure.Infrastructure} with
 *       {@code public static ProviderInfrastructure infrastructure(List<RocketDescriptor> rockets)}.
 * </ul>
 *
 * Classes are loaded with the thread context class loader (falling back to this class's loader).
 */
public final class ProviderLoader {

  /** Simple name of the provider entry class. */
  public static final String PROVIDER_CLASS = "Provider";

  /** Name of the static provider factory method. */
  public static final String PROVIDER_METHOD = "provider";

  /** Sub-package of the infrastructure entry class. */
  public static final String INFRASTRUCTURE_PACKAGE = "infrastructure";

  /** Simple name of the infrastructure entry class. */
  public static final String INFRASTRUCTURE_CLASS = "Infrastructure";

  /** Name of the static infrastructure factory method. */
  public static final String INFRASTRUCTURE_METHOD = "infrastructure";

  private ProviderLoader() {}

  /**
   * @param providerPackage a provider package, e.g. {@code com.boostercloud.framework.provider.local}
   * @param rockets the rockets of the application
   * @return the provider library
   * @throws IllegalStateException when the class or method cannot be found or the factory fails
   */
  public static ProviderLibrary loadProvider(String providerPackage, List<RocketDescriptor> rockets) {
    Objects.requireNonNull(providerPackage, "providerPackage");
    Object provider = invokeStaticFactory(providerPackage + "." + PROVIDER_CLASS, PROVIDER_METHOD, rockets, "provider");
    if (provider != null && !(provider instanceof ProviderLibrary)) {
      throw new IllegalStateException("The method " + providerPackage + "." + PROVIDER_CLASS + "." + PROVIDER_METHOD
          + " must return a ProviderLibrary, but it returned " + provider.getClass().getName());
    }
    return (ProviderLibrary) provider;
  }

  /**
   * Adapts {@code <providerPackage>.infrastructure.Infrastructure.infrastructure(rockets)} as a
   * {@link HasInfrastructure} (TypeScript {@code loadInfrastructurePackage}). The class is resolved immediately so a
   * missing infrastructure module is reported here.
   *
   * @param providerPackage the provider package
   * @return the infrastructure module
   * @throws IllegalStateException when the class or method cannot be found
   */
  public static HasInfrastructure loadInfrastructurePackage(String providerPackage) {
    String className = providerPackage + "." + INFRASTRUCTURE_PACKAGE + "." + INFRASTRUCTURE_CLASS;
    Method factory = findStaticFactory(className, INFRASTRUCTURE_METHOD, "infrastructure");
    return rockets -> {
      Object infrastructure = invoke(factory, rockets, className);
      if (!(infrastructure instanceof ProviderInfrastructure result)) {
        throw new IllegalStateException("The method " + className + "." + INFRASTRUCTURE_METHOD
            + " must return a ProviderInfrastructure");
      }
      return result;
    };
  }

  /**
   * @param providerPackage the provider package
   * @param rockets the rockets of the application
   * @return the infrastructure
   */
  public static ProviderInfrastructure loadInfrastructure(String providerPackage, List<RocketDescriptor> rockets) {
    return loadInfrastructurePackage(providerPackage).infrastructure(rockets == null ? List.of() : rockets);
  }

  private static Object invokeStaticFactory(String className, String methodName, List<RocketDescriptor> rockets, String what) {
    Method factory = findStaticFactory(className, methodName, what);
    return invoke(factory, rockets, className);
  }

  private static Method findStaticFactory(String className, String methodName, String what) {
    Class<?> type;
    try {
      type = Class.forName(className, true, classLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Could not load the " + what + " class " + className
          + ". Make sure the module that contains it is a dependency of your project.", e);
    }
    try {
      Method method = type.getMethod(methodName, List.class);
      if (!Modifier.isStatic(method.getModifiers())) {
        throw new IllegalStateException(className + "." + methodName + "(List<RocketDescriptor>) must be static");
      }
      return method;
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("The class " + className + " does not declare a public static method "
          + methodName + "(List<RocketDescriptor>)", e);
    }
  }

  private static Object invoke(Method factory, List<RocketDescriptor> rockets, String className) {
    try {
      return factory.invoke(null, rockets == null ? List.of() : List.copyOf(rockets));
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("The factory " + className + "." + factory.getName() + " failed", cause);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot access " + className + "." + factory.getName(), e);
    }
  }

  private static ClassLoader classLoader() {
    ClassLoader context = Thread.currentThread().getContextClassLoader();
    return context != null ? context : ProviderLoader.class.getClassLoader();
  }
}
