package com.boostercloud.framework.provider.local.infrastructure.web;

import com.boostercloud.framework.types.Logger;
import java.util.Map;

/**
 * What Express does when a request leaves the application without being answered ({@code finalhandler} 1.x).
 *
 * <ul>
 *   <li>No error: {@code 404} with the HTML page {@code Cannot <METHOD> <path>} (the path of the original URL,
 *       URL-encoded and HTML-escaped), {@code Content-Security-Policy: default-src 'none'} and
 *       {@code X-Content-Type-Options: nosniff}.
 *   <li>An error: it is logged; when the response has not started, the status is the error's own 4xx/5xx status, or
 *       500, and the page shows the status message (Express's production page; the development page with the stack
 *       trace is not reproduced). When the response already started, nothing more can be sent: an unfinished response
 *       is aborted (Express destroys the socket).
 * </ul>
 */
public final class FinalHandler {

  private static final Map<Integer, String> STATUS_MESSAGES = Map.ofEntries(
      Map.entry(400, "Bad Request"), Map.entry(401, "Unauthorized"), Map.entry(403, "Forbidden"),
      Map.entry(404, "Not Found"), Map.entry(405, "Method Not Allowed"), Map.entry(408, "Request Timeout"),
      Map.entry(413, "Payload Too Large"), Map.entry(415, "Unsupported Media Type"),
      Map.entry(422, "Unprocessable Entity"), Map.entry(500, "Internal Server Error"),
      Map.entry(501, "Not Implemented"), Map.entry(502, "Bad Gateway"), Map.entry(503, "Service Unavailable"));

  private FinalHandler() {}

  /** The outcome of the final handler. */
  public enum Outcome {
    /** A 404 or error page was sent. */
    RESPONDED,
    /** The response was already complete. */
    ALREADY_SENT,
    /** The response had started and must be aborted. */
    ABORT
  }

  /**
   * @param error the error that reached the end of the application, or {@code null}
   * @param request the request
   * @param response the response
   * @param logger where errors are logged
   * @return what happened
   */
  public static Outcome handle(Throwable error, HttpRequest request, HttpResponse response, Logger logger) {
    int status;
    String message;
    if (error == null) {
      status = 404;
      message = "Cannot " + request.method() + " " + Uris.encodeUrl(Uris.pathname(request.originalUrl()));
    } else {
      logger.error(error);
      status = error instanceof HttpStatusError statusError && statusError.status() >= 400
          && statusError.status() < 600 ? statusError.status() : 500;
      message = STATUS_MESSAGES.getOrDefault(status, Integer.toString(status));
    }
    if (response.finished()) {
      return Outcome.ALREADY_SENT;
    }
    if (response.headersSent()) {
      return Outcome.ABORT;
    }
    for (String header : new String[] {"Content-Encoding", "Content-Language", "Content-Range"}) {
      response.removeHeader(header);
    }
    response.status(status);
    response.setHeader("Content-Security-Policy", "default-src 'none'");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Content-Type", "text/html; charset=utf-8");
    response.send(htmlDocument(message));
    return Outcome.RESPONDED;
  }

  /**
   * finalhandler's {@code createHtmlDocument}.
   *
   * @param message the message
   * @return the page
   */
  static String htmlDocument(String message) {
    String body = Uris.escapeHtml(message).replaceAll("\\n", "<br>").replace("  ", " &nbsp;");
    return "<!DOCTYPE html>\n"
        + "<html lang=\"en\">\n"
        + "<head>\n"
        + "<meta charset=\"utf-8\">\n"
        + "<title>Error</title>\n"
        + "</head>\n"
        + "<body>\n"
        + "<pre>" + body + "</pre>\n"
        + "</body>\n"
        + "</html>\n";
  }
}
