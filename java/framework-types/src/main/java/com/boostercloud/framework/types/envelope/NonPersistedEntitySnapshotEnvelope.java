package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * An entity snapshot about to be stored. Port of {@code NonPersistedEntitySnapshotEnvelope} ({@code kind: 'snapshot'}).
 *
 * @param version the schema version of the entity
 * @param superKind the super kind
 * @param requestID id of the request
 * @param currentUser the user, or {@code null} for anonymous requests
 * @param entityID id of the entity
 * @param entityTypeName the entity class name
 * @param typeName the entity class name (same as {@code entityTypeName})
 * @param value the entity state: an instance or a raw value
 * @param snapshottedEventCreatedAt {@code createdAt} of the last event folded into the snapshot
 * @param context the request context, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"version", "kind", "superKind", "requestID", "currentUser", "entityID", "entityTypeName", "typeName", "value", "snapshottedEventCreatedAt", "context"})
public record NonPersistedEntitySnapshotEnvelope(
    int version,
    SuperKindType superKind,
    UUID requestID,
    UserEnvelope currentUser,
    UUID entityID,
    String entityTypeName,
    String typeName,
    Object value,
    String snapshottedEventCreatedAt,
    ContextEnvelope context) implements EventStoreEntryEnvelope {

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

  /** Value of {@link #kind()} for snapshots. */
  public static final String KIND = "snapshot";

  @Override
  @JsonProperty("kind")
  public String kind() {
    return KIND;
  }

  /**
   * @param persistedAt the wall-clock storage time
   * @return the persisted snapshot; {@code createdAt} is {@link #snapshottedEventCreatedAt()}, as providers do
   */
  public EntitySnapshotEnvelope persisted(String persistedAt) {
    return new EntitySnapshotEnvelope(version, superKind, requestID, currentUser, entityID, entityTypeName, typeName,
        value, snapshottedEventCreatedAt, context, null, snapshottedEventCreatedAt, persistedAt);
  }

  /** Builder of {@link NonPersistedEntitySnapshotEnvelope}. */
  public static final class Builder {
    private int version;
    private SuperKindType superKind;
    private UUID requestID;
    private UserEnvelope currentUser;
    private UUID entityID;
    private String entityTypeName;
    private String typeName;
    private Object value;
    private String snapshottedEventCreatedAt;
    private ContextEnvelope context;

    private Builder() {}

    private Builder(NonPersistedEntitySnapshotEnvelope source) {
      this.version = source.version();
      this.superKind = source.superKind();
      this.requestID = source.requestID();
      this.currentUser = source.currentUser();
      this.entityID = source.entityID();
      this.entityTypeName = source.entityTypeName();
      this.typeName = source.typeName();
      this.value = source.value();
      this.snapshottedEventCreatedAt = source.snapshottedEventCreatedAt();
      this.context = source.context();
    }

    /**
     * @param version the schema version of the entity
     * @return this builder
     */
    public Builder version(int version) {
      this.version = version;
      return this;
    }

    /**
     * @param superKind the super kind
     * @return this builder
     */
    public Builder superKind(SuperKindType superKind) {
      this.superKind = superKind;
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
     * @param entityID id of the entity
     * @return this builder
     */
    public Builder entityID(UUID entityID) {
      this.entityID = entityID;
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
     * @param typeName the entity class name (same as {@code entityTypeName})
     * @return this builder
     */
    public Builder typeName(String typeName) {
      this.typeName = typeName;
      return this;
    }

    /**
     * @param value the entity state: an instance or a raw value
     * @return this builder
     */
    public Builder value(Object value) {
      this.value = value;
      return this;
    }

    /**
     * @param snapshottedEventCreatedAt {@code createdAt} of the last event folded into the snapshot
     * @return this builder
     */
    public Builder snapshottedEventCreatedAt(String snapshottedEventCreatedAt) {
      this.snapshottedEventCreatedAt = snapshottedEventCreatedAt;
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
     * @return the record
     */
    public NonPersistedEntitySnapshotEnvelope build() {
      return new NonPersistedEntitySnapshotEnvelope(version, superKind, requestID, currentUser, entityID, entityTypeName, typeName, value, snapshottedEventCreatedAt, context);
    }
  }
}
