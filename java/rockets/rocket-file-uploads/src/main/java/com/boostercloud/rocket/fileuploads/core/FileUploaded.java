package com.boostercloud.rocket.fileuploads.core;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.ConsoleLogger;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.rocket.fileuploads.types.RocketFileUploadedLibrary;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import com.boostercloud.rocket.fileuploads.types.UploadedFileEntity;
import com.boostercloud.rocket.fileuploads.types.UploadedFileEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code file-uploaded.ts}: the body of the rocket function {@code rocket-files} for one user configuration.
 * The provider library extracts the upload metadata from the raw request and decides whether it belongs to the
 * configuration; when it does, an {@code UploadedFileEvent} envelope is stored with the application's provider (which
 * dispatches it, so {@code UploadedFileEntity} is reduced and projected as for any other event).
 */
public final class FileUploaded {

  /** The prefix of the line logged when the event cannot be stored. */
  public static final String STORE_ERROR_MESSAGE = "[ROCKET#files] An error occurred while performing a PutItem "
      + "operation: ";

  private FileUploaded() {}

  /**
   * {@code fileUploaded(boosterConfig, request, rocketFilesUserConfiguration, provider)}.
   *
   * @param boosterConfig the application configuration
   * @param request the raw rocket function request
   * @param rocketFilesUserConfiguration the user configuration
   * @param provider the provider runtime library
   * @throws RuntimeException what {@code getMetadataFromRequest} / {@code validateMetadata} throw (store failures are
   *     only logged)
   */
  public static void fileUploaded(BoosterConfig boosterConfig, Object request,
      RocketFilesUserConfiguration rocketFilesUserConfiguration, RocketFileUploadedLibrary provider) {
    Object metadata = provider.getMetadataFromRequest(request);
    if (provider.validateMetadata(rocketFilesUserConfiguration, metadata)) {
      processEvent(boosterConfig, metadata);
    }
  }

  private static void processEvent(BoosterConfig boosterConfig, Object metadata) {
    try {
      NonPersistedEventEnvelope envelope = toEventEnvelope(metadata);
      boosterConfig.provider().events().store(List.of(envelope), boosterConfig);
    } catch (RuntimeException e) {
      Logger sink = boosterConfig.logger != null ? boosterConfig.logger : ConsoleLogger.INSTANCE;
      sink.info(STORE_ERROR_MESSAGE, e);
    }
  }

  /**
   * {@code toEventEnvelop(metadata)}: a domain event {@code UploadedFileEvent} of {@code UploadedFileEntity}, version
   * 1, whose entity id and request id are one fresh random id, with value {@code { metadata }}. (TypeScript also sets
   * {@code createdAt}, which every provider overwrites when it stores the event.)
   *
   * @param metadata the upload metadata
   * @return the envelope
   */
  static NonPersistedEventEnvelope toEventEnvelope(Object metadata) {
    UUID id = UUID.generate();
    Map<String, Object> value = new LinkedHashMap<>();
    if (metadata != null) {
      value.put("metadata", metadata);
    }
    return NonPersistedEventEnvelope.builder()
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(id)
        .requestID(id)
        .typeName(UploadedFileEvent.class.getSimpleName())
        .entityTypeName(UploadedFileEntity.class.getSimpleName())
        .value(value)
        .build();
  }
}
