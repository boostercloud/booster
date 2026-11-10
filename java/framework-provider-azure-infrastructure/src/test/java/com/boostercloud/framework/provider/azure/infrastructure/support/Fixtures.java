package com.boostercloud.framework.provider.azure.infrastructure.support;

import com.boostercloud.framework.provider.azure.infrastructure.InfrastructureContext;
import com.boostercloud.framework.provider.azure.infrastructure.helper.DeploySources;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.ScheduleInterface;
import com.boostercloud.framework.types.concepts.EventStreamConfiguration;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Configurations and contexts shared by the tests. */
public final class Fixtures {

  /** The application class written to {@code BOOSTER_APP_CLASS}. */
  public static final String APP_CLASS = "com.example.store.Main";

  /** The Azure settings of the tests. */
  public static final Params.Configuration CONFIGURATION =
      Params.Configuration.of("app-id", "app-secret", "tenant-id", "subscription-id", "East US");

  private Fixtures() {}

  /** A read model class. */
  public record CartReadModel(String id) {}

  /** Another read model class. */
  public record ProductReadModel(String id) {}

  /** A scheduled command class. */
  public static final class CheckCart {}

  /** Another scheduled command class. */
  public static final class CleanUp {}

  /**
   * @return {@code my-store} / {@code production} with two read models and one scheduled command (and one without
   *     schedule), subscriptions on, streaming off, silent logger
   */
  public static BoosterConfig config() {
    BoosterConfig config = new BoosterConfig("production");
    config.appName = "my-store";
    config.logger = Logger.NOOP;
    config.readModels.put("CartReadModel",
        new ReadModelMetadata(CartReadModel.class, List.of(), null, List.of()));
    config.readModels.put("ProductReadModel",
        new ReadModelMetadata(ProductReadModel.class, List.of(), null, List.of()));
    config.scheduledCommandHandlers.put("CheckCart",
        new ScheduledCommandMetadata(CheckCart.class, new ScheduleInterface("0/5", null, null, null, null, "2030")));
    config.scheduledCommandHandlers.put("CleanUp",
        new ScheduledCommandMetadata(CleanUp.class, new ScheduleInterface(null, null, null, null, null, null)));
    config.env.put("CUSTOM_SETTING", "custom value");
    return config;
  }

  /**
   * @return {@link #config()} with event streaming (topic {@code cart-events}, 4 partitions, 2 days, 0 retries, fixed)
   */
  public static BoosterConfig streamingConfig() {
    BoosterConfig config = config();
    config.eventStreamConfiguration = EventStreamConfiguration.enabled(new EventStreamConfiguration.Parameters(
        "cart-events", 4, 2, 600, 0, EventStreamConfiguration.Mode.FIXED));
    return config;
  }

  /**
   * @return {@link #config()} without subscriptions
   */
  public static BoosterConfig noSubscriptionsConfig() {
    BoosterConfig config = config();
    config.enableSubscriptions = false;
    return config;
  }

  /**
   * Package sources under {@code root}: {@code target/classes} with two class files and a resource, two jars in
   * {@code target/lib} and an asset folder.
   *
   * @param root the project root
   * @return the sources
   */
  public static DeploySources sources(Path root) {
    try {
      Path classes = root.resolve("target/classes");
      Files.createDirectories(classes.resolve("com/example/store"));
      Files.write(classes.resolve("com/example/store/Main.class"), new byte[] {(byte) 0xCA, (byte) 0xFE, 1});
      Files.write(classes.resolve("com/example/store/Cart.class"), new byte[] {(byte) 0xCA, (byte) 0xFE, 2});
      Files.writeString(classes.resolve("application.properties"), "a=1");
      Path lib = root.resolve("target/lib");
      Files.createDirectories(lib);
      Files.write(lib.resolve("booster-framework-core-4.1.0.jar"), new byte[] {'P', 'K', 1});
      Files.write(lib.resolve("booster-framework-provider-azure-4.1.0.jar"), new byte[] {'P', 'K', 2});
      Path assets = root.resolve("assets");
      Files.createDirectories(assets.resolve("img"));
      Files.writeString(assets.resolve("img/logo.txt"), "logo");
      Files.writeString(root.resolve("host.json"), "{\"version\": \"custom\"}");
      return new DeploySources(root, List.of(classes, lib.resolve("booster-framework-core-4.1.0.jar"),
          lib.resolve("booster-framework-provider-azure-4.1.0.jar")), List.of(Path.of("assets"), Path.of("host.json")));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @param root the project root
   * @param commands the command runner
   * @param http the HTTP transport
   * @param sleeper the sleeper
   * @return a context with fixed settings and fake I/O
   */
  public static InfrastructureContext context(
      Path root, RecordingCommandRunner commands, FakeHttpTransport http, RecordingSleeper sleeper) {
    return InfrastructureContext.builder()
        .projectRoot(root)
        .applicationClass(APP_CLASS)
        .deploySources(sources(root))
        .commandRunner(commands)
        .httpTransport(http)
        .sleeper(sleeper)
        .configuration(CONFIGURATION)
        .build();
  }
}
