package com.boostercloud.framework.provider.local.infrastructure.web;

/**
 * An error-handling middleware: the Java form of Express's four-argument {@code (err, req, res, next) => void}. It
 * only runs when a previous handler failed; {@link NextFunction#next(Throwable)} passes the error on.
 */
@FunctionalInterface
public interface ErrorHandler {
  /**
   * @param error the error raised by a previous handler
   * @param request the request
   * @param response the response
   * @param next passes control to the next error handler ({@code next(error)}) or back to normal handlers
   *     ({@code next()})
   */
  void handle(Throwable error, HttpRequest request, HttpResponse response, NextFunction next);
}
