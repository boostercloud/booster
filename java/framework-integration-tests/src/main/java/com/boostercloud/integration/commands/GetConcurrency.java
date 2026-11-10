package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.List;
import java.util.Map;

/**
 * Reads the raw stored documents of a read model, {@code boosterMetadata} included, straight from the provider
 * ({@code src/commands/get-concurrency.ts}); the concurrency suites check the stored versions with it.
 *
 * @param id the read model id
 * @param readModelName the read model type
 */
@Command(authorize = All.class)
public record GetConcurrency(UUID id, String readModelName) {

  /**
   * @param command the command
   * @param register the register
   * @return {@code config.provider.readModels.fetch(config, readModelName, id)}
   */
  public static List<Map<String, Object>> handle(GetConcurrency command, Register register) {
    BoosterConfig config = Booster.config();
    return config.provider().readModels().fetch(config, command.readModelName(), command.id(), null);
  }
}
