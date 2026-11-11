package com.boostercloud.cli.testing;

import com.boostercloud.cli.services.OraLogger;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/** An {@link OraLogger} that records its calls (the fake logger of the TypeScript tests). */
public class RecordingOraLogger extends OraLogger {
  /**
   * A recorded call.
   *
   * @param method {@code start}, {@code succeed}, {@code fail}, {@code info} or {@code warn}
   * @param text the text
   */
  public record LogCall(String method, String text) {}

  private final List<LogCall> calls = new ArrayList<>();

  /** Creates the logger, discarding the output. */
  public RecordingOraLogger() {
    super(new PrintStream(OutputStream.nullOutputStream()), false, true);
  }

  /** @return the calls, in order */
  public List<LogCall> calls() {
    return List.copyOf(calls);
  }

  /**
   * @param method a method name
   * @return the texts passed to it, in order
   */
  public List<String> texts(String method) {
    return calls.stream().filter(c -> c.method().equals(method)).map(LogCall::text).toList();
  }

  @Override
  public synchronized OraLogger start(String newText) {
    calls.add(new LogCall("start", newText));
    return this;
  }

  @Override
  public OraLogger succeed(String message) {
    calls.add(new LogCall("succeed", message));
    return this;
  }

  @Override
  public OraLogger fail(String message) {
    calls.add(new LogCall("fail", message));
    return this;
  }

  @Override
  public OraLogger info(String message) {
    calls.add(new LogCall("info", message));
    return this;
  }

  @Override
  public OraLogger warn(String message) {
    calls.add(new LogCall("warn", message));
    return this;
  }
}
