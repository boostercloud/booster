package com.boostercloud.framework.core.decorators;

import com.boostercloud.framework.types.concepts.ReadModelBeforeFunction;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @ReadModel(attributes)} decorator: registers the class in {@code config.readModels} with its
 * properties (calculated fields carry the {@link CalculatedField#dependsOn()} dependencies, every other property an
 * empty list), an authorizer and the {@code before} hooks. Projections are static methods annotated with
 * {@link Projects}.
 *
 * <p>Registration error: {@code A read model called <Name> is already registered.} + clean-build hint.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReadModel {
  /**
   * Who may read it; same rules as {@link Command#authorize()}, with
   * {@link com.boostercloud.framework.types.concepts.ReadModelAuthorizer} custom authorizers.
   *
   * @return the authorization
   */
  Class<?>[] authorize() default {};

  /**
   * Hooks applied in order to every read model request envelope.
   *
   * @return the hook classes
   */
  Class<? extends ReadModelBeforeFunction>[] before() default {};
}
