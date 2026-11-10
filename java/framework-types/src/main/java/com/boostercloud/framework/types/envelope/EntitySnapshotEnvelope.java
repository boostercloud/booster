package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A stored entity snapshot. Port of {@code EntitySnapshotEnvelope}; also used for
 * {@code EntitySnapshotEnvelopeFromDatabase} (where {@code id} is always present).
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
 * @param id the database id, when known
 * @param createdAt logical creation date: the creation date of the latest event included
 * @param persistedAt when the snapshot was actually persisted
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"version", "kind", "superKind", "requestID", "currentUser", "entityID", "entityTypeName", "typeName", "value", "snapshottedEventCreatedAt", "context", "id", "createdAt", "persistedAt"})
public record EntitySnapshotEnvelope(
    int version,
    SuperKindType superKind,
    UUID requestID,
    UserEnvelope currentUser,
    UUID entityID,
    String entityTypeName,
    String typeName,
    Object value,
    String snapshottedEventCreatedAt,
    ContextEnvelope context,
    String id,
    String createdAt,
    String persistedAt) implements EventStoreEntryEnvelope {

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
   * @return this snapshot without the persistence fields
   */
  public NonPersistedEntitySnapshotEnvelope toNonPersisted() {
    return new NonPersistedEntitySnapshotEnvelope(version, superKind, requestID, currentUser, entityID, entityTypeName,
        typeName, value, snapshottedEventCreatedAt, context);
  }

  /** Builder of {@link EntitySnapshotEnvelope}. */
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
    private String id;
    private String createdAt;
    private String persistedAt;

    private Builder() {}

    private Builder(EntitySnapshotEnvelope source) {
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
      this.id = source.id();
      this.createdAt = source.createdAt();
      this.persistedAt = source.persistedAt();
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
     * @param id the database id, when known
     * @return this builder
     */
    public Builder id(String id) {
      this.id = id;
      return this;
    }

    /**
     * @param createdAt logical creation date: the creation date of the latest event included
     * @return this builder
     */
    public Builder createdAt(String createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * @param persistedAt when the snapshot was actually persisted
     * @return this builder
     */
    public Builder persistedAt(String persistedAt) {
      this.persistedAt = persistedAt;
      return this;
    }

    /**
     * @return the record
     */
    public EntitySnapshotEnvelope build() {
      return new EntitySnapshotEnvelope(version, superKind, requestID, currentUser, entityID, entityTypeName, typeName, value, snapshottedEventCreatedAt, context, id, createdAt, persistedAt);
    }
  }
}
