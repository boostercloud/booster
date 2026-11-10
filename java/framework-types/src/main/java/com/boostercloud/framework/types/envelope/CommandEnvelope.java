package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * A command request. Port of {@code CommandEnvelope}; {@code value} is the raw GraphQL input
 * ({@code CommandInput = Record<string, any>}).
 *
 * @param requestID id of the request
 * @param currentUser the user, or {@code null} for anonymous requests
 * @param context the request context, or {@code null}
 * @param typeName the command class name
 * @param version the schema version of the command
 * @param value the raw command input
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"requestID", "currentUser", "context", "typeName", "version", "value"})
public record CommandEnvelope(
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

  /** Builder of {@link CommandEnvelope}. */
  public static final class Builder {
    private UUID requestID;
    private UserEnvelope currentUser;
    private ContextEnvelope context;
    private String typeName;
    private int version;
    private Map<String, Object> value;

    private Builder() {}

    private Builder(CommandEnvelope source) {
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
     * @param typeName the command class name
     * @return this builder
     */
    public Builder typeName(String typeName) {
      this.typeName = typeName;
      return this;
    }

    /**
     * @param version the schema version of the command
     * @return this builder
     */
    public Builder version(int version) {
      this.version = version;
      return this;
    }

    /**
     * @param value the raw command input
     * @return this builder
     */
    public Builder value(Map<String, Object> value) {
      this.value = value;
      return this;
    }

    /**
     * @return the record
     */
    public CommandEnvelope build() {
      return new CommandEnvelope(requestID, currentUser, context, typeName, version, value);
    }
  }
}
