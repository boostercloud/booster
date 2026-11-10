package com.boostercloud.rocket.webhook.e2e.app;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

/** Registered by the {@code upload} webhook for every file of the form. */
@Event
public record DocumentUploaded(UUID documentId, String title, String fileName, String mimeType, int size,
    String sha256) {
  public UUID entityID() {
    return documentId;
  }
}
