package com.boostercloud.rocket.fileuploads.e2e.app;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.rocket.fileuploads.core.FileHandler;
import java.util.Optional;

/** The documentation's {@code DeleteFile} command (a Java addition for the local and Azure providers). */
@Command(authorize = All.class)
public record DeleteFile(String directory, String fileName, Optional<String> storageName) {
  /**
   * @param command the command
   * @param register ignored
   * @return whether the file was deleted
   */
  public static boolean handle(DeleteFile command, Register register) {
    return new FileHandler(Booster.config(), command.storageName().orElse(null))
        .deleteFile(command.directory(), command.fileName());
  }
}
