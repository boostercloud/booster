package com.boostercloud.cli.services.generator.target;

import java.util.List;

/**
 * Generator information with import declarations ({@code HasImports}).
 *
 * @param imports the import declarations
 */
public record HasImports(List<ImportDeclaration> imports) {
  /** Makes the list immutable. */
  public HasImports {
    imports = List.copyOf(imports);
  }
}
