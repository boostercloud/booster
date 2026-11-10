package com.boostercloud.rocket.fileuploads.e2e.app;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.rocket.fileuploads.core.FileHandler;
import java.util.Optional;

/** The README's {@code FileUploadGet} command. */
@Command(authorize = All.class)
public record FileUploadGet(String directory, String fileName, Optional<String> storageName) {
  /**
   * @param command the command
   * @param register ignored
   * @return the presigned GET URL
   */
  public static String handle(FileUploadGet command, Register register) {
    return new FileHandler(Booster.config(), command.storageName().orElse(null))
        .presignedGet(command.directory(), command.fileName());
  }
}
