package com.boostercloud.framework.types.concepts;

import com.boostercloud.metadata.MethodMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import java.util.List;
import java.util.Objects;

/**
 * What {@code @Command} registers in {@code config.commandHandlers[className]}. Port of {@code CommandMetadata};
 * the TypeScript {@code class} field is called {@code type} (Java keyword).
 *
 * @param type the command class (with a static {@code handle(command, register)} method)
 * @param properties the command properties, from {@code ClassMetadata.of(type).fields()}
 * @param methods the command methods, from {@code ClassMetadata.of(type).methods()}
 * @param authorizer the resolved authorizer (never {@code null}; deny by default)
 * @param before the before hooks, never {@code null} (default empty)
 */
public record CommandMetadata(
    Class<?> type,
    List<PropertyMetadata> properties,
    List<MethodMetadata> methods,
    CommandAuthorizer authorizer,
    List<CommandBeforeFunction> before) {

  /** Canonical constructor; copies the lists ({@code null} {@code before} becomes empty). */
  public CommandMetadata {
    Objects.requireNonNull(type, "type");
    properties = List.copyOf(properties);
    methods = List.copyOf(methods);
    before = before == null ? List.of() : List.copyOf(before);
  }
}
