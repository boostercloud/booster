package com.boostercloud.framework.types.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code { type: 'error', id, payload: { errors } }}. Port of the TypeScript {@code GraphQLError} message class,
 * renamed because graphql-java already has a {@code GraphQLError} type. TypeScript puts {@code Error} objects in
 * {@code errors}, which {@code JSON.stringify} turns into {@code {}}; Java sends explicit
 * {@code { "message": ... }} entries (see {@link #of(String, List)}).
 *
 * @param id the operation id
 * @param payload the errors
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"id", "payload", "type"})
public record GraphQLErrorMessage(String id, Payload payload) implements GraphQLServerMessage {

  @Override
  @JsonProperty("type")
  public MessageTypes type() {
    return MessageTypes.GQL_ERROR;
  }

  /**
   * @param id the operation id
   * @param errors the errors to report
   * @return the message with one {@code {message}} entry per error
   */
  public static GraphQLErrorMessage of(String id, List<? extends Throwable> errors) {
    List<Map<String, Object>> entries = errors.stream().map(error -> {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("message", error.getMessage());
      return entry;
    }).toList();
    return new GraphQLErrorMessage(id, new Payload(entries));
  }

  /**
   * The errors payload.
   *
   * @param errors error entries (raw maps, at least a {@code message})
   */
  public record Payload(List<Map<String, Object>> errors) {}
}
