package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.integration.common.Nullable;
import java.util.Map;

/**
 * Lists the ids of the entities of a type, one page at a time ({@code src/commands/entities-ids-finder.ts}).
 *
 * @param entityName the entity type
 * @param limit the page size
 * @param afterCursor the cursor of the previous page
 */
@Command(authorize = All.class)
public record EntitiesIdsFinder(String entityName, double limit, @Nullable Map<String, String> afterCursor) {

  /**
   * @param command the command
   * @param register the register
   * @return {@code Booster.entitiesIDs(entityName, limit, afterCursor)}
   */
  public static PaginatedEntitiesIdsResult handle(EntitiesIdsFinder command, Register register) {
    return Booster.entitiesIDs(command.entityName(), (int) command.limit(), command.afterCursor());
  }
}
