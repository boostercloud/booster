package com.boostercloud.application.tester.graphql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A GraphQL operation ready to be sent: Apollo's link {@code Operation} after {@code transformOperation}. The
 * {@code query} is the printed (and, by default, {@code __typename}-augmented) document.
 *
 * <p>On the websocket the payload of the {@code start} message is {@link #toStartPayload()}:
 * {@code {"variables": ..., "extensions": {}, "operationName": ..., "query": ...}} ({@code operationName} omitted for
 * anonymous operations); over HTTP the body is {@code {"operationName", "variables", "query"}} with the unused
 * variables stripped.
 *
 * @param query the GraphQL text
 * @param operationName the operation name, {@code null} for anonymous operations
 * @param variables the variables (defaults of the document merged with the caller's), may hold {@code null} values
 * @param extensions the operation extensions ({@code {}})
 * @param subscription whether the main definition is a subscription (Apollo's split-link test)
 */
public record Operation(
    String query,
    String operationName,
    Map<String, Object> variables,
    Map<String, Object> extensions,
    boolean subscription) {

  /** Canonical constructor; maps are copied (keeping order and {@code null} values) and made unmodifiable. */
  public Operation {
    Objects.requireNonNull(query, "query");
    variables = Collections.unmodifiableMap(variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables));
    extensions =
        Collections.unmodifiableMap(extensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extensions));
  }

  /**
   * Prepares an operation like Apollo's {@code QueryManager}: parses and checks the document, optionally adds
   * {@code __typename} to its selection sets, and merges the variables over the document's default values.
   *
   * @param document the GraphQL text
   * @param variables the caller's variables, may be {@code null}
   * @param addTypename whether to apply {@link GraphQLDocument#withTypename()}
   * @return the operation
   * @throws IllegalArgumentException for invalid documents
   */
  public static Operation prepare(String document, Map<String, ?> variables, boolean addTypename) {
    GraphQLDocument parsed = GraphQLDocument.parse(document);
    if (addTypename) {
      parsed = parsed.withTypename();
    }
    Map<String, Object> merged = new LinkedHashMap<>(parsed.defaultVariableValues());
    if (variables != null) {
      merged.putAll(variables);
    }
    return new Operation(
        parsed.print(), parsed.operationName().orElse(null), merged, new LinkedHashMap<>(), parsed.isSubscription());
  }

  /**
   * @return the payload of a websocket {@code start} message, in the key order Apollo produces
   */
  public Map<String, Object> toStartPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("variables", variables);
    payload.put("extensions", extensions);
    if (operationName != null) {
      payload.put("operationName", operationName);
    }
    payload.put("query", query);
    return payload;
  }
}
