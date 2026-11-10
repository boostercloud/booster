package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @ToVersion(toVersion, { fromSchema, toSchema })} decorator, on an <b>instance</b> method of a
 * {@link SchemaMigration} class: {@code public ProductV2 changeField2(ProductV1 old)}. A new instance of the
 * migration class (no-argument constructor) is created for every migration step.
 *
 * <p>Registration error: {@code Migration versions must always be greater than 1}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToVersion {
  /**
   * @return the version this method migrates to (at least 2)
   */
  int toVersion();

  /**
   * @return the schema of the received value
   */
  Class<?> fromSchema();

  /**
   * @return the schema of the returned value
   */
  Class<?> toSchema();
}
