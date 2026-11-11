package com.boostercloud.framework.provider.azure.infrastructure.types;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A function app package that was built ({@code ZipResource} of {@code types/zip-resource.ts}).
 *
 * @param path the package in the project root
 * @param fileName its file name
 * @param name {@code functions} (main, rocket, legacy) or {@code consumer-functions} (event stream consumer)
 */
public record ZipResource(Path path, String fileName, String name) {

  /** Canonical constructor. */
  public ZipResource {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(fileName, "fileName");
    Objects.requireNonNull(name, "name");
  }
}
