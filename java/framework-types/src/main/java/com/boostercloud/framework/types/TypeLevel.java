package com.boostercloud.framework.types;

import java.util.Objects;

/**
 * Port of {@code typelevel.ts}.
 *
 * <p>The TypeScript type helpers have direct Java equivalents and no counterpart here: {@code Class<T>} and
 * {@code AnyClass} are {@link java.lang.Class}, {@code Instance} is any {@link Object} (its class name is
 * {@code getClass().getSimpleName()}), and {@code ReadOnlyNonEmptyArray<T>} is a {@link java.util.List} (non-emptiness
 * is not enforced at run time in TypeScript either).
 */
public final class TypeLevel {

  private TypeLevel() {}

  /**
   * Humanizes the class name of an instance: {@code InvalidParameterError} becomes {@code Invalid Parameter Error},
   * {@code HTTPError} becomes {@code HTTP Error}. Used for the {@code title} of HTTP error bodies.
   *
   * @param instance any object (errors in practice)
   * @return the title
   */
  public static String toClassTitle(Object instance) {
    Objects.requireNonNull(instance, "instance");
    Class<?> type = instance instanceof Class<?> c ? c : instance.getClass();
    return toClassTitle(type.getSimpleName());
  }

  /**
   * Same as {@link #toClassTitle(Object)} on a class name: inserts a space before every uppercase letter followed by
   * a lowercase one, then between every lowercase letter followed by an uppercase one, then trims (ASCII only, as the
   * TypeScript regular expressions).
   *
   * @param className a class name
   * @return the title
   */
  public static String toClassTitle(String className) {
    return className.replaceAll("([A-Z])([a-z])", " $1$2").replaceAll("([a-z])([A-Z])", "$1 $2").strip();
  }
}
