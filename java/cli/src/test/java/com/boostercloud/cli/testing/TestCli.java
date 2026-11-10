package com.boostercloud.cli.testing;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.Main;
import com.boostercloud.cli.common.ColorSupport;
import com.boostercloud.cli.services.PortProbe;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs the CLI in-process against a directory, with captured output, a scripted standard input and a fake
 * process runner. Colours are disabled so that the output can be compared literally.
 */
public final class TestCli {
  private final Path cwd;
  private final Map<String, String> environment = new LinkedHashMap<>();
  private String stdin = "";
  private String cliVersion;
  private PortProbe portProbe = port -> true;
  private CliContext context;
  private FakeProcessService process;
  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;

  /**
   * The result of a run.
   *
   * @param exitCode the exit code
   * @param stdout the standard output
   * @param stderr the standard error
   */
  public record Result(int exitCode, String stdout, String stderr) {}

  /**
   * Creates the runner.
   *
   * @param cwd the directory where the CLI runs
   */
  public TestCli(Path cwd) {
    this.cwd = cwd;
    ColorSupport.setLevel(0);
  }

  /** @param value the standard input text @return this runner */
  public TestCli stdin(String value) {
    this.stdin = value;
    context = null;
    return this;
  }

  /** @param name a variable @param value its value @return this runner */
  public TestCli env(String name, String value) {
    environment.put(name, value);
    context = null;
    return this;
  }

  /** @param value the CLI version @return this runner */
  public TestCli cliVersion(String value) {
    this.cliVersion = value;
    context = null;
    return this;
  }

  /** @param value the port probe @return this runner */
  public TestCli portProbe(PortProbe value) {
    this.portProbe = value;
    context = null;
    return this;
  }

  /** @return the context used by the next run (created on demand and kept until a setting changes) */
  public CliContext context() {
    if (context == null) {
      out = new ByteArrayOutputStream();
      err = new ByteArrayOutputStream();
      CliContext.Builder builder =
          CliContext.builder()
              .cwd(cwd)
              .environment(environment)
              .out(new PrintStream(out, true, StandardCharsets.UTF_8))
              .err(new PrintStream(err, true, StandardCharsets.UTF_8))
              .stdin(stdin)
              .interactive(false)
              .unicode(true)
              .clock(Clock.fixed(Instant.parse("2024-01-31T10:20:30.123Z"), ZoneOffset.UTC))
              .portProbe(portProbe)
              .pollInterval(Duration.ofMillis(1))
              .windows(false);
      if (cliVersion != null) {
        builder.cliVersion(cliVersion);
      }
      FakeProcessService[] holder = new FakeProcessService[1];
      CliContext[] self = new CliContext[1];
      holder[0] = new FakeProcessService(cwd, () -> self[0].environment());
      builder.process(holder[0]);
      context = builder.build();
      self[0] = context;
      process = holder[0];
    }
    return context;
  }

  /** @return the fake process runner of the current context */
  public FakeProcessService process() {
    context();
    return process;
  }

  /**
   * Runs the CLI.
   *
   * @param args the command line
   * @return the result
   */
  public Result run(String... args) {
    CliContext ctx = context();
    out.reset();
    err.reset();
    int exitCode = Main.run(ctx, args);
    return new Result(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  /** @return what was written to the standard output since the last run started */
  public String stdout() {
    return out.toString(StandardCharsets.UTF_8);
  }

  /** @return what was written to the standard error since the last run started */
  public String stderr() {
    return err.toString(StandardCharsets.UTF_8);
  }
}
