package com.boostercloud.rocket.webhook.e2e.app;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;

/** A file received by the {@code upload} webhook. */
@Entity
public record Document(UUID id, String title, String fileName, String mimeType, int size, String sha256) {

  @Reduces(DocumentUploaded.class)
  public static Document uploaded(DocumentUploaded event, Document current) {
    return new Document(event.documentId(), event.title(), event.fileName(), event.mimeType(), event.size(),
        event.sha256());
  }
}
