package com.boostercloud.framework.provider.local;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Level;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A process for {@code RecoveryTest}: stores events one by one (printing each acknowledged id), then stores a batch
 * whose dispatch never finishes and waits to be killed.
 */
public final class CrashingApp {

  /** Events stored and dispatched before the crash. */
  static final int COMMITTED_EVENTS = 20;

  /** Events stored in the batch whose dispatch is interrupted. */
  static final int BATCH_SIZE = 3;

  private CrashingApp() {}

  /**
   * @param args the project directory
   * @throws InterruptedException never, the process is killed while sleeping
   */
  public static void main(String[] args) throws InterruptedException {
    BoosterConfig config = new BoosterConfig("test");
    config.logLevel = Level.ERROR;
    boolean[] block = {false};
    FakeUserApp app = new FakeUserApp(config) {
      @Override
      public void boosterEventDispatcher(Object rawEvents) {
        if (block[0]) {
          System.out.println("DISPATCHING");
          System.out.flush();
          try {
            Thread.sleep(Long.MAX_VALUE);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
        super.boosterEventDispatcher(rawEvents);
      }
    };
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(Path.of(args[0])).withWebSocketServer(false, "0").withUserApp(() -> app));
    LocalProvider provider = new LocalProvider(runtime, List.of(), Provider::loadInfrastructurePackage);
    app.provider(provider);
    for (int i = 0; i < COMMITTED_EVENTS; i++) {
      EventEnvelope stored = provider.events().store(List.of(RecoveryTest.event("cart-1", i)), config).get(0);
      System.out.println("STORED " + stored.id());
      System.out.flush();
    }
    block[0] = true;
    List<com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope> batch = new ArrayList<>();
    for (int i = 0; i < BATCH_SIZE; i++) {
      batch.add(RecoveryTest.event("cart-1", 100 + i));
    }
    provider.events().store(batch, config);
    Thread.sleep(Long.MAX_VALUE);
  }
}
