package com.boostercloud.framework.provider.local.infrastructure.web;

/**
 * The {@code cors} middleware (2.8.5) with its default options, as the TypeScript local server installs it
 * ({@code expressServer.use(cors())}).
 *
 * <ul>
 *   <li>Every request gets {@code Access-Control-Allow-Origin: *}.
 *   <li>{@code OPTIONS} requests (pre-flight) are answered right away with {@code 204}, {@code Content-Length: 0},
 *       {@code Access-Control-Allow-Methods: GET,HEAD,PUT,PATCH,POST,DELETE}, the requested headers echoed in
 *       {@code Access-Control-Allow-Headers} (when the request has {@code Access-Control-Request-Headers}) and
 *       {@code Vary: Access-Control-Request-Headers}; later middleware and routes never see them.
 * </ul>
 */
public final class Cors {

  /** The methods allowed by default. */
  public static final String DEFAULT_METHODS = "GET,HEAD,PUT,PATCH,POST,DELETE";

  private Cors() {}

  /**
   * @return {@code cors()}
   */
  public static RequestHandler middleware() {
    return (request, response, next) -> {
      if (request.method().equals("OPTIONS")) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", DEFAULT_METHODS);
        response.vary("Access-Control-Request-Headers");
        String requested = request.header("access-control-request-headers");
        if (requested != null && !requested.isEmpty()) {
          response.setHeader("Access-Control-Allow-Headers", requested);
        }
        response.status(204);
        response.setHeader("Content-Length", "0");
        response.end();
      } else {
        response.setHeader("Access-Control-Allow-Origin", "*");
        next.next();
      }
    };
  }
}
