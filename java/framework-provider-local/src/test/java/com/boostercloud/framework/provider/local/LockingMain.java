package com.boostercloud.framework.provider.local;

import java.nio.file.Path;

/** A process for {@code DataDirectoryTest}: locks the data directory given as argument and waits to be killed. */
public final class LockingMain {

  private LockingMain() {}

  /**
   * @param args the project directory
   * @throws InterruptedException never, the process is killed while sleeping
   */
  public static void main(String[] args) throws InterruptedException {
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(Path.of(args[0])).withWebSocketServer(false, "0"));
    runtime.lockDataDirectory();
    System.out.println("LOCKED");
    System.out.flush();
    Thread.sleep(Long.MAX_VALUE);
  }
}
