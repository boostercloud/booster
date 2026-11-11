package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.Constants;
import com.boostercloud.framework.provider.azure.Constants.DedupAttributes;
import com.boostercloud.framework.provider.azure.Constants.OtherEnvironmentVarNames;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.types.AzureFunctionTypes;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code library/events-stream-consumer-adapter.ts}: de-duplicates Event Hub messages through the dedup
 * container (unique key {@code /primaryKey}) and maps them to envelopes.
 */
public final class EventsStreamConsumerAdapter {

  /** {@code DEFAULT_DEDUP_TTL}: one day, in seconds. */
  public static final int DEFAULT_DEDUP_TTL = 86400;

  private EventsStreamConsumerAdapter() {}

  /**
   * {@code dedupEventStream}: for each message, sequentially, creates
   * {@code { primaryKey: <_etag>, createdAt: now, ttl: dedupTtl ?? 86400 }}; a 409 (already seen) drops the message
   * with a warning, other errors propagate. The surviving messages are returned unparsed, in order. When a message has
   * no {@code _etag} (it was produced from an envelope this provider did not read), its {@code id} is the key instead.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param rawInput an Event Hub input, a list of messages, or anything else (no messages)
   * @return the messages that were not seen before
   */
  @SuppressWarnings("unchecked")
  public static List<Object> dedupEventStream(CosmosClient cosmosDb, BoosterConfig config, Object rawInput) {
    Logger logger = Loggers.getLogger(config, "dedup-events-stream#dedupEventsStream");

    List<Object> events = new ArrayList<>();
    if (AzureFunctionTypes.isEventHubFunctionInput(rawInput)) {
      events = (List<Object>) get(rawInput, "messages");
    } else if (rawInput instanceof List<?> list) {
      // Direct array for testing
      events = (List<Object>) list;
    }

    logger.debug("Dedup " + events.size() + " events");

    List<Object> resources = new ArrayList<>();
    for (Object event : events) {
      Object rawParsed = parse(event);
      Map<String, Object> eventTag = new LinkedHashMap<>();
      Object primaryKey = get(rawParsed, "_etag") != null ? get(rawParsed, "_etag") : get(rawParsed, "id");
      if (primaryKey != null) {
        eventTag.put(DedupAttributes.PARTITION_KEY, primaryKey);
      }
      eventTag.put("createdAt", Dates.nowIso());
      eventTag.put("ttl", dedupTtl(config));
      try {
        ItemResponse response = cosmosDb.database(config.resourceNames().applicationStack())
            .container(config.resourceNames().eventsDedup())
            .items()
            .create(eventTag);
        if (response != null && response.resource() != null) {
          resources.add(event);
        }
      } catch (RuntimeException error) {
        if (!(error instanceof CosmosException cosmosError)
            || cosmosError.code() != Constants.AZURE_CONFLICT_ERROR_CODE) {
          throw error;
        }
        logger.warn("Ignoring duplicated event with etag " + (primaryKey == null ? "undefined" : primaryKey) + ".");
      }
    }
    return resources;
  }

  /**
   * {@code rawEventsStreamToEnvelopes}: parses the de-duplicated messages into envelopes (keeping only documents of
   * kind {@code event}, see {@link EventsAdapter#rawEventsToEnvelopes(Object)}); logs the {@code CONSUMED_EVENT} line
   * when the input carries binding data.
   *
   * @param config the configuration
   * @param rawInput the original input
   * @param dedupEventStream the messages returned by {@link #dedupEventStream}
   * @return the envelopes
   */
  public static List<EventEnvelope> rawEventsStreamToEnvelopes(
      BoosterConfig config, Object rawInput, List<Object> dedupEventStream) {
    Logger logger = Loggers.getLogger(config, "events-adapter#rawEventsStreamToEnvelopes");
    logger.debug("Mapping " + dedupEventStream.size() + " events");

    // Extract binding data from the input
    Object bindingData = isEventHubConsumerInput(rawInput) ? get(rawInput, "bindingData") : null;

    List<Object> documents = new ArrayList<>();
    for (int index = 0; index < dedupEventStream.size(); index++) {
      Object rawParsed = parse(dedupEventStream.get(index));
      String instance = EnvironmentVariables.get(OtherEnvironmentVarNames.WEBSITE_INSTANCE_ID).orElse("undefined");
      if (bindingData != null) {
        logger.debug("CONSUMED_EVENT:" + instance + "#" + at(get(bindingData, "partitionKeyArray"), index) + "=>("
            + index + "#" + at(get(bindingData, "offsetArray"), index) + "#"
            + at(get(bindingData, "sequenceNumberArray"), index) + "#"
            + at(get(bindingData, "enqueuedTimeUtcArray"), index) + ")");
      }
      if (EventDocuments.isEvent(rawParsed)) {
        documents.add(rawParsed);
      }
    }
    return EventDocuments.toEventEnvelopes(documents);
  }

  private static Object parse(Object message) {
    return message instanceof String text ? BoosterJson.parse(text) : message;
  }

  private static int dedupTtl(BoosterConfig config) {
    var parameters = config.eventStreamConfiguration == null ? null : config.eventStreamConfiguration.parameters();
    return parameters != null && parameters.dedupTtl() != null ? parameters.dedupTtl() : DEFAULT_DEDUP_TTL;
  }

  /**
   * The private {@code isEventHubConsumerInput} guard: an Event Hub input whose {@code bindingData} is an object with a
   * {@code partitionKeyArray} array. Structural, so it accepts an {@link EventHubConsumerInput} record or a raw map.
   *
   * @param input the raw input
   * @return whether it carries binding data
   */
  static boolean isEventHubConsumerInput(Object input) {
    if (!AzureFunctionTypes.isEventHubFunctionInput(input)) {
      return false;
    }
    Object bindingData = get(input, "bindingData");
    boolean isObject = bindingData instanceof Map<?, ?> || bindingData instanceof EventHubConsumerInput.BindingData;
    return isObject && get(bindingData, "partitionKeyArray") instanceof List<?>;
  }

  private static String at(Object values, int index) {
    return values instanceof List<?> list && index < list.size() ? String.valueOf(list.get(index)) : "undefined";
  }
}
