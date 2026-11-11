package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.ConcurrencyPersisted;

/**
 * Stores a {@code Concurrency} entity, projected into {@code ConcurrencyReadModel} once and into
 * {@code OtherConcurrencyReadModel} twice ({@code src/commands/add-concurrency.ts}).
 *
 * @param id the entity
 * @param otherId the second join key
 */
@Command(authorize = All.class)
public record AddConcurrency(UUID id, UUID otherId) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(AddConcurrency command, Register register) {
    register.events(new ConcurrencyPersisted(command.id(), command.otherId()));
  }

  /**
   * The unused {@code export interface ProjectionDetails { methodName: string; joinKey: keyof AddConcurrency }} of the
   * TypeScript file.
   *
   * @param methodName a projection method
   * @param joinKey a property of {@link AddConcurrency}
   */
  public record ProjectionDetails(String methodName, String joinKey) {}
}
