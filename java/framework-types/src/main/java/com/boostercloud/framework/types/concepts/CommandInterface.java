package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code CommandInterface}: a command is a record or class with a <b>static</b> handler
 * {@code public static R handle(MyCommand command, Register register)} (Java interfaces cannot require static
 * methods; core finds it by name). The handler may return a value, exposed as the mutation result ({@code void}
 * means {@code true}). The command input of TypeScript ({@code CommandInput = Record<string, any>}) is a raw
 * {@code Map<String, Object>}. Implementing this marker is optional.
 */
public interface CommandInterface {
  /** Name of the static handler method core invokes. */
  String HANDLE_METHOD = "handle";
}
