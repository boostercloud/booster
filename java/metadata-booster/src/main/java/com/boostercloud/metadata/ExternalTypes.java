package com.boostercloud.metadata;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Decides which classes are "external" (not part of the user application).
 *
 * <p>TypeScript marks a type as external when it was imported from a package instead of a relative path, and the
 * GraphQL generator then exposes it as {@code JSON} instead of expanding it into an object type. Java has no import
 * paths at runtime, so a class is external when it is a JDK class or its package starts with one of the
 * {@linkplain #externalPackagePrefixes() registered prefixes}: by default the Booster packages that applications
 * import types from ({@code com.boostercloud.framework.types.}, {@code com.boostercloud.framework.common.},
 * {@code com.boostercloud.metadata}), Jackson and graphql-java. Applications can register more prefixes (for example
 * a shared library of value types) with {@link #addExternalPackagePrefix(String)}.
 *
 * <p>Framework-core is deliberately not external: like in TypeScript, core's own test fixtures and internal concepts
 * are expanded.
 */
public final class ExternalTypes {

  private static final List<String> DEFAULT_PREFIXES =
      List.of(
          "java.",
          "javax.",
          "jdk.",
          "sun.",
          "com.sun.",
          "com.boostercloud.framework.types.",
          "com.boostercloud.framework.common.",
          "com.fasterxml.jackson.",
          "graphql.");

  private static final List<String> PREFIXES = new CopyOnWriteArrayList<>(DEFAULT_PREFIXES);

  private ExternalTypes() {}

  /**
   * @param type a class
   * @return whether the class is external
   */
  public static boolean isExternal(Class<?> type) {
    if (type == null) {
      return false;
    }
    return isExternalPackage(type.getPackageName(), type);
  }

  /**
   * Registers an additional external package prefix. It applies immediately, also to metadata computed before,
   * because {@link TypeMetadata#isExternal()} is evaluated on every call.
   *
   * @param packagePrefix a package name or prefix, e.g. {@code "com.acme.shared."}
   */
  public static void addExternalPackagePrefix(String packagePrefix) {
    if (packagePrefix != null && !packagePrefix.isBlank() && !PREFIXES.contains(packagePrefix)) {
      PREFIXES.add(packagePrefix);
    }
  }

  /**
   * @return the current prefixes (JDK classes are always external, whatever the prefixes)
   */
  public static List<String> externalPackagePrefixes() {
    return List.copyOf(PREFIXES);
  }

  /** Restores the default prefixes (for tests). */
  public static void resetExternalPackagePrefixes() {
    PREFIXES.clear();
    PREFIXES.addAll(DEFAULT_PREFIXES);
  }

  static boolean isExternalPackage(String packageName, Class<?> type) {
    if (type != null && isJdkClass(type)) {
      return true;
    }
    if (packageName == null) {
      return false;
    }
    if (packageName.equals("com.boostercloud.metadata")) {
      return true;
    }
    String withDot = packageName + ".";
    for (String prefix : PREFIXES) {
      if (withDot.startsWith(prefix) || packageName.equals(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isJdkClass(Class<?> type) {
    Class<?> top = type;
    while (top.isArray()) {
      top = top.getComponentType();
    }
    if (top.isPrimitive()) {
      return true;
    }
    ClassLoader loader = top.getClassLoader();
    return loader == null || loader == ClassLoader.getPlatformClassLoader();
  }
}
