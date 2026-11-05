package io.boostercloud.booster.transport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Excludes a record component from generated GraphQL inputs, outputs, filters and sorts. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface GraphQLHidden {}
