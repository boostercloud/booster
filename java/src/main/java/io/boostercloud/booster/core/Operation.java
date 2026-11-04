package io.boostercloud.booster.core;

import java.lang.reflect.Type;
import java.util.Objects;

/** Runtime operation types; outputSchemaType retains generic return metadata for generated APIs. */
public record Operation(String name, Class<?> inputType, Class<?> outputType, Type outputSchemaType) {
  public Operation(String name, Class<?> inputType, Class<?> outputType) { this(name, inputType, outputType, outputType); }
  public Operation { Objects.requireNonNull(name); Objects.requireNonNull(inputType); Objects.requireNonNull(outputType); Objects.requireNonNull(outputSchemaType); }
}
