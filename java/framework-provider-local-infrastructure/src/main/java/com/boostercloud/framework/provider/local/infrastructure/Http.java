package com.boostercloud.framework.provider.local.infrastructure;

import com.boostercloud.framework.provider.local.infrastructure.web.HttpResponse;
import com.boostercloud.framework.types.TypeLevel;
import com.boostercloud.framework.types.errors.BoosterErrors;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code http.ts}: the error response of the local server.
 */
public final class Http {

  private Http() {}

  /**
   * {@code requestFailed(error, res)}: answers with the status of the error class
   * ({@code httpStatusCodeFor(error)}: 400, 401, 404, 422 for the Booster errors that have one, 500 otherwise) and
   * {@code { "title": toClassTitle(error), "reason": error.message }}.
   *
   * @param error the error
   * @param response the response
   */
  public static void requestFailed(Throwable error, HttpResponse response) {
    response.status(BoosterErrors.httpStatusCodeFor(error)).json(errorBody(error));
  }

  /**
   * @param error an error
   * @return {@code { title, reason }}; a missing message is {@code ""}, like a JavaScript {@code Error}'s
   */
  public static Map<String, Object> errorBody(Throwable error) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", TypeLevel.toClassTitle(error));
    body.put("reason", error.getMessage() == null ? "" : error.getMessage());
    return body;
  }
}
