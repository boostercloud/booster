package com.boostercloud.framework.provider.local.infrastructure.testhelper;

import com.boostercloud.framework.provider.local.Paths;
import java.nio.file.Path;
import java.util.Map;

/**
 * Counts what a local application has stored, reading its {@code .booster} files with {@link LocalStoreReader}.
 * Port of {@code test-helper/local-counters.ts}, whose methods are unimplemented stubs returning {@code -1}; these
 * return the real numbers (the same quantities as the Azure test helper's counters).
 */
public class LocalCounters {

  private final String stackName;
  private final LocalStoreReader reader;

  /**
   * {@code new LocalCounters(stackName)}: reads the files of the working directory.
   *
   * @param stackName {@code <appName>-app}
   */
  public LocalCounters(String stackName) {
    this(stackName, null);
  }

  /**
   * @param stackName {@code <appName>-app}
   * @param baseDirectory the project directory, or {@code null} for the working directory
   */
  public LocalCounters(String stackName, Path baseDirectory) {
    this.stackName = stackName;
    this.reader = new LocalStoreReader(baseDirectory);
  }

  /**
   * @return the stack name ({@code <appName>-app}); the local provider has one set of files per project, so it does
   *     not select anything
   */
  public String stackName() {
    return stackName;
  }

  /**
   * @return the number of stored GraphQL subscriptions ({@code .booster/subscriptions.json})
   */
  public long subscriptions() {
    return reader.count(Paths.SUBSCRIPTION_DATABASE, Map.of());
  }

  /**
   * @return the number of stored websocket connections ({@code .booster/connections.json})
   */
  public long connections() {
    return reader.count(Paths.CONNECTIONS_DATABASE, Map.of());
  }

  /**
   * @return the number of stored events ({@code kind: "event"} in {@code .booster/events.json}, soft-deleted ones
   *     included; snapshots are not counted)
   */
  public long events() {
    return reader.count(Paths.EVENTS_DATABASE, Map.of("kind", "event"));
  }

  /**
   * @param readModelName the read model name
   * @return the number of stored instances of it ({@code typeName} in {@code .booster/read_models.json})
   */
  public long readModels(String readModelName) {
    return reader.count(Paths.READ_MODELS_DATABASE, Map.of("typeName", readModelName));
  }
}
