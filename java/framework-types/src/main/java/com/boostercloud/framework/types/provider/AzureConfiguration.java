package com.boostercloud.framework.types.provider;

/**
 * Azure provider tuning ({@code config.azureConfiguration}). Port of {@code provider/azure-configuration.ts}.
 */
public class AzureConfiguration {
  /** {@code DEFAULT_CHUNK_SIZE}: default maximum number of operations in a Cosmos DB batch. */
  public static final int DEFAULT_CHUNK_SIZE = 100;

  /** Whether events are stored in batches (default {@code true}). */
  public boolean enableEventBatching = true;

  /** Cosmos DB settings. */
  public final Cosmos cosmos = new Cosmos();

  /** Cosmos DB settings. */
  public static class Cosmos {
    /** Maximum number of operations in a single batch (default {@value AzureConfiguration#DEFAULT_CHUNK_SIZE}). */
    public int batchSize = DEFAULT_CHUNK_SIZE;

    /** Request options, or {@code null} (the default). */
    public RequestOptions requestOptions;
  }

  /**
   * Cosmos DB request options.
   *
   * @param consistencyLevel {@code Strong}, {@code BoundedStaleness}, {@code Session}, {@code Eventual} or
   *     {@code ConsistentPrefix}, optional
   * @param disableRUPerMinuteUsage optional
   * @param indexingDirective {@code Include} or {@code Exclude}, optional
   */
  public record RequestOptions(String consistencyLevel, Boolean disableRUPerMinuteUsage, String indexingDirective) {}
}
