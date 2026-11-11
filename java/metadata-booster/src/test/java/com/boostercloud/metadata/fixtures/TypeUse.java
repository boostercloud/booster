package com.boostercloud.metadata.fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Holder of a type-use {@code Nullable} annotation, like JSpecify's {@code @Nullable}. */
public final class TypeUse {
  private TypeUse() {}

  /** Type-use nullability marker. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE_USE)
  public @interface Nullable {}
}
