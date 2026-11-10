package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Port of {@code WebhookParsedField} ({@code webhook-multipart-form.ts}): a non-file part of a
 * {@code multipart/form-data} request.
 *
 * @param name the {@code name} parameter of the part's {@code Content-Disposition}, or {@code null} when absent
 * @param value the part's content decoded with its charset ({@code utf-8} unless its {@code Content-Type} says
 *     otherwise), or {@code null} when the charset is unknown
 * @param nameTruncated always {@code false} for multipart bodies (as Busboy)
 * @param valueTruncated whether the value was cut at Busboy's 1 MiB field size limit
 * @param encoding the part's {@code Content-Transfer-Encoding}, lower-cased ({@code 7bit} by default)
 * @param mimeType the part's {@code Content-Type} type and subtype ({@code text/plain} by default)
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record WebhookParsedField(
    String name, String value, boolean nameTruncated, boolean valueTruncated, String encoding, String mimeType) {}
