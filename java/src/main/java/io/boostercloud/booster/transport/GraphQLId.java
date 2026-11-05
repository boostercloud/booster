package io.boostercloud.booster.transport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Maps a string identifier (or a container of identifiers) to GraphQL ID without requiring UUID syntax. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface GraphQLId {}
