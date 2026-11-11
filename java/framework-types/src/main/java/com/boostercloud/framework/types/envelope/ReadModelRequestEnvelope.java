package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

/**
 * A read model request (by id, search or subscription). Port of {@code ReadModelRequestEnvelope}.
 *
 * @param requestID id of the request
 * @param currentUser the user, or {@code null} for anonymous requests
 * @param context the request context, or {@code null}
 * @param key the id (and sequence key) for by-id requests, absent for searches
 * @param type the read model class (TypeScript {@code class}); not serialized, resolve it from {@code className}
 * @param className the read model class name
 * @param version the schema version requested
 * @param filters the filter, in {@code FilterFor} JSON shape
 * @param sortBy the sort, in {@code SortFor} JSON shape (key order is significant), optional
 * @param limit maximum number of results, optional
 * @param afterCursor opaque provider cursor, optional
 * @param paginatedVersion whether the paginated result shape was requested (retro-compatibility), optional
 * @param select the projection paths ({@code ProjectionFor}, with the {@code []} array convention), optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"requestID", "currentUser", "context", "key", "className", "version", "filters", "sortBy", "limit", "afterCursor", "paginatedVersion", "select"})
public record ReadModelRequestEnvelope(
    UUID requestID,
    UserEnvelope currentUser,
    ContextEnvelope context,
    Key key,
    @JsonIgnore Class<?> type,
    String className,
    int version,
    Map<String, Object> filters,
    Map<String, Object> sortBy,
    Integer limit,
    Object afterCursor,
    Boolean paginatedVersion,
    List<String> select) implements Envelope {

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
   * The key of a by-id read model request.
   *
   * @param id the read model id
   * @param sequenceKey the sequence key, for sequenced read models
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record Key(UUID id, SequenceKey sequenceKey) {}

  /** Builder of {@link ReadModelRequestEnvelope}. */
  public static final class Builder {
    private UUID requestID;
    private UserEnvelope currentUser;
    private ContextEnvelope context;
    private Key key;
    private Class<?> type;
    private String className;
    private int version;
    private Map<String, Object> filters;
    private Map<String, Object> sortBy;
    private Integer limit;
    private Object afterCursor;
    private Boolean paginatedVersion;
    private List<String> select;

    private Builder() {}

    private Builder(ReadModelRequestEnvelope source) {
      this.requestID = source.requestID();
      this.currentUser = source.currentUser();
      this.context = source.context();
      this.key = source.key();
      this.type = source.type();
      this.className = source.className();
      this.version = source.version();
      this.filters = source.filters();
      this.sortBy = source.sortBy();
      this.limit = source.limit();
      this.afterCursor = source.afterCursor();
      this.paginatedVersion = source.paginatedVersion();
      this.select = source.select();
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
     * @param context the request context, or {@code null}
     * @return this builder
     */
    public Builder context(ContextEnvelope context) {
      this.context = context;
      return this;
    }

    /**
     * @param key the id (and sequence key) for by-id requests, absent for searches
     * @return this builder
     */
    public Builder key(Key key) {
      this.key = key;
      return this;
    }

    /**
     * @param type the read model class (TypeScript {@code class}); not serialized, resolve it from {@code className}
     * @return this builder
     */
    public Builder type(Class<?> type) {
      this.type = type;
      return this;
    }

    /**
     * @param className the read model class name
     * @return this builder
     */
    public Builder className(String className) {
      this.className = className;
      return this;
    }

    /**
     * @param version the schema version requested
     * @return this builder
     */
    public Builder version(int version) {
      this.version = version;
      return this;
    }

    /**
     * @param filters the filter, in {@code FilterFor} JSON shape
     * @return this builder
     */
    public Builder filters(Map<String, Object> filters) {
      this.filters = filters;
      return this;
    }

    /**
     * @param sortBy the sort, in {@code SortFor} JSON shape (key order is significant), optional
     * @return this builder
     */
    public Builder sortBy(Map<String, Object> sortBy) {
      this.sortBy = sortBy;
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
     * @param afterCursor opaque provider cursor, optional
     * @return this builder
     */
    public Builder afterCursor(Object afterCursor) {
      this.afterCursor = afterCursor;
      return this;
    }

    /**
     * @param paginatedVersion whether the paginated result shape was requested (retro-compatibility), optional
     * @return this builder
     */
    public Builder paginatedVersion(Boolean paginatedVersion) {
      this.paginatedVersion = paginatedVersion;
      return this;
    }

    /**
     * @param select the projection paths ({@code ProjectionFor}, with the {@code []} array convention), optional
     * @return this builder
     */
    public Builder select(List<String> select) {
      this.select = select;
      return this;
    }

    /**
     * @return the record
     */
    public ReadModelRequestEnvelope build() {
      return new ReadModelRequestEnvelope(requestID, currentUser, context, key, type, className, version, filters, sortBy, limit, afterCursor, paginatedVersion, select);
    }
  }
}
