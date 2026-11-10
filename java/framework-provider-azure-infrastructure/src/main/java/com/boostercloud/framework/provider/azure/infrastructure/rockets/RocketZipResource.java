package com.boostercloud.framework.provider.azure.infrastructure.rockets;

import com.boostercloud.framework.provider.azure.infrastructure.types.ZipResource;
import java.util.Objects;

/**
 * A rocket package and the function app it is deployed to ({@code RocketZipResource}).
 *
 * @param functionAppName the function app
 * @param zip the package
 */
public record RocketZipResource(String functionAppName, ZipResource zip) {

  /** Canonical constructor. */
  public RocketZipResource {
    Objects.requireNonNull(zip, "zip");
  }
}
