package com.boostercloud.cli.services.packagemanager;

/** A build-tool command (script) of a project failed. */
public final class RunScriptError extends PackageManagerError {
  private static final long serialVersionUID = 1L;

  /**
   * Wraps an error.
   *
   * @param error the underlying error
   */
  public RunScriptError(Throwable error) {
    super(error);
  }

  @Override
  public String name() {
    return "RunScriptError";
  }
}
