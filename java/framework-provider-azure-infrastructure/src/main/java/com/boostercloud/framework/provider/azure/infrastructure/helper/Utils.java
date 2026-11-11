package com.boostercloud.framework.provider.azure.infrastructure.helper;

import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Port of {@code helper/utils.ts}: the naming rules of every Azure resource and Terraform construct, the region, the
 * template renderer and {@code waitForIt}.
 *
 * <p>The naming functions are a compatibility contract: changing any result renames (recreates) resources of existing
 * deployments.
 *
 * <p>Not ported: {@code readProjectConfig} (Java receives the configuration explicitly instead of re-requiring
 * {@code dist/index.js}); the Azure SDK client factories and {@code azureCredentials} are the constructors of
 * {@link com.boostercloud.framework.provider.azure.infrastructure.azure.ClientSecretCredential} and the management
 * clients of that package.
 */
public final class Utils {

  /** {@code MAX_TERRAFORM_SIZE_NAME}. */
  public static final int MAX_TERRAFORM_SIZE_NAME = 24;

  /** {@code MAX_RESOURCE_GROUP_NAME_SIZE}. */
  public static final int MAX_RESOURCE_GROUP_NAME_SIZE = 20;

  /** The error {@code getDeployRegion} throws when {@code REGION} is unset or empty. */
  public static final String REGION_NOT_LOADED =
      "REGION was not properly loaded and is required to run the deploy process. Check that you've set it in REGION"
          + " environment variable.";

  private Utils() {}

  private static String terraformCleanText(String text) {
    return text.toLowerCase(Locale.ROOT).replaceAll("[-_]", "");
  }

  /**
   * {@code toTerraformName(name, '')}.
   *
   * @param name the name
   * @return the Terraform-safe name
   */
  public static String toTerraformName(String name) {
    return toTerraformName(name, "");
  }

  /**
   * {@code toTerraformName(name, suffix)}: lower-cases and strips {@code -}/{@code _} from both parts, then fits them in
   * 24 characters: the concatenation when it fits; the first 23 characters of it when the name is not longer than the
   * suffix; otherwise the last {@code 24 - suffix} characters of the name followed by the whole suffix. A leading ASCII
   * digit is replaced by {@code B}.
   *
   * @param name the name
   * @param suffix the suffix
   * @return the Terraform-safe name
   * @throws IllegalArgumentException {@code Error. Suffix could not be greater than 24} when the cleaned suffix is longer
   */
  public static String toTerraformName(String name, String suffix) {
    String cleanSuffix = terraformCleanText(suffix);
    if (cleanSuffix.length() > MAX_TERRAFORM_SIZE_NAME) {
      throw new IllegalArgumentException("Error. Suffix could not be greater than " + MAX_TERRAFORM_SIZE_NAME);
    }
    String cleanName = terraformCleanText(name);
    int totalLength = cleanName.length() + cleanSuffix.length();
    if (totalLength <= MAX_TERRAFORM_SIZE_NAME) {
      return startsWithLetter(cleanName + cleanSuffix);
    }
    if (cleanName.length() <= cleanSuffix.length()) {
      String newName = substr(cleanName + cleanSuffix, 0, MAX_TERRAFORM_SIZE_NAME - 1);
      return startsWithLetter(newName);
    }
    int extraLength = totalLength - MAX_TERRAFORM_SIZE_NAME;
    String fixedCleanName = cleanName.substring(extraLength);
    return startsWithLetter(fixedCleanName + cleanSuffix);
  }

  private static String startsWithLetter(String text) {
    return !text.isEmpty() && text.charAt(0) >= '0' && text.charAt(0) <= '9' ? "B" + text.substring(1) : text;
  }

  /**
   * {@code toAzureName(name)} with the default maximum size (24).
   *
   * @param name the name
   * @return the name without {@code -}/{@code _}, at most 24 characters (case kept)
   */
  public static String toAzureName(String name) {
    return toAzureName(name, 24);
  }

  /**
   * {@code toAzureName(name, maxSize)}.
   *
   * @param name the name
   * @param maxSize the maximum length
   * @return the name without {@code -}/{@code _}, at most {@code maxSize} characters (case kept)
   */
  public static String toAzureName(String name, int maxSize) {
    return substr(name.replaceAll("[-_]", ""), 0, maxSize);
  }

  /**
   * {@code getDeployRegion()}.
   *
   * @return the {@code REGION} environment variable
   * @throws IllegalStateException {@link #REGION_NOT_LOADED} when it is unset or empty
   */
  public static String getDeployRegion() {
    String region = EnvironmentVariables.get(Params.REGION).orElse("");
    if (region.isEmpty()) {
      throw new IllegalStateException(REGION_NOT_LOADED);
    }
    return region;
  }

  /**
   * The region as it appears in host names: {@code (process.env['REGION'] ?? '').toLowerCase().replace(/ /g, '')}.
   *
   * @return e.g. {@code eastus} for {@code East US}
   */
  public static String urlRegion() {
    return EnvironmentVariables.get(Params.REGION).orElse("").toLowerCase(Locale.ROOT).replace(" ", "");
  }

  /**
   * {@code buildAppPrefix(config)}: the cleaned {@code appName + environmentName}, at most 24 characters.
   *
   * @param config the configuration
   * @return the prefix of the Terraform construct ids
   */
  public static String buildAppPrefix(BoosterConfig config) {
    return buildAppPrefix(config.appName, config.environmentName);
  }

  /**
   * @param appName the application name
   * @param environmentName the environment name
   * @return the app prefix
   */
  public static String buildAppPrefix(String appName, String environmentName) {
    return substr(terraformCleanText(appName + environmentName), 0, MAX_TERRAFORM_SIZE_NAME);
  }

  /**
   * {@code createResourceGroupName(appName, environmentName)}.
   *
   * @param appName the application name
   * @param environmentName the environment name
   * @return {@code toAzureName(appName + environmentName, 20) + 'rg'}
   */
  public static String createResourceGroupName(String appName, String environmentName) {
    return toAzureName(appName + environmentName, MAX_RESOURCE_GROUP_NAME_SIZE) + "rg";
  }

  /**
   * @param resourceGroupName the resource group name
   * @return the main function app name ({@code <rg>fpr})
   */
  public static String createFunctionResourceGroupName(String resourceGroupName) {
    return resourceGroupName + "fpr";
  }

  /**
   * @param resourceGroupName the resource group name
   * @return the event stream consumer function app name ({@code <rg>fcs})
   */
  public static String createStreamFunctionResourceGroupName(String resourceGroupName) {
    return resourceGroupName + "fcs";
  }

  /**
   * @param resourceGroupName the resource group name
   * @return the domain name label of the public IP ({@code <rg>apis})
   */
  public static String createDomainNameLabel(String resourceGroupName) {
    return resourceGroupName + "apis";
  }

  /**
   * {@code renderToFile(config)([filePath, template])}: renders a Mustache template with the configuration as view
   * into {@code <projectRoot>/<filePath...>} (parent directories created), unless the file already exists.
   *
   * @param config the configuration (the template view)
   * @param projectRoot the project root ({@code process.cwd()} in TypeScript)
   * @param filePath the path segments of the file
   * @param template the Mustache template
   * @throws UncheckedIOException when the file cannot be written
   */
  public static void renderToFile(BoosterConfig config, Path projectRoot, List<String> filePath, String template) {
    Path renderPath = projectRoot;
    for (String segment : filePath) {
      renderPath = renderPath.resolve(segment);
    }
    if (Files.exists(renderPath)) {
      return;
    }
    String rendered = renderTemplate(template, config);
    try {
      if (renderPath.getParent() != null) {
        Files.createDirectories(renderPath.getParent());
      }
      Files.writeString(renderPath, rendered, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + renderPath + ": " + e.getMessage(), e);
    }
  }

  /**
   * {@code Mustache.render(template, view)}.
   *
   * @param template the Mustache template
   * @param view the view (a map or an object whose fields/methods the tags name)
   * @return the rendered text
   */
  public static String renderTemplate(String template, Object view) {
    Mustache mustache = new DefaultMustacheFactory().compile(new StringReader(template), "template");
    StringWriter writer = new StringWriter();
    mustache.execute(writer, view);
    return writer.toString();
  }

  /**
   * {@code waitForIt(tryFunction, checkResult, errorMessage)} with the default timeout (180 s) and interval (1 s).
   *
   * @param <T> the result type
   * @param tryFunction produces a result
   * @param checkResult whether the result is the expected one
   * @param errorMessage the message of the error thrown on timeout
   * @return the first accepted result
   */
  public static <T> T waitForIt(Supplier<T> tryFunction, Predicate<T> checkResult, String errorMessage) {
    return waitForIt(tryFunction, checkResult, errorMessage, 180000, 1000);
  }

  /**
   * {@code waitForIt(tryFunction, checkResult, errorMessage, timeoutMs, tryEveryMs)}.
   *
   * @param <T> the result type
   * @param tryFunction produces a result (its exceptions propagate immediately)
   * @param checkResult whether the result is the expected one
   * @param errorMessage the message of the error thrown on timeout
   * @param timeoutMs the timeout
   * @param tryEveryMs the retry interval
   * @return the first accepted result
   */
  public static <T> T waitForIt(
      Supplier<T> tryFunction, Predicate<T> checkResult, String errorMessage, long timeoutMs, long tryEveryMs) {
    return waitForIt(tryFunction, checkResult, errorMessage, timeoutMs, tryEveryMs, System::currentTimeMillis,
        Retrier.Sleeper.THREAD_SLEEP);
  }

  /**
   * {@code waitForIt} with an injectable clock and sleeper. Between attempts it waits
   * {@code (timeoutMs - elapsed) % tryEveryMs} (possibly 0), as TypeScript does; it prints {@code [waitForIt] start},
   * {@code .} per attempt and {@code [waitForIt] match} ({@code console.debug}).
   *
   * @param <T> the result type
   * @param tryFunction produces a result
   * @param checkResult whether the result is the expected one
   * @param errorMessage the message of the error thrown on timeout
   * @param timeoutMs the timeout
   * @param tryEveryMs the retry interval
   * @param nowMillis the clock
   * @param sleeper waits between attempts
   * @return the first accepted result
   * @throws IllegalStateException with {@code errorMessage} when the timeout elapses
   */
  public static <T> T waitForIt(
      Supplier<T> tryFunction,
      Predicate<T> checkResult,
      String errorMessage,
      long timeoutMs,
      long tryEveryMs,
      LongSupplier nowMillis,
      Retrier.Sleeper sleeper) {
    System.out.println("[waitForIt] start");
    long start = nowMillis.getAsLong();
    while (true) {
      System.out.println(".");
      T result = tryFunction.get();
      if (checkResult.test(result)) {
        System.out.println("[waitForIt] match");
        return result;
      }
      long elapsed = nowMillis.getAsLong() - start;
      if (elapsed > timeoutMs) {
        throw new IllegalStateException(errorMessage);
      }
      long nextExecutionDelay = (timeoutMs - elapsed) % tryEveryMs;
      sleeper.sleep(nextExecutionDelay);
    }
  }

  /**
   * JavaScript's {@code text.substr(start, length)} for non-negative arguments.
   *
   * @param text the text
   * @param start the start index
   * @param length the maximum length
   * @return the substring
   */
  static String substr(String text, int start, int length) {
    if (start >= text.length()) {
      return "";
    }
    return text.substring(start, Math.min(text.length(), start + length));
  }
}
