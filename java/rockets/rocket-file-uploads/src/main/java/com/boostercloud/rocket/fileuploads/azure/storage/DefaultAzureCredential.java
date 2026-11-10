package com.boostercloud.rocket.fileuploads.azure.storage;

import com.boostercloud.framework.provider.azure.infrastructure.azure.AuthenticationError;
import com.boostercloud.framework.provider.azure.infrastructure.azure.ClientSecretCredential;
import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.azure.TokenCredential;
import com.boostercloud.framework.provider.azure.http.UriEncoding;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The subset of {@code @azure/identity}'s {@code DefaultAzureCredential} the rocket needs to call Blob Storage with
 * Azure AD: the first available of
 *
 * <ol>
 *   <li><b>environment</b>: a service principal from {@code AZURE_TENANT_ID}, {@code AZURE_CLIENT_ID} and
 *       {@code AZURE_CLIENT_SECRET} (client credentials grant);</li>
 *   <li><b>managed identity</b>: the App Service / Azure Functions endpoint ({@code IDENTITY_ENDPOINT} and
 *       {@code IDENTITY_HEADER}, API version {@code 2019-08-01}), otherwise the instance metadata service
 *       ({@code http://169.254.169.254}, API version {@code 2018-02-01}); {@code AZURE_CLIENT_ID} selects a
 *       user-assigned identity;</li>
 *   <li><b>Azure CLI</b>: {@code az account get-access-token --output json --resource <resource>} (developer
 *       machines after {@code az login}).</li>
 * </ol>
 *
 * A credential that does not apply is skipped; one that applies and fails stops the chain with its error. Tokens are
 * cached per scope until two minutes before they expire. Not implemented: workload identity, certificates,
 * username/password, Azure PowerShell and Azure Developer CLI.
 */
public final class DefaultAzureCredential implements TokenCredential {

  /** The App Service managed identity API version. */
  static final String APP_SERVICE_API_VERSION = "2019-08-01";
  /** The instance metadata service API version. */
  static final String IMDS_API_VERSION = "2018-02-01";
  /** The instance metadata service token endpoint. */
  static final String IMDS_ENDPOINT = "http://169.254.169.254/metadata/identity/oauth2/token";

  private static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);
  private static final Duration IMDS_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration CLI_TIMEOUT = Duration.ofSeconds(30);

  /** Runs a command and returns its outcome (the Azure CLI step). */
  @FunctionalInterface
  public interface CommandRunner {
    /**
     * @param command the command and its arguments
     * @return the outcome
     * @throws IOException when the program cannot be started
     */
    CommandResult run(List<String> command) throws IOException;
  }

  /**
   * @param exitCode the exit code
   * @param stdout the standard output
   * @param stderr the standard error
   */
  public record CommandResult(int exitCode, String stdout, String stderr) {}

  /** A step of the chain that does not apply in this environment ({@code CredentialUnavailableError}). */
  static final class CredentialUnavailableError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    CredentialUnavailableError(String message) {
      super(message);
    }
  }

  private record CachedToken(String token, Instant expiresOn) {}

  private final HttpTransport transport;
  private final CommandRunner commandRunner;
  private final Clock clock;
  private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();
  private volatile ClientSecretCredential environmentCredential;
  private volatile String environmentKey;

  /**
   * @param transport sends the token requests
   */
  public DefaultAzureCredential(HttpTransport transport) {
    this(transport, DefaultAzureCredential::runProcess, Clock.systemUTC());
  }

  /**
   * @param transport sends the token requests
   * @param commandRunner runs the Azure CLI
   * @param clock the clock of the token cache
   */
  public DefaultAzureCredential(HttpTransport transport, CommandRunner commandRunner, Clock clock) {
    this.transport = Objects.requireNonNull(transport, "transport");
    this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public String getToken(String scope) {
    CachedToken cached = cache.get(scope);
    if (cached != null && clock.instant().isBefore(cached.expiresOn().minus(REFRESH_MARGIN))) {
      return cached.token();
    }
    List<String> unavailable = new ArrayList<>();
    try {
      return environment(scope);
    } catch (CredentialUnavailableError e) {
      unavailable.add(e.getMessage());
    }
    try {
      return remember(scope, managedIdentity(scope));
    } catch (CredentialUnavailableError e) {
      unavailable.add(e.getMessage());
    }
    try {
      return remember(scope, azureCli(scope));
    } catch (CredentialUnavailableError e) {
      unavailable.add(e.getMessage());
    }
    StringBuilder message = new StringBuilder("ChainedTokenCredential authentication failed.");
    unavailable.forEach(reason -> message.append("\nCredentialUnavailableError: ").append(reason));
    throw new AuthenticationError(0, message.toString(), null);
  }

  private String remember(String scope, CachedToken token) {
    cache.put(scope, token);
    return token.token();
  }

  // --- environment ------------------------------------------------------------------------------------------------

  private String environment(String scope) {
    Optional<String> tenant = EnvironmentVariables.get("AZURE_TENANT_ID").filter(value -> !value.isEmpty());
    Optional<String> client = EnvironmentVariables.get("AZURE_CLIENT_ID").filter(value -> !value.isEmpty());
    Optional<String> secret = EnvironmentVariables.get("AZURE_CLIENT_SECRET").filter(value -> !value.isEmpty());
    if (tenant.isEmpty() || client.isEmpty() || secret.isEmpty()) {
      throw new CredentialUnavailableError("EnvironmentCredential is unavailable. No underlying credential could be "
          + "used. AZURE_TENANT_ID, AZURE_CLIENT_ID and AZURE_CLIENT_SECRET must be set.");
    }
    String key = tenant.get() + "\n" + client.get() + "\n" + secret.get();
    ClientSecretCredential credential = environmentCredential;
    if (credential == null || !key.equals(environmentKey)) {
      credential = new ClientSecretCredential(tenant.get(), client.get(), secret.get(), transport, clock);
      environmentCredential = credential;
      environmentKey = key;
    }
    return credential.getToken(scope);
  }

  // --- managed identity -------------------------------------------------------------------------------------------

  private CachedToken managedIdentity(String scope) {
    String resource = resourceOf(scope);
    Optional<String> clientId = EnvironmentVariables.get("AZURE_CLIENT_ID").filter(value -> !value.isEmpty());
    Optional<String> identityEndpoint = EnvironmentVariables.get("IDENTITY_ENDPOINT").filter(v -> !v.isEmpty());
    Optional<String> identityHeader = EnvironmentVariables.get("IDENTITY_HEADER").filter(v -> !v.isEmpty());
    Map<String, String> headers = new LinkedHashMap<>();
    String url;
    Duration timeout;
    boolean imds = identityEndpoint.isEmpty() || identityHeader.isEmpty();
    if (!imds) {
      url = identityEndpoint.get() + "?api-version=" + APP_SERVICE_API_VERSION + "&resource="
          + UriEncoding.encodeURIComponent(resource)
          + clientId.map(id -> "&client_id=" + UriEncoding.encodeURIComponent(id)).orElse("");
      headers.put("X-IDENTITY-HEADER", identityHeader.get());
      timeout = Duration.ofSeconds(30);
    } else {
      url = IMDS_ENDPOINT + "?api-version=" + IMDS_API_VERSION + "&resource=" + UriEncoding.encodeURIComponent(resource)
          + clientId.map(id -> "&client_id=" + UriEncoding.encodeURIComponent(id)).orElse("");
      headers.put("Metadata", "true");
      timeout = IMDS_TIMEOUT;
    }
    headers.put("Accept", "application/json");
    HttpTransport.Response response;
    try {
      response = transport.send(new HttpTransport.Request("GET", URI.create(url), headers, null, timeout));
    } catch (UncheckedIOException e) {
      if (imds) {
        throw new CredentialUnavailableError("ManagedIdentityCredential: no managed identity endpoint is reachable ("
            + e.getMessage() + ").");
      }
      throw new AuthenticationError(0, "ManagedIdentityCredential authentication failed: " + e.getMessage(), e);
    }
    Map<String, Object> json = parse(response.bodyText());
    if (response.status() == 200 && json.get("access_token") instanceof String token) {
      return new CachedToken(token, expiresOn(json.get("expires_on"), json.get("expires_in")));
    }
    if (imds && (response.status() == 400 || response.status() == 404)) {
      throw new CredentialUnavailableError("ManagedIdentityCredential: the instance metadata service has no identity "
          + "for this resource (status " + response.status() + ").");
    }
    Object description = json.getOrDefault("error_description", json.getOrDefault("message", response.bodyText()));
    throw new AuthenticationError(response.status(), "ManagedIdentityCredential authentication failed: " + description,
        null);
  }

  // --- Azure CLI --------------------------------------------------------------------------------------------------

  private CachedToken azureCli(String scope) {
    List<String> command = List.of(isWindows() ? "az.cmd" : "az", "account", "get-access-token", "--output", "json",
        "--resource", resourceOf(scope));
    CommandResult result;
    try {
      result = commandRunner.run(command);
    } catch (IOException e) {
      throw new CredentialUnavailableError("Azure CLI could not be found. Please visit https://aka.ms/azure-cli for "
          + "installation instructions and then, once installed, authenticate to your Azure account using 'az login'.");
    }
    if (result.exitCode() != 0) {
      String stderr = result.stderr() == null ? "" : result.stderr();
      if (stderr.contains("az login") || stderr.contains("az account set")) {
        throw new CredentialUnavailableError("Please run 'az login' from a command prompt to authenticate before using "
            + "this credential.");
      }
      if (stderr.contains("not recognized") || stderr.contains("not found")) {
        throw new CredentialUnavailableError("Azure CLI could not be found. Please visit https://aka.ms/azure-cli for "
            + "installation instructions and then, once installed, authenticate to your Azure account using 'az "
            + "login'.");
      }
      throw new AuthenticationError(result.exitCode(), "AzureCliCredential authentication failed: " + stderr.strip(),
          null);
    }
    Map<String, Object> json = parse(result.stdout());
    if (!(json.get("accessToken") instanceof String token)) {
      throw new AuthenticationError(0, "AzureCliCredential authentication failed: unexpected output of 'az account "
          + "get-access-token'", null);
    }
    return new CachedToken(token, expiresOn(json.get("expires_on"), null));
  }

  private Instant expiresOn(Object expiresOnSeconds, Object expiresIn) {
    Long seconds = asLong(expiresOnSeconds);
    if (seconds != null) {
      return Instant.ofEpochSecond(seconds);
    }
    Long in = asLong(expiresIn);
    return clock.instant().plusSeconds(in != null ? in : 3600);
  }

  private static Long asLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text) {
      try {
        return Long.parseLong(text.strip());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /** {@code https://storage.azure.com/.default} → {@code https://storage.azure.com}. */
  static String resourceOf(String scope) {
    return scope.endsWith("/.default") ? scope.substring(0, scope.length() - "/.default".length()) : scope;
  }

  private static Map<String, Object> parse(String text) {
    try {
      return BoosterJson.parseObject(text);
    } catch (RuntimeException e) {
      return Map.of();
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
  }

  private static CommandResult runProcess(List<String> command) throws IOException {
    Process process = new ProcessBuilder(command).start();
    try {
      byte[] out = process.getInputStream().readAllBytes();
      byte[] err = process.getErrorStream().readAllBytes();
      if (!process.waitFor(CLI_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        return new CommandResult(-1, "", "az account get-access-token timed out");
      }
      return new CommandResult(process.exitValue(), new String(out, StandardCharsets.UTF_8),
          new String(err, StandardCharsets.UTF_8));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new IOException("Interrupted while running the Azure CLI", e);
    }
  }
}
