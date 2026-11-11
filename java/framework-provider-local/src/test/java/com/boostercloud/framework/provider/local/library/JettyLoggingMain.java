package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.provider.local.services.GraphQLService;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Level;

/**
 * A process for {@code JettyLoggingTest}: {@code jetty} starts and stops the websocket server (Jetty logs through
 * SLF4J), {@code plain} only asks SLF4J for a logger, without {@link JettyLogging#prepare()}.
 */
public final class JettyLoggingMain {

  private JettyLoggingMain() {}

  /**
   * @param args {@code jetty} or {@code plain}
   */
  public static void main(String[] args) {
    if (args[0].equals("plain")) {
      org.slf4j.LoggerFactory.getLogger("plain").info("hello");
    } else {
      BoosterConfig config = new BoosterConfig("test");
      config.logLevel = Level.ERROR;
      try (WebSocketServerAdapter server = new WebSocketServerAdapter(new GraphQLService(() -> null), () -> config,
          "0", true)) {
        System.out.println(server.isRunning() ? "STARTED" : "NOT STARTED");
      }
    }
    System.out.println("verbosity=" + System.getProperty(JettyLogging.SLF4J_INTERNAL_VERBOSITY));
  }
}
