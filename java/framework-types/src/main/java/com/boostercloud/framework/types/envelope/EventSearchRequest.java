package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A request to read events. Port of {@code EventSearchRequest}.
 *
 * @param requestID id of the request
 * @param currentUser the user, or {@code null} for anonymous requests
 * @param context the request context, or {@code null}
 * @param parameters what to search
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"requestID", "currentUser", "context", "parameters"})
public record EventSearchRequest(
    UUID requestID,
    UserEnvelope currentUser,
    ContextEnvelope context,
    EventSearchParameters parameters) implements Envelope {

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

  /** Builder of {@link EventSearchRequest}. */
  public static final class Builder {
    private UUID requestID;
    private UserEnvelope currentUser;
    private ContextEnvelope context;
    private EventSearchParameters parameters;

    private Builder() {}

    private Builder(EventSearchRequest source) {
      this.requestID = source.requestID();
      this.currentUser = source.currentUser();
      this.context = source.context();
      this.parameters = source.parameters();
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
     * @param parameters what to search
     * @return this builder
     */
    public Builder parameters(EventSearchParameters parameters) {
      this.parameters = parameters;
      return this;
    }

    /**
     * @return the record
     */
    public EventSearchRequest build() {
      return new EventSearchRequest(requestID, currentUser, context, parameters);
    }
  }
}
