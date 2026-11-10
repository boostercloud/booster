package com.boostercloud.cli.services.packagemanager;

/** The dependencies of a project could not be installed. */
public final class InstallDependenciesError extends PackageManagerError {
  private static final long serialVersionUID = 1L;

  /**
   * Wraps an error.
   *
   * @param error the underlying error
   */
  public InstallDependenciesError(Throwable error) {
    super(error);
  }

  @Override
  public String name() {
    return "InstallDependenciesError";
  }
}
