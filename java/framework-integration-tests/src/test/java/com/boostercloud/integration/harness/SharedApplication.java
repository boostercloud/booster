package com.boostercloud.integration.harness;

import com.boostercloud.integration.helper.AppHelper;
import com.boostercloud.integration.helper.FileHelper;

/**
 * The application every {@code @EndToEnd} suite shares, started once per test run (the failsafe JVM) and stopped at
 * its end. Port of the TypeScript topology of {@code npm run integration/local}: {@code local/start/setup.ts} starts
 * one server in the sandbox {@code local-project}, {@code end-to-end/setup.ts} builds {@code applicationUnderTest}
 * with {@code setEnv()} and {@code getProviderTestHelper()}, every end-to-end suite runs against it, and
 * {@code local/stop/setup.ts} kills it through its PID file and removes the sandbox.
 *
 * <p>With {@code TESTED_PROVIDER=LOCAL} the harness launches the application ({@link AppProcess} named
 * {@value #SANDBOX_NAME}); with another provider nothing is launched and {@link AppHelper#getProviderTestHelper()}
 * finds the deployed application. It is started lazily by the first {@link #applicationUnderTest()} call (the
 * {@link EndToEndExtension} makes that call and registers the stop at the end of the run); a JVM shutdown hook stops it
 * if the run ends abnormally. A stop that is not graceful (no exit within the stop timeout, or an exit code other than
 * the SIGTERM one) fails the run.
 */
public final class SharedApplication implements AutoCloseable {

  /** The sandbox name of {@code integration/provider-specific/local/constants.ts}. */
  public static final String SANDBOX_NAME = "local-project";

  private static SharedApplication instance;

  private final AppProcess process;
  private final ApplicationUnderTest applicationUnderTest;
  private boolean closed;

  private SharedApplication(AppProcess process, ApplicationUnderTest applicationUnderTest) {
    this.process = process;
    this.applicationUnderTest = applicationUnderTest;
  }

  /**
   * @return the shared application, started on the first call
   */
  public static synchronized SharedApplication get() {
    if (instance == null || instance.closed) {
      instance = create();
      SharedApplication created = instance;
      Runtime.getRuntime().addShutdownHook(new Thread(created::close, "e2e-shared-application-stop"));
    }
    return instance;
  }

  /**
   * The TypeScript {@code applicationUnderTest} exported by {@code end-to-end/setup.ts}.
   *
   * @return the shared application under test
   */
  public static ApplicationUnderTest applicationUnderTest() {
    return get().applicationUnderTest;
  }

  /**
   * @return the process of the shared application (only with {@code TESTED_PROVIDER=LOCAL})
   * @throws IllegalStateException with another provider
   */
  public AppProcess process() {
    if (process == null) {
      throw new IllegalStateException("The shared application of TESTED_PROVIDER=" + AppHelper.testedProvider()
          + " is not a process launched by the harness");
    }
    return process;
  }

  /**
   * @return the application under test
   */
  public ApplicationUnderTest application() {
    return applicationUnderTest;
  }

  private static SharedApplication create() {
    AppHelper.setEnv();
    if (!AppHelper.LOCAL.equals(AppHelper.testedProvider())) {
      return new SharedApplication(null, ApplicationUnderTest.of(AppHelper.getProviderTestHelper()));
    }
    // local/start/setup.ts
    System.out.println("preparing sandboxed project...");
    AppProcess app = AppProcess.builder(SANDBOX_NAME).build();
    System.out.println("starting local server in " + app.projectDirectory() + "...");
    app.start();
    System.out.println("local server ready");
    try {
      // end-to-end/setup.ts
      return new SharedApplication(app,
          ApplicationUnderTest.of(AppHelper.getProviderTestHelper(LocalProviderTestHelper.options(app)), app));
    } catch (RuntimeException e) {
      app.close();
      throw e;
    }
  }

  /**
   * {@code local/stop/setup.ts}: stops the server whose PID the sandbox holds (SIGTERM) and removes the sandbox
   * (kept with {@code -Dbooster.e2e.keepProjects=true}). Idempotent.
   */
  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (process == null) {
      return;
    }
    try {
      if (process.isAlive()) {
        long pid = FileHelper.readPIDFor(process.projectDirectory());
        System.out.println("stopping local server with pid " + pid + "...");
        // a stop that is not graceful (timeout, unexpected exit code) fails the run
        process.stop();
      }
    } finally {
      System.out.println("removing sandbox project...");
      process.close();
    }
  }
}
