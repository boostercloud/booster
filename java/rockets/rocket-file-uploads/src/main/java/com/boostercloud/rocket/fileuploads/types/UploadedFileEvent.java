package com.boostercloud.rocket.fileuploads.types;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.provider.azure.infrastructure.helper.JsonText;
import com.boostercloud.framework.types.concepts.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The event stored for every upload ({@code UploadedFileEvent} of {@code uploaded-file-event.ts}), reduced into
 * {@link UploadedFileEntity}. The stored value is {@code { metadata }}; core binds it back to this record before
 * reducing, so {@code metadata} is the raw JSON value (maps, lists, strings...).
 *
 * <p>Its classes live outside the application's scanned packages, so {@link RocketFilesConcepts} registers them when
 * the rocket is configured (TypeScript registers them as a side effect of importing the package).
 *
 * @param metadata the provider metadata of the upload
 */
@Event
public record UploadedFileEvent(Object metadata) implements RocketFilesBlobUploaded {

  /**
   * {@code entityID()}: the MD5 (32 lowercase hex characters) of {@code JSON.stringify(this.metadata)}, so the same
   * metadata always gives the same id. Note that the rocket stores the event under a random entity id; this value is
   * the {@code id} of the reduced {@link UploadedFileEntity}.
   *
   * @return the MD5 of the metadata JSON
   */
  @Override
  public UUID entityID() {
    return UUID.of(md5Hex(JsonText.stringify(metadata)));
  }

  /**
   * @param text a text
   * @return the MD5 of its UTF-8 bytes, lowercase hex ({@code crypto.createHash('md5').update(text).digest('hex')})
   */
  static String md5Hex(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 is not available", e);
    }
  }
}
