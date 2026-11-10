package com.boostercloud.framework.provider.azure.infrastructure.support;

import com.boostercloud.framework.common.RunCommandError;
import com.boostercloud.framework.provider.azure.infrastructure.process.Command;
import com.boostercloud.framework.provider.azure.infrastructure.process.CommandRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Records the commands and answers them with a script (default: empty output). */
public final class RecordingCommandRunner implements CommandRunner {

  private final List<Command> commands = new ArrayList<>();
  private Function<Command, String> answer = command -> "";

  /**
   * @param answer produces the output of a command (or throws)
   * @return this runner
   */
  public RecordingCommandRunner answering(Function<Command, String> answer) {
    this.answer = answer;
    return this;
  }

  /**
   * @param commandLinePrefix fails the commands whose command line starts with it
   * @param stderr the standard error
   * @return this runner
   */
  public RecordingCommandRunner failing(String commandLinePrefix, String stderr) {
    Function<Command, String> previous = answer;
    this.answer = command -> {
      if (command.commandLine().startsWith(commandLinePrefix)) {
        throw new RunCommandError(1, "", stderr);
      }
      return previous.apply(command);
    };
    return this;
  }

  @Override
  public synchronized String run(Command command) {
    commands.add(command);
    return answer.apply(command);
  }

  /**
   * @return the recorded commands
   */
  public synchronized List<Command> commands() {
    return List.copyOf(commands);
  }
}
