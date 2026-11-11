package com.boostercloud.cli.services.generator.target;

import java.util.Arrays;
import java.util.List;

/**
 * An import declaration of a generated file ({@code ImportDeclaration}): several classes of one package.
 *
 * @param packagePath the Java package
 * @param commaSeparatedComponents the imported class names, separated by {@code ", "}
 */
public record ImportDeclaration(String packagePath, String commaSeparatedComponents) {
  /**
   * The fully qualified names of the imported classes (Java imports one class per line).
   *
   * @return for example {@code [com.boostercloud.framework.core.decorators.Entity, ...Reduces]}
   */
  public List<String> qualifiedNames() {
    return Arrays.stream(commaSeparatedComponents.split(","))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .map(component -> packagePath.isEmpty() ? component : packagePath + "." + component)
        .toList();
  }
}
