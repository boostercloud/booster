package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A health request. Port of {@code HealthEnvelope}.
 *
 * @param requestID id of the request
 * @param currentUser the user, or {@code null} for anonymous requests
 * @param context the request context, or {@code null}
 * @param componentPath the indicator path after the health endpoint, without leading slash ({@code ""} for the root)
 * @param token the raw {@code authorization} header value, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"requestID", "currentUser", "context", "componentPath", "token"})
public record HealthEnvelope(
    UUID requestID,
    UserEnvelope currentUser,
    ContextEnvelope context,
    String componentPath,
    String token) implements Envelope {

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

  /** Builder of {@link HealthEnvelope}. */
  public static final class Builder {
    private UUID requestID;
    private UserEnvelope currentUser;
    private ContextEnvelope context;
    private String componentPath;
    private String token;

    private Builder() {}

    private Builder(HealthEnvelope source) {
      this.requestID = source.requestID();
      this.currentUser = source.currentUser();
      this.context = source.context();
      this.componentPath = source.componentPath();
      this.token = source.token();
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
     * @param componentPath the indicator path after the health endpoint, without leading slash ({@code ""} for the root)
     * @return this builder
     */
    public Builder componentPath(String componentPath) {
      this.componentPath = componentPath;
      return this;
    }

    /**
     * @param token the raw {@code authorization} header value, or {@code null}
     * @return this builder
     */
    public Builder token(String token) {
      this.token = token;
      return this;
    }

    /**
     * @return the record
     */
    public HealthEnvelope build() {
      return new HealthEnvelope(requestID, currentUser, context, componentPath, token);
    }
  }
}
