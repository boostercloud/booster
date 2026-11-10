package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Search the events of an entity type (optionally one entity). Port of {@code EventParametersFilterByEntity}.
 *
 * @param entity the entity class name
 * @param entityID restricts the search to one entity, optional
 * @param from ISO lower bound of {@code createdAt}, optional
 * @param to ISO upper bound of {@code createdAt}, optional
 * @param limit maximum number of results, optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"entity", "entityID", "from", "to", "limit"})
public record EventParametersFilterByEntity(
    String entity,
    String entityID,
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

  /** Builder of {@link EventParametersFilterByEntity}. */
  public static final class Builder {
    private String entity;
    private String entityID;
    private String from;
    private String to;
    private Integer limit;

    private Builder() {}

    private Builder(EventParametersFilterByEntity source) {
      this.entity = source.entity();
      this.entityID = source.entityID();
      this.from = source.from();
      this.to = source.to();
      this.limit = source.limit();
    }

    /**
     * @param entity the entity class name
     * @return this builder
     */
    public Builder entity(String entity) {
      this.entity = entity;
      return this;
    }

    /**
     * @param entityID restricts the search to one entity, optional
     * @return this builder
     */
    public Builder entityID(String entityID) {
      this.entityID = entityID;
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
    public EventParametersFilterByEntity build() {
      return new EventParametersFilterByEntity(entity, entityID, from, to, limit);
    }
  }
}
