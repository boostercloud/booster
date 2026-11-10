package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Search the events of an event type. Port of {@code EventParametersFilterByType}.
 *
 * @param type the event class name
 * @param from ISO lower bound of {@code createdAt}, optional
 * @param to ISO upper bound of {@code createdAt}, optional
 * @param limit maximum number of results, optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"type", "from", "to", "limit"})
public record EventParametersFilterByType(
    String type,
    String from,
    String to,
    Integer limit) implements EventSearchParameters {

  /**
   * @return a builder initialised with the values of this record (TypeScript: object spread)
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  /**
   * @return an empty builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder of {@link EventParametersFilterByType}. */
  public static final class Builder {
    private String type;
    private String from;
    private String to;
    private Integer limit;

    private Builder() {}

    private Builder(EventParametersFilterByType source) {
      this.type = source.type();
      this.from = source.from();
      this.to = source.to();
      this.limit = source.limit();
    }

    /**
     * @param type the event class name
     * @return this builder
     */
    public Builder type(String type) {
      this.type = type;
      return this;
    }

    /**
     * @param from ISO lower bound of {@code createdAt}, optional
     * @return this builder
     */
    public Builder from(String from) {
      this.from = from;
      return this;
    }

    /**
     * @param to ISO upper bound of {@code createdAt}, optional
     * @return this builder
     */
    public Builder to(String to) {
      this.to = to;
      return this;
    }

    /**
     * @param limit maximum number of results, optional
     * @return this builder
     */
    public Builder limit(Integer limit) {
      this.limit = limit;
      return this;
    }

    /**
     * @return the record
     */
    public EventParametersFilterByType build() {
      return new EventParametersFilterByType(type, from, to, limit);
    }
  }
}
