package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @DataMigration({ order })} decorator: registers the class in {@code config.dataMigrationHandlers}.
 * The class must declare {@code public static void start(Register register)}.
 *
 * <p>Registration error: {@code A data migration called <Name> is already registered.} + clean-build hint.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataMigration {
  /**
   * @return the position of this migration (ascending; ties keep registration order)
   */
  int order();
}
