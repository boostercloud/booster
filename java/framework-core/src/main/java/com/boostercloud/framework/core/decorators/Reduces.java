package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @Reduces(EventClass)} decorator, on a <b>static</b> method of an entity class:
 * {@code public static Cart changeItem(CartItemChanged event, Cart currentCart)} (the current snapshot is
 * {@code null} for the first event). Registers {@code config.reducers[<Event>] = {entity class, method name}}; a
 * method may reduce several events. Registered independently of {@link Entity}, as in TypeScript.
 *
 * <p>Registration error (one reducer per event): {@code Error registering reducer: The event <E> was already
 * registered to be reduced by method <m> in the entity <C>.} + clean-build hint.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Reduces.List.class)
public @interface Reduces {
  /**
   * @return the reduced event class
   */
  Class<?> value();

  /** Container of repeated {@link Reduces}. */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface List {
    /**
     * @return the annotations
     */
    Reduces[] value();
  }
}
