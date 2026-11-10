package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code QueryInterface}: a query is a record or class with a <b>static</b> handler
 * {@code public static R handle(MyQuery query, QueryInfo queryInfo)} returning any JSON-serialisable value (core finds
 * it by name). The query input ({@code QueryInput = Record<string, any>}) is a raw {@code Map<String, Object>}.
 * Implementing this marker is optional.
 */
public interface QueryInterface {
  /** Name of the static handler method core invokes. */
  String HANDLE_METHOD = "handle";
}
