package com.boostercloud.framework.core.integration.app.readmodels;

import com.boostercloud.framework.types.concepts.ReadModelBeforeFunction;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;

/**
 * Before hook of {@link CartReadModel}: carts whose id starts with {@code secret-} are only visible to admins, and
 * {@code alias-<id>} is read as {@code <id>}.
 */
public class CartReadModelGuard implements ReadModelBeforeFunction {
  @Override
  public ReadModelRequestEnvelope apply(ReadModelRequestEnvelope request, UserEnvelope currentUser) {
    if (request.key() == null || request.key().id() == null) {
      return request;
    }
    String id = request.key().id().value();
    if (id.startsWith("secret-") && (currentUser == null || !currentUser.hasRole("Admin"))) {
      throw new NotAuthorizedError("Secret carts are only visible to admins");
    }
    if (id.startsWith("alias-")) {
      return request.toBuilder()
          .key(new ReadModelRequestEnvelope.Key(UUID.of(id.substring("alias-".length())), request.key().sequenceKey()))
          .build();
    }
    return request;
  }
}
