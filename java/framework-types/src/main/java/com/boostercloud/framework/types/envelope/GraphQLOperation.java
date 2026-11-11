package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * A GraphQL operation. Port of {@code GraphQLOperation}; {@code id} is only present for websocket operations.
 *
 * @param query the GraphQL document
 * @param id the websocket operation id, optional
 * @param operationName the operation to run, optional
 * @param variables the variables, optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"query", "id", "operationName", "variables"})
public record GraphQLOperation(
    String query,
    String id,
    String operationName,
    Map<String, Object> variables) implements GraphQLRequestValue {

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

  /** Builder of {@link GraphQLOperation}. */
  public static final class Builder {
    private String query;
    private String id;
    private String operationName;
    private Map<String, Object> variables;

    private Builder() {}

    private Builder(GraphQLOperation source) {
      this.query = source.query();
      this.id = source.id();
      this.operationName = source.operationName();
      this.variables = source.variables();
    }

    /**
     * @param query the GraphQL document
     * @return this builder
     */
    public Builder query(String query) {
      this.query = query;
      return this;
    }

    /**
     * @param id the websocket operation id, optional
     * @return this builder
     */
    public Builder id(String id) {
      this.id = id;
      return this;
    }

    /**
     * @param operationName the operation to run, optional
     * @return this builder
     */
    public Builder operationName(String operationName) {
      this.operationName = operationName;
      return this;
    }

    /**
     * @param variables the variables, optional
     * @return this builder
     */
    public Builder variables(Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    /**
     * @return the record
     */
    public GraphQLOperation build() {
      return new GraphQLOperation(query, id, operationName, variables);
    }
  }
}
