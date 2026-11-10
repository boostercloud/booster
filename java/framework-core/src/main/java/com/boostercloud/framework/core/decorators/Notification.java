package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @Notification(options?)} decorator: an event that does not belong to an entity. Registers the
 * class in {@code config.notifications} and, unless the topic is the empty string, maps it in
 * {@code config.eventToTopic} / {@code config.topicToEvent} (the latter is last-registration-wins).
 *
 * <p>Registration error: {@code A notification called <Name> is already registered.} + clean-build hint, also when an
 * {@link Event} with the same name exists.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Notification {
  /** The topic used when none is given ({@code 'defaultTopic'} in TypeScript). */
  String DEFAULT_TOPIC = "defaultTopic";

  /**
   * @return the topic ({@link #DEFAULT_TOPIC} by default; {@code ""} stores no topic mapping)
   */
  String topic() default DEFAULT_TOPIC;
}
