package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @SchemaMigration(ConceptClass)} decorator: registers the {@link ToVersion} methods of the class
 * in {@code config.schemaMigrations[<Concept>]} (several classes may migrate one concept). Gaps are reported by
 * {@code config.validate()} during {@code Booster.start}.
 *
 * <p>Registration errors: {@code No migration methods found in this class. Define at least one migration and annotate
 * it with @ToVersion()}; {@code Found duplicated migration for '<Concept>' in migration class '<Class>': There is an
 * already defined migration for version <n>}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SchemaMigration {
  /**
   * @return the migrated concept (command, event, entity, read model...)
   */
  Class<?> value();
}
