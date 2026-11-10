package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A stored event. Port of {@code EventEnvelope}; also used for TypeScript's {@code EventEnvelopeFromDatabase}
 * (where {@code id} is always present).
 *
 * @param version the schema version of the event
 * @param superKind {@code domain} for user events, {@code booster} for framework events
 * @param entityID id of the entity the event belongs to
 * @param requestID id of the request
 * @param currentUser the user, or {@code null} for anonymous requests
 * @param entityTypeName the entity class name
 * @param typeName the event class name
 * @param value the event: an instance when built by user code, a raw value when read from storage
 * @param context the request context, or {@code null}
 * @param id the database id, when known
 * @param createdAt ISO storage time (lexically ordered)
 * @param deletedAt ISO deletion time, when the event was soft-deleted
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"version", "kind", "superKind", "entityID", "requestID", "currentUser", "entityTypeName", "typeName", "value", "context", "id", "createdAt", "deletedAt"})
public record EventEnvelope(
    int version,
    SuperKindType superKind,
    UUID entityID,
    UUID requestID,
    UserEnvelope currentUser,
    String entityTypeName,
    String typeName,
    Object value,
    ContextEnvelope context,
    String id,
    String createdAt,
    String deletedAt) implements EventStoreEntryEnvelope {

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

  /** Value of {@link #kind()} for events. */
  public static final String KIND = "event";

  @Override
  @JsonProperty("kind")
  public String kind() {
    return KIND;
  }

  /**
   * @return this envelope without the persistence fields
   */
  public NonPersistedEventEnvelope toNonPersisted() {
    return new NonPersistedEventEnvelope(version, superKind, entityID, requestID, currentUser, entityTypeName, typeName,
        value, context);
  }

  /**
   * TypeScript {@code !envelope.deletedAt}: an empty {@code deletedAt} counts as not deleted.
   *
   * @return whether the event was soft-deleted
   */
  @JsonIgnore
  public boolean isDeleted() {
    return deletedAt != null && !deletedAt.isEmpty();
  }

  /** Builder of {@link EventEnvelope}. */
  public static final class Builder {
    private int version;
    private SuperKindType superKind;
    private UUID entityID;
    private UUID requestID;
    private UserEnvelope currentUser;
    private String entityTypeName;
    private String typeName;
    private Object value;
    private ContextEnvelope context;
    private String id;
    private String createdAt;
    private String deletedAt;

    private Builder() {}

    private Builder(EventEnvelope source) {
      this.version = source.version();
      this.superKind = source.superKind();
      this.entityID = source.entityID();
      this.requestID = source.requestID();
      this.currentUser = source.currentUser();
      this.entityTypeName = source.entityTypeName();
      this.typeName = source.typeName();
      this.value = source.value();
      this.context = source.context();
      this.id = source.id();
      this.createdAt = source.createdAt();
      this.deletedAt = source.deletedAt();
    }

    /**
     * @param version the schema version of the event
     * @return this builder
     */
    public Builder version(int version) {
      this.version = version;
      return this;
    }

    /**
     * @param superKind {@code domain} for user events, {@code booster} for framework events
     * @return this builder
     */
    public Builder superKind(SuperKindType superKind) {
      this.superKind = superKind;
      return this;
    }

    /**
     * @param entityID id of the entity the event belongs to
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
     * @param currentUser the user, or {@code null} for anonymous requests
     * @return this builder
     */
    public Builder currentUser(UserEnvelope currentUser) {
      this.currentUser = currentUser;
      return this;
    }

    /**
     * @param entityTypeName the entity class name
     * @return this builder
     */
    public Builder entityTypeName(String entityTypeName) {
      this.entityTypeName = entityTypeName;
      return this;
    }

    /**
     * @param typeName the event class name
     * @return this builder
     */
    public Builder typeName(String typeName) {
      this.typeName = typeName;
      return this;
    }

    /**
     * @param value the event: an instance when built by user code, a raw value when read from storage
     * @return this builder
     */
    public Builder value(Object value) {
      this.value = value;
      return this;
    }

    /**
     * @param context the request context, or {@code null}
     * @return this builder
     */
    public Builder context(ContextEnvelope context) {
      this.context = context;
      return this;
    }

    /**
     * @param id the database id, when known
     * @return this builder
     */
    public Builder id(String id) {
      this.id = id;
      return this;
    }

    /**
     * @param createdAt ISO storage time (lexically ordered)
     * @return this builder
     */
    public Builder createdAt(String createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * @param deletedAt ISO deletion time, when the event was soft-deleted
     * @return this builder
     */
    public Builder deletedAt(String deletedAt) {
      this.deletedAt = deletedAt;
      return this;
    }

    /**
     * @return the record
     */
    public EventEnvelope build() {
      return new EventEnvelope(version, superKind, entityID, requestID, currentUser, entityTypeName, typeName, value, context, id, createdAt, deletedAt);
    }
  }
}
