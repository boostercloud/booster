package com.boostercloud.cli.testing;

import com.boostercloud.cli.services.process.CommandFailedException;
import com.boostercloud.cli.services.process.ProcessError;
import com.boostercloud.cli.services.process.ProcessService;
import com.boostercloud.cli.services.process.RunningProcess;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A {@link ProcessService} that records the commands instead of running them (the TypeScript test layer). */
public final class FakeProcessService implements ProcessService {
  /**
   * A recorded command.
   *
   * @param command the program and its arguments
   * @param cwd the working directory
   * @param environment the environment at the time of the call
   * @param spawned whether it was {@link #spawn}ed (attached to the terminal) rather than executed
   */
  public record Call(List<String> command, Path cwd, Map<String, String> environment, boolean spawned) {
    /** @return the command line joined with spaces */
    public String line() {
      return String.join(" ", command);
    }
  }

  private final Path cwd;
  private final Supplier<Map<String, String>> environment;
  private final List<Call> calls = new ArrayList<>();
  private final List<Failure> failures = new ArrayList<>();
  private final List<Consumer<Call>> listeners = new ArrayList<>();
  private int spawnExitCode;
  private boolean spawnAlive;

  private record Failure(Predicate<List<String>> matcher, int exitCode, String output) {}

  /**
   * Creates the fake.
   *
   * @param cwd the directory returned by {@link #cwd()}
   * @param environment the environment recorded with every call
   */
  public FakeProcessService(Path cwd, Supplier<Map<String, String>> environment) {
    this.cwd = cwd;
    this.environment = environment;
  }

  /**
   * Makes the commands that match fail.
   *
   * @param matcher selects the commands
   * @param exitCode the exit code
   * @param output the error output
   * @return this fake
   */
  public FakeProcessService failWhen(Predicate<List<String>> matcher, int exitCode, String output) {
    failures.add(new Failure(matcher, exitCode, output));
    return this;
  }

  /**
   * Runs an action whenever a command is recorded (for example to create {@code target/classes}).
   *
   * @param listener the action
   * @return this fake
   */
  public FakeProcessService onCall(Consumer<Call> listener) {
    listeners.add(listener);
    return this;
  }

  /**
   * The exit code of spawned processes.
   *
   * @param exitCode the exit code
   * @return this fake
   */
  public FakeProcessService spawnExitCode(int exitCode) {
    this.spawnExitCode = exitCode;
    return this;
  }

  /**
   * Whether spawned processes report being alive until waited for.
   *
   * @param alive the value
   * @return this fake
   */
  public FakeProcessService spawnAlive(boolean alive) {
    this.spawnAlive = alive;
    return this;
  }

  /** @return every recorded command, in order */
  public List<Call> calls() {
    return List.copyOf(calls);
  }

  /** @return every recorded command line, in order */
  public List<String> lines() {
    return calls.stream().map(Call::line).toList();
  }

  @Override
  public String exec(List<String> command, Path workingDirectory) {
    Call call = new Call(List.copyOf(command), workingDirectory, new LinkedHashMap<>(environment.get()), false);
    calls.add(call);
    listeners.forEach(l -> l.accept(call));
    for (Failure failure : failures) {
      if (failure.matcher().test(command)) {
        String line = String.join(" ", command);
        throw new ProcessError(
            new CommandFailedException(
                "Command failed with exit code " + failure.exitCode() + ": " + line + "\n" + failure.output(),
                line,
                failure.exitCode(),
                failure.output(),
                "",
                null));
      }
    }
    return "\n\n\n";
  }

  @Override
  public Path cwd() {
    return cwd;
  }

  @Override
  public RunningProcess spawn(List<String> command, Path workingDirectory) {
    Call call = new Call(List.copyOf(command), workingDirectory, new LinkedHashMap<>(environment.get()), true);
    calls.add(call);
    listeners.forEach(l -> l.accept(call));
    int exitCode = spawnExitCode;
    boolean alive = spawnAlive;
    return new RunningProcess() {
      private boolean running = alive;

      @Override
      public int waitFor() {
        running = false;
        return exitCode;
      }

      @Override
      public boolean isAlive() {
        return running;
      }

      @Override
      public void destroy() {
        running = false;
      }
    };
  }
}
