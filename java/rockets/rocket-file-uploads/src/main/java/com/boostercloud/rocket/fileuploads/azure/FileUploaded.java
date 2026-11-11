package com.boostercloud.rocket.fileuploads.azure;

import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.types.ConsoleLogger;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.rocket.fileuploads.types.NodePath;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import com.boostercloud.rocket.fileuploads.types.Utils;
import java.util.Objects;

/**
 * Port of {@code file-uploaded.ts} (Azure): the metadata of a blob trigger invocation
 * ({@code boosterRocketDispatcher({ blob, context })}, built by
 * {@code BoosterAzureFunctions.rocketBlob}) and the check that the blob belongs to a user configuration.
 */
public final class FileUploaded {

  private static volatile Logger logger = ConsoleLogger.INSTANCE;

  private FileUploaded() {}

  /**
   * {@code getMetadataFromRequest(request)}: {@code request.context.triggerMetadata} ({@code BlobTriggerMetadata},
   * with at least {@code blobTrigger = <container>/<blob name>}), stored as the event metadata. The request is a map
   * or a record; the context an {@code AzureInvocationContext}, a map or a record.
   *
   * @param request the rocket function request
   * @return the trigger metadata
   * @throws IllegalStateException {@code InvocationContext.triggerMetadata is missing for the blob upload request}
   */
  public static Object getMetadataFromRequest(Object request) {
    Object context = JsValues.get(request, "context");
    Object triggerMetadata = context instanceof AzureInvocationContext invocationContext
        ? invocationContext.triggerMetadata()
        : JsValues.get(context, "triggerMetadata");
    if (triggerMetadata == null) {
      throw new IllegalStateException("InvocationContext.triggerMetadata is missing for the blob upload request");
    }
    return triggerMetadata;
  }

  /**
   * {@code validateMetadata(configuration, metadata)}: the blob path must start with {@code <containerName>/} and its
   * directory (the path inside the container without the file name; {@code ""} at the container root) must match one
   * of the directory patterns. Ignored blobs are logged at info level, as TypeScript's {@code console.info}.
   *
   * @param configuration the user configuration
   * @param metadata the trigger metadata
   * @return whether an event is stored for that configuration
   * @throws IllegalArgumentException {@code Cannot read properties of undefined (reading 'startsWith')} without
   *     {@code blobTrigger}
   */
  public static boolean validateMetadata(RocketFilesUserConfiguration configuration, Object metadata) {
    Object value = JsValues.get(metadata, "blobTrigger");
    if (value == null) {
      throw new IllegalArgumentException("Cannot read properties of undefined (reading 'startsWith')");
    }
    if (!(value instanceof String blobTrigger)) {
      throw new IllegalArgumentException("blobTrigger.startsWith is not a function");
    }
    String containerPrefix = configuration.containerName() + "/";
    if (!blobTrigger.startsWith(containerPrefix)) {
      logger.info("Ignoring blob trigger " + blobTrigger + " - container name does not match "
          + configuration.containerName());
      return false;
    }
    String sourceWithoutContainer = blobTrigger.substring(containerPrefix.length());
    String sourceDirectoryPath = NodePath.parseDir(sourceWithoutContainer);
    boolean directoryFound = Utils.isValidDirectory(sourceDirectoryPath, configuration.directories());
    if (!directoryFound) {
      logger.info("Ignoring blob trigger " + blobTrigger + " - directory does not match configured directories");
      return false;
    }
    return true;
  }

  /**
   * Replaces the sink of the "Ignoring blob trigger" lines ({@code console.info} by default).
   *
   * @param newLogger the sink
   */
  public static void useLogger(Logger newLogger) {
    logger = Objects.requireNonNull(newLogger, "logger");
  }

  /** Goes back to the console. */
  public static void resetLogger() {
    logger = ConsoleLogger.INSTANCE;
  }
}
