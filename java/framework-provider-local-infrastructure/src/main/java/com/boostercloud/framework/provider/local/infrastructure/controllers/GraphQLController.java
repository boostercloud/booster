package com.boostercloud.framework.provider.local.infrastructure.controllers;

import com.boostercloud.framework.provider.local.infrastructure.Http;
import com.boostercloud.framework.provider.local.infrastructure.HttpCodes;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpRequest;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpResponse;
import com.boostercloud.framework.provider.local.infrastructure.web.NextFunction;
import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.provider.local.services.GraphQLService;
import java.util.Objects;

/**
 * The GraphQL endpoint: {@code POST /} of a router the server mounts at {@code /graphql}. Port of
 * {@code controllers/graphql.ts}.
 *
 * <p>The request goes to {@link GraphQLService#handleGraphQLRequest(Object)} as a
 * {@link com.boostercloud.framework.provider.local.library.LocalHttpRequest} (TypeScript hands over the Express
 * request). A result is always answered with {@code 200} and {@code response.result} as JSON (the
 * {@code { data, errors }} payload): the result headers are not sent, as in TypeScript. When the service throws, the
 * error response of {@link Http#requestFailed} is sent and the error is also passed to {@code next}.
 */
public class GraphQLController {

  private final Router router = new Router();
  private final GraphQLService graphQLService;

  /**
   * @param graphQLService the service that hands requests to the application
   */
  public GraphQLController(GraphQLService graphQLService) {
    this.graphQLService = Objects.requireNonNull(graphQLService, "graphQLService");
    router.post("/", this::handleGraphQL);
  }

  /**
   * @return the router with the {@code POST /} route ({@code router} in TypeScript)
   */
  public Router router() {
    return router;
  }

  /**
   * @return the service
   */
  public GraphQLService graphQLService() {
    return graphQLService;
  }

  /**
   * {@code handleGraphQL(req, res, next)}.
   *
   * @param request the request
   * @param response the response
   * @param next the next function, called only with an error
   */
  public void handleGraphQL(HttpRequest request, HttpResponse response, NextFunction next) {
    try {
      Object result = graphQLService.handleGraphQLRequest(request.toLocalHttpRequest());
      ApiResults.requirePresent(result, "result");
      response.status(HttpCodes.OK.code());
      if (ApiResults.hasResult(result)) {
        response.json(ApiResults.result(result));
      } else {
        response.header("Content-Type", "application/json");
        response.end();
      }
    } catch (RuntimeException e) {
      Http.requestFailed(e, response);
      next.next(e);
    }
  }
}
