package com.boostercloud.cli.integration.helper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

/**
 * Free TCP ports for the applications the suite starts. Other builds on the same machine start Booster applications
 * at the same time, so nothing uses the fixed defaults (3000 for HTTP, 65529 for the local provider's websocket
 * server).
 */
public final class Ports {
  private static final Set<Integer> HANDED_OUT = new HashSet<>();

  private Ports() {}

  /**
   * A port that is free now and was not handed out before by this JVM.
   *
   * @return the port
   */
  public static synchronized int freePort() {
    for (int attempt = 0; attempt < 50; attempt++) {
      try (ServerSocket socket = new ServerSocket()) {
        socket.setReuseAddress(false);
        socket.bind(new InetSocketAddress("localhost", 0));
        int port = socket.getLocalPort();
        if (HANDED_OUT.add(port)) {
          return port;
        }
      } catch (IOException e) {
        throw new UncheckedIOException("Could not find a free port", e);
      }
    }
    throw new IllegalStateException("Could not find a free port that was not handed out before");
  }

  /**
   * @param port a port
   * @return whether something accepts connections on {@code localhost:<port>}
   */
  public static boolean isListening(int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("localhost", port), 500);
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
