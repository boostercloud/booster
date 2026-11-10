package com.boostercloud.framework.provider.azure.eventhubs.rest;

import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.provider.azure.eventhubs.CreateBatchOptions;
import com.boostercloud.framework.provider.azure.eventhubs.EventData;
import com.boostercloud.framework.provider.azure.eventhubs.EventDataBatch;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubProducerClient;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubRetryOptions;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubsException;
import com.boostercloud.framework.provider.azure.eventhubs.RetryMode;
import com.boostercloud.framework.provider.azure.http.UriEncoding;
import com.boostercloud.framework.types.BoosterJson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * Production {@link EventHubProducerClient}: the Event Hubs REST API "Send batch events"
 * ({@code POST https://<ns>.servicebus.windows.net/<hub>/messages?timeout=60&api-version=2014-01}, content type
 * {@code application/vnd.microsoft.servicebus.json}) authenticated with a shared access signature derived from the
 * connection string. Each event is {@code {"Body": "<JSON>", "BrokerProperties": {"PartitionKey": "<key>"}}}, so all
 * the events of an entity land in the same partition, like the AMQP producer of {@code @azure/event-hubs}.
 *
 * <p>Transient failures (connection errors, 408, 429, 5xx) are retried with the {@code @azure/core-amqp} policy:
 * {@code maxRetries} retries, fixed delay {@code retryDelayInMs} or exponential
 * {@code min((2^attempt - 1) * random[0.8, 1.2) * retryDelayInMs, maxRetryDelayInMs)}.
 */
public final class EventHubsRestProducerClient implements EventHubProducerClient {

  /** Maximum batch payload (1 MiB, the Standard tier message size limit). */
  public static final long DEFAULT_MAX_BATCH_SIZE_IN_BYTES = 1_048_576;
  /** REST API version. */
  public static final String API_VERSION = "2014-01";
  /** Content type of a batch. */
  public static final String BATCH_CONTENT_TYPE = "application/vnd.microsoft.servicebus.json";

  private static final Set<Integer> TRANSIENT_STATUSES = Set.of(408, 429, 500, 502, 503, 504);

  private final EventHubConnectionString connectionString;
  private final String entityPath;
  private final EventHubRetryOptions retryOptions;
  private final HttpClient httpClient;
  private final Clock clock;
  private final Retrier.Sleeper sleeper;
  private final DoubleSupplier random;

  /**
   * @param connectionString the connection string
   * @param eventHubName the event hub ({@code EVENTHUB_NAME}), or {@code null} to use the connection string's
   *     {@code EntityPath}
   * @param retryOptions the retry options
   * @param httpClient the HTTP client
   * @param clock clock used for token expiry
   * @param sleeper waits between retries
   * @param random jitter source in [0, 1)
   */
  public EventHubsRestProducerClient(
      EventHubConnectionString connectionString,
      String eventHubName,
      EventHubRetryOptions retryOptions,
      HttpClient httpClient,
      Clock clock,
      Retrier.Sleeper sleeper,
      DoubleSupplier random) {
    this.connectionString = connectionString;
    String fromConnectionString = connectionString.entityPath();
    if (eventHubName != null && fromConnectionString != null && !eventHubName.equals(fromConnectionString)) {
      throw new IllegalArgumentException("The entity path \"" + fromConnectionString
          + "\" in connectionString doesn't match with eventHubName: \"" + eventHubName + "\".");
    }
    this.entityPath = eventHubName != null ? eventHubName : fromConnectionString;
    if (this.entityPath == null || this.entityPath.isEmpty()) {
      throw new IllegalArgumentException(
          "Either provide \"eventHubName\" or the \"connectionString\" must contain \"EntityPath\".");
    }
    this.retryOptions = retryOptions;
    this.httpClient = httpClient;
    this.clock = clock;
    this.sleeper = sleeper;
    this.random = random;
  }

  /**
   * @param connectionString {@code EVENTHUB_CONNECTION_STRING}
   * @param eventHubName {@code EVENTHUB_NAME}
   * @param retryOptions the retry options
   * @return the production client
   */
  public static EventHubsRestProducerClient create(
      String connectionString, String eventHubName, EventHubRetryOptions retryOptions) {
    return new EventHubsRestProducerClient(
        EventHubConnectionString.parse(connectionString),
        eventHubName,
        retryOptions,
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
        Clock.systemUTC(),
        Retrier.Sleeper.THREAD_SLEEP,
        Math::random);
  }

  /**
   * @return the event hub the client sends to
   */
  public String eventHubName() {
    return entityPath;
  }

  @Override
  public EventDataBatch createBatch(CreateBatchOptions options) {
    long max = options == null || options.maxSizeInBytes() == null
        ? DEFAULT_MAX_BATCH_SIZE_IN_BYTES
        : options.maxSizeInBytes();
    return new RestEventDataBatch(options == null ? null : options.partitionKey(), max);
  }

  @Override
  public void sendBatch(EventDataBatch batch) {
    if (!(batch instanceof RestEventDataBatch restBatch)) {
      throw new IllegalArgumentException("The batch was not created by this producer");
    }
    if (restBatch.count() == 0) {
      return;
    }
    String body = restBatch.payload();
    URI resource = connectionString.httpBase().resolve(UriEncoding.encodeURIComponent(entityPath));
    URI target = URI.create(resource + "/messages?timeout=60&api-version=" + API_VERSION);
    for (int attempt = 1; ; attempt++) {
      try {
        send(target, resource.toString(), body);
        return;
      } catch (EventHubsException e) {
        if (!e.isTransient() || attempt > retryOptions.maxRetries()) {
          throw e;
        }
        sleeper.sleep(delayFor(attempt));
      }
    }
  }

  long delayFor(int attempt) {
    long base = retryOptions.retryDelayInMs();
    if (retryOptions.mode() == RetryMode.FIXED) {
      return base;
    }
    double boundedRandDelta = base * 0.8 + Math.floor(random.getAsDouble() * (base * 1.2 - base * 0.8));
    double incrementDelta = Math.pow(2, attempt) - 1;
    return (long) Math.min(incrementDelta * boundedRandDelta, retryOptions.maxRetryDelayInMs());
  }

  private void send(URI target, String resourceUri, String body) {
    String authorization = connectionString.sharedAccessSignature() != null
        ? connectionString.sharedAccessSignature()
        : SharedAccessSignatures.token(resourceUri, connectionString.sharedAccessKeyName(),
            connectionString.sharedAccessKey(),
            clock.instant().getEpochSecond() + SharedAccessSignatures.TOKEN_VALIDITY_SECONDS);
    HttpRequest request = HttpRequest.newBuilder(target)
        .timeout(Duration.ofSeconds(60))
        .header("Authorization", authorization)
        .header("Content-Type", BATCH_CONTENT_TYPE)
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new EventHubsException("Could not send the batch to Event Hubs: " + e.getMessage(), 0, true, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EventHubsException("Interrupted while sending to Event Hubs", 0, false, e);
    }
    int status = response.statusCode();
    if (status >= 200 && status < 300) {
      return;
    }
    String detail = response.body() == null || response.body().isBlank() ? "" : ": " + response.body();
    throw new EventHubsException("Event Hubs rejected the batch with status " + status + detail, status,
        TRANSIENT_STATUSES.contains(status), null);
  }

  /** A batch that measures its REST payload size. */
  static final class RestEventDataBatch implements EventDataBatch {
    private final String partitionKey;
    private final long maxSizeInBytes;
    private final List<EventData> events = new ArrayList<>();
    private final List<String> entries = new ArrayList<>();
    private long sizeInBytes = 2; // "[" and "]"

    RestEventDataBatch(String partitionKey, long maxSizeInBytes) {
      this.partitionKey = partitionKey;
      this.maxSizeInBytes = maxSizeInBytes;
    }

    @Override
    public boolean tryAdd(EventData eventData) {
      String entry = entryFor(eventData);
      long entrySize = entry.getBytes(StandardCharsets.UTF_8).length + (entries.isEmpty() ? 0 : 1);
      if (sizeInBytes + entrySize > maxSizeInBytes) {
        return false;
      }
      sizeInBytes += entrySize;
      entries.add(entry);
      events.add(eventData);
      return true;
    }

    private String entryFor(EventData eventData) {
      Object body = eventData.body();
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("Body", body instanceof String text ? text : BoosterJson.toJson(body));
      if (partitionKey != null) {
        entry.put("BrokerProperties", Map.of("PartitionKey", partitionKey));
      }
      return BoosterJson.toJson(entry);
    }

    String payload() {
      return "[" + String.join(",", entries) + "]";
    }

    @Override
    public int count() {
      return events.size();
    }

    @Override
    public long sizeInBytes() {
      return sizeInBytes;
    }

    @Override
    public long maxSizeInBytes() {
      return maxSizeInBytes;
    }

    @Override
    public String partitionKey() {
      return partitionKey;
    }

    @Override
    public List<EventData> events() {
      return Collections.unmodifiableList(events);
    }
  }
}
