package com.boostercloud.rocket.fileuploads.e2e.app;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.rocket.fileuploads.core.FileHandler;
import com.boostercloud.rocket.fileuploads.types.ListItem;
import java.util.List;
import java.util.Optional;

/** The README's {@code FileUploadList} command. */
@Command(authorize = All.class)
public record FileUploadList(String directory, Optional<String> storageName) {
  /**
   * @param command the command
   * @param register ignored
   * @return the files
   */
  public static List<ListItem> handle(FileUploadList command, Register register) {
    return new FileHandler(Booster.config(), command.storageName().orElse(null)).list(command.directory());
  }
}
