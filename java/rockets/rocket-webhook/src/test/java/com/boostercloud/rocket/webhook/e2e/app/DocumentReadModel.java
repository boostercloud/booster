package com.boostercloud.rocket.webhook.e2e.app;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;

/** The uploaded documents, as GraphQL shows them. */
@ReadModel(authorize = All.class)
public record DocumentReadModel(UUID id, String title, String fileName, String mimeType, int size, String sha256) {

  @Projects(entity = Document.class, joinKey = "id")
  public static ProjectionResult<DocumentReadModel> projectDocument(Document document, DocumentReadModel current) {
    return ProjectionResult.of(new DocumentReadModel(document.id(), document.title(), document.fileName(),
        document.mimeType(), document.size(), document.sha256()));
  }
}
