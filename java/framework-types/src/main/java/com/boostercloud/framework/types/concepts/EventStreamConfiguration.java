package com.boostercloud.framework.types.concepts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/**
 * Event stream (Azure Event Hub) settings. Port of the union
 * {@code { enabled: true, parameters } | { enabled: false }}; JSON {@code {"enabled":false}} or
 * {@code {"enabled":true,"parameters":{...}}}.
 *
 * @param enabled whether the event stream is enabled
 * @param parameters the settings, required when enabled and absent otherwise
 */
public record EventStreamConfiguration(boolean enabled, Parameters parameters) {

  /** Canonical constructor; enforces the union shape. */
  public EventStreamConfiguration {
    if (enabled) {
      Objects.requireNonNull(parameters, "An enabled event stream needs parameters");
    } else if (parameters != null) {
      throw new IllegalArgumentException("A disabled event stream cannot have parameters");
    }
  }

  /**
   * @return {@code { enabled: false }}, the default
   */
  public static EventStreamConfiguration disabled() {
    return new EventStreamConfiguration(false, null);
  }

  /**
   * @param parameters the settings
   * @return {@code { enabled: true, parameters }}
   */
  public static EventStreamConfiguration enabled(Parameters parameters) {
    return new EventStreamConfiguration(true, parameters);
  }

  /**
   * Retry mode of the stream consumer.
   */
  public enum Mode {
    /** {@code 'exponential'}. */
    EXPONENTIAL("exponential"),
    /** {@code 'fixed'}. */
    FIXED("fixed");

    private final String value;

    Mode(String value) {
      this.value = value;
    }

    /**
     * @return the TypeScript literal
     */
    @JsonValue
    public String value() {
      return value;
    }

    /**
     * @param value the literal
     * @return the mode
     */
    @JsonCreator
    public static Mode fromValue(String value) {
      for (Mode mode : values()) {
        if (mode.value.equalsIgnoreCase(value)) {
          return mode;
        }
      }
      throw new IllegalArgumentException("Unknown event stream mode '" + value + "'");
    }
  }

  /**
   * Settings of an enabled event stream.
   *
   * @param streamTopic name of the stream topic ({@code resourceNames.streamTopic})
   * @param partitionCount number of partitions
   * @param messageRetention days to retain the events
   * @param dedupTtl de-duplication time to live in seconds, optional
   * @param maxRetries maximum retries, optional
   * @param mode retry mode, optional
   */
  public record Parameters(
      String streamTopic, int partitionCount, int messageRetention, Integer dedupTtl, Integer maxRetries, Mode mode) {
    /**
     * @param streamTopic the topic
     * @param partitionCount number of partitions
     * @param messageRetention retention days
     * @return parameters without the optional settings
     */
    public static Parameters of(String streamTopic, int partitionCount, int messageRetention) {
      return new Parameters(streamTopic, partitionCount, messageRetention, null, null, null);
    }
  }
}
