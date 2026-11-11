package com.boostercloud.cli.services;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Checks whether something listens on a local TCP port (used to know when {@code boost start} is ready). */
@FunctionalInterface
public interface PortProbe {
  /**
   * Whether a server accepts connections on the port of the local machine.
   *
   * @param port the TCP port
   * @return {@code true} when a connection succeeds
   */
  boolean isListening(int port);

  /**
   * The probe that tries to connect to {@code localhost}.
   *
   * @return the live probe
   */
  static PortProbe live() {
    return port -> {
      for (String host : new String[] {"127.0.0.1", "::1"}) {
        try (Socket socket = new Socket()) {
          socket.connect(new InetSocketAddress(host, port), 200);
          return true;
        } catch (IOException | IllegalArgumentException e) {
          // try the next address
        }
      }
      return false;
    };
  }
}
