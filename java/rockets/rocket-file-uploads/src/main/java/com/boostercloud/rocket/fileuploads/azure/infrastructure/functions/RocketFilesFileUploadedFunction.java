package com.boostercloud.rocket.fileuploads.azure.infrastructure.functions;

import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.provider.azure.infrastructure.helper.FunctionAppLayout;
import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Port of {@code functions/rocket-files-file-uploaded-function.ts}: the blob trigger function of one user
 * configuration. TypeScript generates the Node v4 registration
 *
 * <pre>
 * app.storageBlob('fileupload_&lt;storageName&gt;', {
 *   path: '&lt;containerName&gt;/{name}',
 *   connection: '&lt;storageName&gt;',
 *   handler: async (blob, context) =&gt; boosterRocketDispatcher({ blob, context })
 * })
 * </pre>
 *
 * and Java the equivalent {@code function.json} for the Java worker: function {@code fileupload_<storageName>}, binding
 * {@code blobTrigger} {@code blob} (binary) on {@code <containerName>/{name}} with the identity-based connection
 * {@code <storageName>} (resolved through the app setting {@code <storageName>__serviceUri}), entry point
 * {@link BoosterAzureFunctions#ROCKET_BLOB_ENTRY_POINT}, which calls {@code boosterRocketDispatcher({ blob, context })}.
 * The names are validated as in TypeScript (there to keep them out of the generated JavaScript; here so the function
 * names and paths stay the same).
 */
public final class RocketFilesFileUploadedFunction {

  /** {@code VALID_NAME_PATTERN} ({@code /^[a-z0-9_-]+$/}). */
  public static final String VALID_NAME_PATTERN = "/^[a-z0-9_-]+$/";
  /** The prefix of the function names. */
  public static final String FUNCTION_NAME_PREFIX = "fileupload_";
  /** The trigger binding name. */
  public static final String BLOB_BINDING = "blob";

  private static final Pattern VALID_NAME = Pattern.compile("^[a-z0-9_-]+\\z");

  private RocketFilesFileUploadedFunction() {}

  /**
   * {@code generateFunctionsCode(containerName, storageName)} for the Java worker.
   *
   * @param config the application configuration (for the application jar of {@code scriptFile})
   * @param containerName the container
   * @param storageName the storage account
   * @return the function definition
   * @throws IllegalArgumentException {@code Invalid containerName: "<value>". Must match /^[a-z0-9_-]+$/.} (checked
   *     first) or {@code Invalid storageName: ...}
   */
  public static FunctionDefinition generateFunctionDefinition(BoosterConfig config, String containerName,
      String storageName) {
    validateName(containerName, "containerName");
    validateName(storageName, "storageName");
    String functionName = FUNCTION_NAME_PREFIX + jsString(storageName);
    return new FunctionDefinition(functionName, new FunctionConfig(
        List.of(Binding.blobTrigger(BLOB_BINDING, jsString(containerName) + "/{name}", jsString(storageName))),
        FunctionAppLayout.scriptFile(config), BoosterAzureFunctions.ROCKET_BLOB_ENTRY_POINT));
  }

  /**
   * {@code validateName(value, label)}: {@code RegExp.test} coerces the value to a string, so a missing value is
   * tested (and accepted) as {@code "undefined"}, as in TypeScript.
   *
   * @param value the name
   * @param label {@code containerName} or {@code storageName}
   */
  static void validateName(String value, String label) {
    if (!VALID_NAME.matcher(jsString(value)).find()) {
      throw new IllegalArgumentException(
          "Invalid " + label + ": \"" + jsString(value) + "\". Must match " + VALID_NAME_PATTERN + ".");
    }
  }

  private static String jsString(String value) {
    return value == null ? "undefined" : value;
  }
}
