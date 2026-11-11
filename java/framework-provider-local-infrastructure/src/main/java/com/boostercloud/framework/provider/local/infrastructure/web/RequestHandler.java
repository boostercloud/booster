package com.boostercloud.framework.provider.local.infrastructure.web;

/**
 * A request handler or middleware: the Java form of Express's {@code (req, res, next) => void}. A {@link Router} is
 * itself a handler, so routers can be mounted inside routers.
 *
 * <p>A handler either answers (through the response) or passes control on with {@link NextFunction#next()};
 * {@link NextFunction#next(Throwable)} (or throwing) hands an error to the error handlers. Handlers run on virtual
 * threads and may block.
 */
@FunctionalInterface
public interface RequestHandler {
  /**
   * @param request the request
   * @param response the response
   * @param next passes control to the next matching handler
   */
  void handle(HttpRequest request, HttpResponse response, NextFunction next);
}
