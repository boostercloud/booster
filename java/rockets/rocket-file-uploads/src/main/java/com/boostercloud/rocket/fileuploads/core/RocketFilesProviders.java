package com.boostercloud.rocket.fileuploads.core;

import com.boostercloud.rocket.fileuploads.types.RocketFilesProvider;
import com.boostercloud.rocket.fileuploads.types.RocketProviderPackageType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java-only: loads a provider runtime library, the replacement of {@code require(rocketProviderPackage)} in
 * {@code BoosterRocketFiles.register} and in the {@code FileHandler} constructor. The library is the class
 * {@link RocketProviderPackageType#libraryClassName()} ({@code com.boostercloud.rocket.fileuploads.local.RocketFilesLibrary}
 * or {@code ...azure.RocketFilesLibrary}), instantiated once with its public no-argument constructor and cached, as
 * Node caches modules.
 */
public final class RocketFilesProviders {

  private static final Map<RocketProviderPackageType, RocketFilesProvider> CACHE = new ConcurrentHashMap<>();

  private RocketFilesProviders() {}

  /**
   * @param rocketProviderPackage the provider runtime library
   * @return the library
   * @throws IllegalStateException {@code Cannot find module '<npm name>'} when the library class is missing or is not
   *     a {@link RocketFilesProvider}, or when no library is configured
   */
  public static RocketFilesProvider load(RocketProviderPackageType rocketProviderPackage) {
    if (rocketProviderPackage == null) {
      throw new IllegalStateException("Cannot find module 'undefined': the rocket parameters have no "
          + "rocketProviderPackage");
    }
    return CACHE.computeIfAbsent(rocketProviderPackage, RocketFilesProviders::instantiate);
  }

  private static RocketFilesProvider instantiate(RocketProviderPackageType type) {
    String className = type.libraryClassName();
    Class<?> libraryClass;
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      libraryClass = Class.forName(className, true,
          loader != null ? loader : RocketFilesProviders.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Cannot find module '" + type.packageName() + "' (class " + className
          + " is not on the class path)", e);
    }
    if (!RocketFilesProvider.class.isAssignableFrom(libraryClass)) {
      throw new IllegalStateException("Cannot find module '" + type.packageName() + "' (class " + className
          + " is not a " + RocketFilesProvider.class.getName() + ")");
    }
    try {
      return (RocketFilesProvider) libraryClass.getConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot load module '" + type.packageName() + "' (class " + className
          + " needs a public no-argument constructor)", e);
    }
  }
}
