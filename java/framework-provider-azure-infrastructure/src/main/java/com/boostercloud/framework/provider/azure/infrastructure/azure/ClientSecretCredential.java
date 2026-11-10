package com.boostercloud.framework.provider.azure.infrastructure.azure;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.types.BoosterJson;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service principal authentication ({@code new ClientSecretCredential(tenantId, appId, secret)} of
 * {@code azureCredentials()}): the OAuth 2.0 client credentials grant against
 * {@code https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token}. Tokens are cached per scope until two minutes
 * before they expire. As in TypeScript, creating the credential never fails; errors surface on the first request.
 */
public final class ClientSecretCredential implements TokenCredential {

  /** The authority host. */
  public static final String AUTHORITY_HOST = "https://login.microsoftonline.com";

  private static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);

  private final String tenantId;
  private final String clientId;
  private final String clientSecret;
  private final HttpTransport transport;
  private final Clock clock;
  private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

  private record CachedToken(String token, Instant expiresOn) {}

  /**
   * @param tenantId the tenant
   * @param clientId the application (client) id
   * @param clientSecret the secret
   * @param transport the HTTP transport
   * @param clock the clock used for the cache
   */
  public ClientSecretCredential(
      String tenantId, String clientId, String clientSecret, HttpTransport transport, Clock clock) {
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    this.clientId = Objects.requireNonNull(clientId, "clientId");
    this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret");
    this.transport = Objects.requireNonNull(transport, "transport");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * {@code azureCredentials()}.
   *
   * @param configuration the {@code AZURE_*} settings
   * @param transport the HTTP transport
   * @return the credential of the configured service principal
   */
  public static ClientSecretCredential fromConfiguration(Params.Configuration configuration, HttpTransport transport) {
    return new ClientSecretCredential(configuration.tenantId(), configuration.appId(), configuration.secret(),
        transport, Clock.systemUTC());
  }

  @Override
  public String getToken(String scope) {
    CachedToken cached = cache.get(scope);
    Instant now = clock.instant();
    if (cached != null && now.isBefore(cached.expiresOn().minus(REFRESH_MARGIN))) {
      return cached.token();
    }
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_id", clientId);
    form.put("scope", scope);
    form.put("client_secret", clientSecret);
    form.put("grant_type", "client_credentials");
    StringBuilder body = new StringBuilder();
    form.forEach((key, value) -> {
      if (body.length() > 0) {
        body.append('&');
      }
      body.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
          .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    });
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/x-www-form-urlencoded");
    headers.put("Accept", "application/json");
    HttpTransport.Response response;
    try {
      response = transport.send(new HttpTransport.Request("POST",
          URI.create(AUTHORITY_HOST + "/" + URLEncoder.encode(tenantId, StandardCharsets.UTF_8) + "/oauth2/v2.0/token"),
          headers, body.toString().getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(60)));
    } catch (UncheckedIOException e) {
      throw new AuthenticationError(0, "ClientSecretCredential authentication failed: " + e.getMessage(), e);
    }
    Map<String, Object> json = parse(response.bodyText());
    if (response.status() != 200 || !(json.get("access_token") instanceof String token)) {
      Object description = json.getOrDefault("error_description", json.getOrDefault("error", response.bodyText()));
      throw new AuthenticationError(response.status(),
          "ClientSecretCredential authentication failed: " + description, null);
    }
    long expiresIn = json.get("expires_in") instanceof Number number ? number.longValue()
        : json.get("expires_in") instanceof String text ? Long.parseLong(text) : 3600;
    cache.put(scope, new CachedToken(token, now.plusSeconds(expiresIn)));
    return token;
  }

  private static Map<String, Object> parse(String text) {
    try {
      return BoosterJson.parseObject(text);
    } catch (RuntimeException e) {
      return Map.of();
    }
  }
}
