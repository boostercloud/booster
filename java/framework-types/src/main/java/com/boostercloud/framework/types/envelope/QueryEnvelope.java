package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * A query request. TypeScript declares {@code type QueryEnvelope = CommandEnvelope}; Java needs a type of its own, with
 * the same fields ({@link #fromCommandEnvelope(CommandEnvelope)} and {@link #toCommandEnvelope()} convert).
 *
 * @param requestID id of the request
 * @param currentUser the user, or {@code null} for anonymous requests
 * @param context the request context, or {@code null}
 * @param typeName the query class name
 * @param version the schema version of the query
 * @param value the raw query input
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"requestID", "currentUser", "context", "typeName", "version", "value"})
public record QueryEnvelope(
    UUID requestID,
    UserEnvelope currentUser,
    ContextEnvelope context,
    String typeName,
    int version,
    Map<String, Object> value) implements TypedEnvelope {

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
   * @param envelope a command envelope
   * @return a query envelope with the same values
   */
  public static QueryEnvelope fromCommandEnvelope(CommandEnvelope envelope) {
    return new QueryEnvelope(envelope.requestID(), envelope.currentUser(), envelope.context(), envelope.typeName(),
        envelope.version(), envelope.value());
  }

  /**
   * @return a command envelope with the same values
   */
  public CommandEnvelope toCommandEnvelope() {
    return new CommandEnvelope(requestID, currentUser, context, typeName, version, value);
  }

  /** Builder of {@link QueryEnvelope}. */
  public static final class Builder {
    private UUID requestID;
    private UserEnvelope currentUser;
    private ContextEnvelope context;
    private String typeName;
    private int version;
    private Map<String, Object> value;

    private Builder() {}

    private Builder(QueryEnvelope source) {
      this.requestID = source.requestID();
      this.currentUser = source.currentUser();
      this.context = source.context();
      this.typeName = source.typeName();
      this.version = source.version();
      this.value = source.value();
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
     * @param typeName the query class name
     * @return this builder
     */
    public Builder typeName(String typeName) {
      this.typeName = typeName;
      return this;
    }

    /**
     * @param version the schema version of the query
     * @return this builder
     */
    public Builder version(int version) {
      this.version = version;
      return this;
    }

    /**
     * @param value the raw query input
     * @return this builder
     */
    public Builder value(Map<String, Object> value) {
      this.value = value;
      return this;
    }

    /**
     * @return the record
     */
    public QueryEnvelope build() {
      return new QueryEnvelope(requestID, currentUser, context, typeName, version, value);
    }
  }
}
