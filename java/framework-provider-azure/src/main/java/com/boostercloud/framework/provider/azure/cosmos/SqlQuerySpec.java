package com.boostercloud.framework.provider.azure.cosmos;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * A parameterised SQL query ({@code SqlQuerySpec} of {@code @azure/cosmos}).
 *
 * @param query the SQL text
 * @param parameters the parameters, in order (never {@code null})
 */
@JsonPropertyOrder({"query", "parameters"})
public record SqlQuerySpec(String query, List<SqlParameter> parameters) {

  /** Canonical constructor; {@code null} parameters become an empty list. */
  public SqlQuerySpec {
    parameters = parameters == null ? List.of() : List.copyOf(parameters);
  }

  /**
   * @param query the SQL text
   * @param parameters the parameters
   * @return the query spec
   */
  public static SqlQuerySpec of(String query, SqlParameter... parameters) {
    return new SqlQuerySpec(query, List.of(parameters));
  }

  /**
   * @param newQuery another SQL text
   * @return a copy with the same parameters ({@code { ...querySpec, query }})
   */
  public SqlQuerySpec withQuery(String newQuery) {
    return new SqlQuerySpec(newQuery, parameters);
  }
}
