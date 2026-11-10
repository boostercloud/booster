package com.boostercloud.cli.integration.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Reads one output stream of a process on a virtual thread, as it is produced, keeping everything read. An optional
 * listener sees every chunk (the prompt driver of {@code new:project} reacts to the questions as they appear, like
 * the {@code stdout.on('data')} handler of the TypeScript suite).
 */
final class OutputCapture {
  private final StringBuilder text = new StringBuilder();
  private final Thread reader;

  OutputCapture(InputStream stream, String name, Consumer<String> listener) {
    this.reader =
        Thread.ofVirtual()
            .name(name)
            .start(
                () -> {
                  try (Reader in = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    char[] buffer = new char[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                      String chunk = new String(buffer, 0, read);
                      synchronized (text) {
                        text.append(chunk);
                      }
                      if (listener != null) {
                        listener.accept(chunk);
                      }
                    }
                  } catch (IOException ignored) {
                    // the process closed the stream: keep what was read
                  }
                });
  }

  /**
   * @return everything read so far
   */
  String text() {
    synchronized (text) {
      return text.toString();
    }
  }

  /**
   * Waits until the stream is closed (the process exited and its descendants released the pipe), at most the given
   * time.
   *
   * @param millis the maximum time
   * @return whether the stream was fully read
   */
  boolean awaitEnd(long millis) {
    try {
      return reader.join(java.time.Duration.ofMillis(Math.max(1, millis)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
