package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A scheduled command trigger. Port of {@code ScheduledCommandEnvelope}.
 *
 * @param requestID id of the request
 * @param currentUser the user, or {@code null} for anonymous requests
 * @param context the request context, or {@code null}
 * @param typeName the scheduled command class name
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"requestID", "currentUser", "context", "typeName"})
public record ScheduledCommandEnvelope(
    UUID requestID,
    UserEnvelope currentUser,
    ContextEnvelope context,
    String typeName) implements Envelope {

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

  /** Builder of {@link ScheduledCommandEnvelope}. */
  public static final class Builder {
    private UUID requestID;
    private UserEnvelope currentUser;
    private ContextEnvelope context;
    private String typeName;

    private Builder() {}

    private Builder(ScheduledCommandEnvelope source) {
      this.requestID = source.requestID();
      this.currentUser = source.currentUser();
      this.context = source.context();
      this.typeName = source.typeName();
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
     * @param typeName the scheduled command class name
     * @return this builder
     */
    public Builder typeName(String typeName) {
      this.typeName = typeName;
      return this;
    }

    /**
     * @return the record
     */
    public ScheduledCommandEnvelope build() {
      return new ScheduledCommandEnvelope(requestID, currentUser, context, typeName);
    }
  }
}
