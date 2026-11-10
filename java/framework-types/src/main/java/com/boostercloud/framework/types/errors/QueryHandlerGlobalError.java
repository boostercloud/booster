package com.boostercloud.framework.types.errors;

import com.boostercloud.framework.types.envelope.QueryEnvelope;

/**
 * Context of an error thrown by a query handler; hook {@code onQueryHandlerError(error, query)}. Port of {@code QueryHandlerGlobalError}; the original error is the last constructor argument.
 */
public final class QueryHandlerGlobalError extends GlobalErrorContainer {
  private final QueryEnvelope query;

  /**
   * @param query the query
   * @param originalError the error that happened
   */
  public QueryHandlerGlobalError(QueryEnvelope query, Throwable originalError) {
    super(originalError);
    this.query = query;
  }

  /**
   * @return the query
   */
  public QueryEnvelope query() {
    return query;
  }
}
