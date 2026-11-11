package com.boostercloud.rocket.webhook.types;

import java.util.List;

/**
 * Port of {@code WebhookMultipartForm} ({@code webhook-multipart-form.ts}): the parsed parts of a
 * {@code multipart/form-data} request, as {@code WebhookEvent.multipart}. The fields are in the order they appear in
 * the body, the files in the order they end; the interleaving of fields and files is not kept. The components follow
 * the object the TypeScript parser builds ({@code { fields, files }}).
 *
 * @param fields the non-file parts
 * @param files the file parts, fully buffered
 */
public record WebhookMultipartForm(List<WebhookParsedField> fields, List<WebhookParsedFile> files) {

  /** Canonical constructor: copies the lists. */
  public WebhookMultipartForm {
    fields = fields == null ? List.of() : List.copyOf(fields);
    files = files == null ? List.of() : List.copyOf(files);
  }
}
