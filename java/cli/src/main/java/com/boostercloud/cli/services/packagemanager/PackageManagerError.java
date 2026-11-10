package com.boostercloud.cli.services.packagemanager;

import com.boostercloud.cli.common.CliException;

/**
 * A failed package-manager operation ({@code PackageManagerError} in TypeScript: the union of
 * {@link InstallDependenciesError} and {@link RunScriptError}). It wraps the underlying error.
 */
public abstract sealed class PackageManagerError extends CliException
    permits InstallDependenciesError, RunScriptError {
  private static final long serialVersionUID = 1L;

  PackageManagerError(Throwable error) {
    super(error.getMessage(), error);
  }

  /**
   * The wrapped error ({@code error} property in TypeScript).
   *
   * @return the underlying error
   */
  public Throwable error() {
    return getCause();
  }
}
