package com.boostercloud.integration.harness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Free TCP ports for the applications the harness launches. Several Booster applications (of this and other
 * worktrees) may run on the same machine at the same time, so the harness never uses the fixed TypeScript ports
 * (3000 and 65529): it asks the operating system for ephemeral ports. Ports handed out by this JVM are remembered and
 * never handed out twice.
 */
public final class FreePorts {

  private static final Set<Integer> ALLOCATED = ConcurrentHashMap.newKeySet();

  private FreePorts() {}

  /**
   * @param count how many ports
   * @return distinct ports that were free a moment ago and were not returned before by this JVM
   */
  public static List<Integer> allocate(int count) {
    if (count < 1) {
      throw new IllegalArgumentException("count must be positive: " + count);
    }
    List<ServerSocket> sockets = new ArrayList<>();
    List<Integer> ports = new ArrayList<>();
    try {
      // Keep every socket open until all the ports are known, so that the operating system gives distinct ports
      while (ports.size() < count) {
        ServerSocket socket = new ServerSocket(0);
        socket.setReuseAddress(true);
        sockets.add(socket);
        int port = socket.getLocalPort();
        if (ALLOCATED.add(port)) {
          ports.add(port);
        }
      }
      return List.copyOf(ports);
    } catch (IOException e) {
      ports.forEach(ALLOCATED::remove);
      throw new UncheckedIOException("Could not allocate " + count + " free ports", e);
    } finally {
      for (ServerSocket socket : sockets) {
        try {
          socket.close();
        } catch (IOException ignored) {
          // nothing to do
        }
      }
    }
  }

  /**
   * @return one free port
   */
  public static int allocate() {
    return allocate(1).getFirst();
  }

  /**
   * @param port a port
   * @return whether a server socket can be bound to it now
   */
  public static boolean isFree(int port) {
    try (ServerSocket socket = new ServerSocket(port)) {
      socket.setReuseAddress(true);
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
