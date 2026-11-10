package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Port of {@code WebhookHandlerFileReturnType}: a file download. The body (bytes or a string) is sent as is with
 * {@code Content-Type: <file.mimeType>} and {@code Content-Disposition: attachment; filename=<file.name>} (the name is
 * neither quoted nor encoded, as in TypeScript).
 *
 * @param body the file content
 * @param headers extra headers, or {@code null}
 * @param file the file name and type; a {@code null} file fails the request (TypeScript reads {@code file.mimeType})
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record WebhookHandlerFileReturnType(Object body, Map<String, Object> headers, File file)
    implements WebhookHandlerReturnType {

  /**
   * The {@code file} member.
   *
   * @param name the file name the client should save the content as
   * @param mimeType the content type ({@code ''} when {@code null} or empty)
   */
  public record File(String name, String mimeType) {}

  /** Canonical constructor: copies the headers. */
  public WebhookHandlerFileReturnType {
    headers = WebhookHandlerReturnType.copyHeaders(headers);
  }

  @Override
  public WebhookResponseType responseType() {
    return WebhookResponseType.FILE;
  }

  /**
   * @param newHeaders extra headers
   * @return a copy with those headers
   */
  public WebhookHandlerFileReturnType withHeaders(Map<String, ?> newHeaders) {
    return new WebhookHandlerFileReturnType(body, WebhookHandlerReturnType.copyHeaders(newHeaders), file);
  }
}
