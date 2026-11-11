package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One event returned by an event search. Port of {@code EventSearchResponse}; note the renames from
 * {@link EventEnvelope}: {@code type <- typeName}, {@code entity <- entityTypeName}, {@code user <- currentUser}.
 *
 * @param type the event class name
 * @param entity the entity class name
 * @param entityID the entity id
 * @param requestID id of the request
 * @param user the user that caused the event, or {@code null}
 * @param createdAt ISO creation time
 * @param value the event (instance or raw value)
 * @param deletedAt ISO deletion time, when soft-deleted
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"type", "entity", "entityID", "requestID", "user", "createdAt", "value", "deletedAt"})
public record EventSearchResponse(
    String type,
    String entity,
    UUID entityID,
    UUID requestID,
    UserEnvelope user,
    String createdAt,
    Object value,
    String deletedAt) {

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

  /**
   * @param envelope a stored event
   * @return the search response with the renamed fields
   */
  public static EventSearchResponse fromEventEnvelope(EventEnvelope envelope) {
    return new EventSearchResponse(envelope.typeName(), envelope.entityTypeName(), envelope.entityID(),
        envelope.requestID(), envelope.currentUser(), envelope.createdAt(), envelope.value(), envelope.deletedAt());
  }

  /** Builder of {@link EventSearchResponse}. */
  public static final class Builder {
    private String type;
    private String entity;
    private UUID entityID;
    private UUID requestID;
    private UserEnvelope user;
    private String createdAt;
    private Object value;
    private String deletedAt;

    private Builder() {}

    private Builder(EventSearchResponse source) {
      this.type = source.type();
      this.entity = source.entity();
      this.entityID = source.entityID();
      this.requestID = source.requestID();
      this.user = source.user();
      this.createdAt = source.createdAt();
      this.value = source.value();
      this.deletedAt = source.deletedAt();
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
     * @param entity the entity class name
     * @return this builder
     */
    public Builder entity(String entity) {
      this.entity = entity;
      return this;
    }

    /**
     * @param entityID the entity id
     * @return this builder
     */
    public Builder entityID(UUID entityID) {
      this.entityID = entityID;
      return this;
    }

    /**
     * @param requestID id of the request
     * @return this builder
     */
    public Builder requestID(UUID requestID) {
      this.requestID = requestID;
      return this;
    }

    /**
     * @param user the user that caused the event, or {@code null}
     * @return this builder
     */
    public Builder user(UserEnvelope user) {
      this.user = user;
      return this;
    }

    /**
     * @param createdAt ISO creation time
     * @return this builder
     */
    public Builder createdAt(String createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * @param value the event (instance or raw value)
     * @return this builder
     */
    public Builder value(Object value) {
      this.value = value;
      return this;
    }

    /**
     * @param deletedAt ISO deletion time, when soft-deleted
     * @return this builder
     */
    public Builder deletedAt(String deletedAt) {
      this.deletedAt = deletedAt;
      return this;
    }

    /**
     * @return the record
     */
    public EventSearchResponse build() {
      return new EventSearchResponse(type, entity, entityID, requestID, user, createdAt, value, deletedAt);
    }
  }
}
