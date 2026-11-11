package com.boostercloud.framework.core.decorators;

import com.boostercloud.framework.types.concepts.QueryBeforeFunction;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @Query(attributes)} decorator: registers the class in {@code config.queryHandlers}. The class
 * must declare {@code public static <R> handle(TheQuery query, QueryInfo queryInfo)}; its result is returned to
 * GraphQL as is.
 *
 * <p>Registration error: {@code A query called <Name> is already registered.} + clean-build hint.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Query {
  /**
   * Who may run the query; same rules as {@link Command#authorize()}, with
   * {@link com.boostercloud.framework.types.concepts.QueryAuthorizer} custom authorizers.
   *
   * @return the authorization
   */
  Class<?>[] authorize() default {};

  /**
   * Hooks applied in order to the raw input before the query instance is created.
   *
   * @return the hook classes
   */
  Class<? extends QueryBeforeFunction>[] before() default {};
}
