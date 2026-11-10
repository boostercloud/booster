package com.boostercloud.rocket.fileuploads.azure.storage;

import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.azure.JdkHttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.azure.TokenCredential;
import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Java-only: how the Azure runtime library reaches Blob Storage, the replacement of the {@code BlobServiceClient}
 * and {@code DefaultAzureCredential} TypeScript creates in every {@code BlobService}. The process-wide
 * {@link #current()} context uses the JDK HTTP client, {@link DefaultAzureCredential}, the public endpoint
 * {@code https://<account>.blob.core.windows.net} and the system clock; tests and embedders replace it with
 * {@link #use(BlobStorageContext)} (for instance to point at a fake server or the Azurite emulator).
 *
 * @param transport sends the HTTP requests
 * @param credential gives the OAuth tokens for {@value #STORAGE_SCOPE}
 * @param endpoint the service URL of an account, without trailing slash
 * @param clock the clock of the SAS start and expiry
 * @param apiVersion the {@code x-ms-version} of the REST calls
 */
public record BlobStorageContext(HttpTransport transport, TokenCredential credential,
    Function<String, URI> endpoint, Clock clock, String apiVersion) {

  /** The OAuth scope of Azure Storage. */
  public static final String STORAGE_SCOPE = "https://storage.azure.com/.default";
  /** The REST API version of the Blob service calls (user delegation key, list, delete). */
  public static final String DEFAULT_API_VERSION = "2025-11-05";
  /** The public endpoint suffix ({@code STORAGE_ACCOUNT_ENDPOINT}). */
  public static final String STORAGE_ACCOUNT_ENDPOINT = "blob.core.windows.net";

  private static volatile BlobStorageContext current;

  /** Canonical constructor: every component is required. */
  public BlobStorageContext {
    Objects.requireNonNull(transport, "transport");
    Objects.requireNonNull(credential, "credential");
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(apiVersion, "apiVersion");
  }

  /**
   * @return the production context (JDK client, {@link DefaultAzureCredential}, public endpoint, system clock)
   */
  public static BlobStorageContext defaults() {
    HttpTransport transport = new JdkHttpTransport();
    return new BlobStorageContext(transport, new DefaultAzureCredential(transport), BlobStorageContext::publicEndpoint,
        Clock.systemUTC(), DEFAULT_API_VERSION);
  }

  /**
   * {@code https://<account>.blob.core.windows.net}: the host is lower-cased, as the SDK's URL normalisation does.
   *
   * @param accountName the storage account
   * @return its service URL
   */
  public static URI publicEndpoint(String accountName) {
    return URI.create("https://" + accountName.toLowerCase(Locale.ROOT) + "." + STORAGE_ACCOUNT_ENDPOINT);
  }

  /**
   * @return the context in use (the defaults, created on first use, unless replaced)
   */
  public static BlobStorageContext current() {
    BlobStorageContext context = current;
    if (context == null) {
      synchronized (BlobStorageContext.class) {
        if (current == null) {
          current = defaults();
        }
        context = current;
      }
    }
    return context;
  }

  /**
   * @param context the context every later call uses ({@code null} goes back to the defaults)
   */
  public static void use(BlobStorageContext context) {
    current = context;
  }

  /**
   * @param newEndpoint the service URL of an account
   * @return a copy with another endpoint
   */
  public BlobStorageContext withEndpoint(Function<String, URI> newEndpoint) {
    return new BlobStorageContext(transport, credential, newEndpoint, clock, apiVersion);
  }

  /**
   * @param newClock the clock
   * @return a copy with another clock
   */
  public BlobStorageContext withClock(Clock newClock) {
    return new BlobStorageContext(transport, credential, endpoint, newClock, apiVersion);
  }
}
