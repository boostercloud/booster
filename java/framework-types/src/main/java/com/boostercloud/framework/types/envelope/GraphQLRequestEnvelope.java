package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A GraphQL request (HTTP or websocket). Port of {@code GraphQLRequestEnvelope}. {@code value} is a
 * {@link GraphQLOperation} for HTTP and a {@code GraphQLClientMessage} for websockets.
 *
 * @param requestID id of the request
 * @param currentUser the user, or {@code null} for anonymous requests
 * @param context the request context, or {@code null}
 * @param eventType {@code CONNECT}, {@code MESSAGE} or {@code DISCONNECT} (HTTP requests are {@code MESSAGE})
 * @param connectionID the websocket connection id, absent for HTTP
 * @param value the operation or websocket message, optional
 * @param token the raw {@code Authorization} header value (with {@code Bearer }), optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"requestID", "currentUser", "context", "eventType", "connectionID", "value", "token"})
public record GraphQLRequestEnvelope(
    UUID requestID,
    UserEnvelope currentUser,
    ContextEnvelope context,
    EventType eventType,
    String connectionID,
    GraphQLRequestValue value,
    String token) implements Envelope, GraphQLRequestEnvelopeOrError {

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

  /** Builder of {@link GraphQLRequestEnvelope}. */
  public static final class Builder {
    private UUID requestID;
    private UserEnvelope currentUser;
    private ContextEnvelope context;
    private EventType eventType;
    private String connectionID;
    private GraphQLRequestValue value;
    private String token;

    private Builder() {}

    private Builder(GraphQLRequestEnvelope source) {
      this.requestID = source.requestID();
      this.currentUser = source.currentUser();
      this.context = source.context();
      this.eventType = source.eventType();
      this.connectionID = source.connectionID();
      this.value = source.value();
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
     * @param eventType {@code CONNECT}, {@code MESSAGE} or {@code DISCONNECT} (HTTP requests are {@code MESSAGE})
     * @return this builder
     */
    public Builder eventType(EventType eventType) {
      this.eventType = eventType;
      return this;
    }

    /**
     * @param connectionID the websocket connection id, absent for HTTP
     * @return this builder
     */
    public Builder connectionID(String connectionID) {
      this.connectionID = connectionID;
      return this;
    }

    /**
     * @param value the operation or websocket message, optional
     * @return this builder
     */
    public Builder value(GraphQLRequestValue value) {
      this.value = value;
      return this;
    }

    /**
     * @param token the raw {@code Authorization} header value (with {@code Bearer }), optional
     * @return this builder
     */
    public Builder token(String token) {
      this.token = token;
      return this;
    }

    /**
     * @return the record
     */
    public GraphQLRequestEnvelope build() {
      return new GraphQLRequestEnvelope(requestID, currentUser, context, eventType, connectionID, value, token);
    }
  }
}
