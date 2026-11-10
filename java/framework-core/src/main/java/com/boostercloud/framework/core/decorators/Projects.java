package com.boostercloud.framework.core.decorators;

import com.boostercloud.framework.types.concepts.ReadModelJoinKeyFunction;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/**
 * Port of the {@code @Projects(Entity, joinKey, unProject?)} decorator, on a <b>static</b> method of a read model
 * class: appends {@code {read model class, method name, join key}} to {@code config.projections[<Entity>]} and, with
 * {@link #unProject()}, {@code {read model class, unProject, join key}} to {@code config.unProjections[<Entity>]}.
 * Exactly one of {@link #joinKey()} (an entity property) and {@link #joinKeyFunction()} must be given.
 *
 * <pre>{@code
 * @Projects(entity = Cart.class, joinKey = "id")
 * public static ProjectionResult<CartReadModel> updateWithCart(Cart cart, CartReadModel old) { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Projects.List.class)
public @interface Projects {
  /**
   * @return the projected entity class
   */
  Class<?> entity();

  /**
   * @return the entity property whose value is the read model id (or a list of ids); {@code ""} when
   *     {@link #joinKeyFunction()} is used
   */
  String joinKey() default "";

  /**
   * @return a class (with a no-argument constructor) computing the read model filter from the entity; the default
   *     {@link NoJoinKeyFunction} means "use {@link #joinKey()}"
   */
  Class<? extends ReadModelJoinKeyFunction<?>> joinKeyFunction() default NoJoinKeyFunction.class;

  /**
   * @return the name of the static method of the same read model class run when an event of the entity is deleted
   *     ({@code ""} for none)
   */
  String unProject() default "";

  /** Sentinel default of {@link #joinKeyFunction()}; never called. */
  final class NoJoinKeyFunction implements ReadModelJoinKeyFunction<Object> {
    private NoJoinKeyFunction() {}

    @Override
    public Map<String, Object> joinKey(Object entity) {
      throw new UnsupportedOperationException("NoJoinKeyFunction is a sentinel");
    }
  }

  /** Container of repeated {@link Projects}. */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface List {
    /**
     * @return the annotations
     */
    Projects[] value();
  }
}
