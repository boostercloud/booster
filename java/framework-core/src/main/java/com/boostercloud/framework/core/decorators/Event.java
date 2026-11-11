package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @Event} decorator: registers the class in {@code config.events}. The event names the entity
 * instance it belongs to with a public no-argument {@code entityID()} method (see
 * {@link com.boostercloud.framework.types.concepts.EventInterface}).
 *
 * <p>Registration error: {@code A event called <Name> is already registered.} (sic) + clean-build hint.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Event {}
