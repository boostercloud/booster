package com.boostercloud.rocket.fileuploads.e2e.app;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.rocket.fileuploads.core.FileHandler;
import java.util.Optional;

/** The README's {@code FileUploadPut} command. */
@Command(authorize = All.class)
public record FileUploadPut(String directory, String fileName, Optional<String> storageName) {
  /**
   * @param command the command
   * @param register ignored
   * @return the presigned PUT URL
   */
  public static String handle(FileUploadPut command, Register register) {
    return new FileHandler(Booster.config(), command.storageName().orElse(null))
        .presignedPut(command.directory(), command.fileName());
  }
}
