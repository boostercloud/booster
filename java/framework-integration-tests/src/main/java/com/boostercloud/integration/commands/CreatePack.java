package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.events.PackCreated;
import com.boostercloud.integration.roles.Admin;
import com.boostercloud.integration.roles.UserWithEmail;
import java.util.List;

/**
 * Creates a pack of products ({@code src/commands/create-pack.ts}); the products' read models get the pack through
 * the array join key {@code Pack.products}.
 *
 * @param name the pack name
 * @param products the products in it
 * @param packID the pack id ({@code UUID.generate()} when missing)
 */
@Command(authorize = {Admin.class, UserWithEmail.class})
public record CreatePack(String name, List<UUID> products, @Nullable UUID packID) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(CreatePack command, Register register) {
    UUID packID = command.packID() != null ? command.packID() : UUID.generate();
    register.events(new PackCreated(packID, command.name(), command.products()));
  }
}
