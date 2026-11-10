package com.boostercloud.framework.provider.local.infrastructure.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An Express 4 {@code Router}: an ordered stack of middleware ({@link #use}) and routes ({@link #get}, {@link #post},
 * ...), which is itself a {@link RequestHandler} so it can be mounted in another router. This is what the local
 * infrastructure gives to {@code LocalInfrastructureRocket.mountStack}, the Java counterpart of the
 * {@code express.Router} TypeScript rockets receive.
 *
 * <p>Dispatch follows Express: layers are tried in registration order; a route runs when its path matches the whole
 * request path and its method matches ({@code GET} routes also answer {@code HEAD}); a middleware mounted at a path
 * runs for every request under that path (whole segments), with the mount path removed from {@link HttpRequest#url()}
 * and added to {@link HttpRequest#baseUrl()} while it runs. A handler that calls {@code next()} passes control to the
 * next matching layer, {@code next(error)} (or throwing) skips to the next {@link ErrorHandler}; when the stack is
 * exhausted control goes back to the enclosing router. Paths are matched case-insensitively and tolerate a trailing
 * slash (Express's defaults), see {@link RoutePattern}.
 *
 * <p>Routers can be modified while serving (layers are copied on write); in practice everything is mounted before
 * the server starts.
 */
public final class Router implements RequestHandler {

  private final boolean caseSensitive;
  private final boolean strict;
  private final List<Layer> stack = new CopyOnWriteArrayList<>();

  /** A router with Express's defaults (case-insensitive, non-strict). */
  public Router() {
    this(false, false);
  }

  /**
   * @param caseSensitive whether paths are matched case-sensitively ({@code caseSensitive} option)
   * @param strict whether a trailing slash matters ({@code strict} option)
   */
  public Router(boolean caseSensitive, boolean strict) {
    this.caseSensitive = caseSensitive;
    this.strict = strict;
  }

  private sealed interface Layer permits Middleware, ErrorMiddleware, Route {
    RoutePattern pattern();
  }

  private record Middleware(RoutePattern pattern, RequestHandler handler) implements Layer {}

  private record ErrorMiddleware(RoutePattern pattern, ErrorHandler handler) implements Layer {}

  private record Route(RoutePattern pattern, String method, List<RequestHandler> handlers) implements Layer {
    boolean handlesMethod(String requestMethod) {
      if (method == null) {
        return true;
      }
      String name = requestMethod.toUpperCase(Locale.ROOT);
      return method.equals(name) || (name.equals("HEAD") && method.equals("GET"));
    }
  }

  /**
   * {@code router.use(fn...)}: middleware for every request.
   *
   * @param handlers the middleware (routers included)
   * @return this router
   */
  public Router use(RequestHandler... handlers) {
    return use("/", handlers);
  }

  /**
   * {@code router.use(path, fn...)}: middleware for the requests under {@code path}.
   *
   * @param path the mount path ({@code /sensor/health})
   * @param handlers the middleware (routers included)
   * @return this router
   */
  public Router use(String path, RequestHandler... handlers) {
    RoutePattern pattern = RoutePattern.compile(normalize(path), false, caseSensitive, false);
    for (RequestHandler handler : handlers) {
      stack.add(new Middleware(pattern, Objects.requireNonNull(handler, "handler")));
    }
    return this;
  }

  /**
   * {@code router.use((err, req, res, next) => ...)}: an error handler for every request.
   *
   * @param handler the error handler
   * @return this router
   */
  public Router use(ErrorHandler handler) {
    return use("/", handler);
  }

  /**
   * {@code router.use(path, (err, req, res, next) => ...)}.
   *
   * @param path the mount path
   * @param handler the error handler
   * @return this router
   */
  public Router use(String path, ErrorHandler handler) {
    stack.add(new ErrorMiddleware(RoutePattern.compile(normalize(path), false, caseSensitive, false),
        Objects.requireNonNull(handler, "handler")));
    return this;
  }

  /**
   * @param path the route path
   * @param handlers the handlers, run in order through {@code next()}
   * @return this router
   */
  public Router get(String path, RequestHandler... handlers) {
    return route("GET", path, handlers);
  }

  /**
   * @param path the route path
   * @param handlers the handlers
   * @return this router
   */
  public Router post(String path, RequestHandler... handlers) {
    return route("POST", path, handlers);
  }

  /**
   * @param path the route path
   * @param handlers the handlers
   * @return this router
   */
  public Router put(String path, RequestHandler... handlers) {
    return route("PUT", path, handlers);
  }

  /**
   * @param path the route path
   * @param handlers the handlers
   * @return this router
   */
  public Router patch(String path, RequestHandler... handlers) {
    return route("PATCH", path, handlers);
  }

  /**
   * @param path the route path
   * @param handlers the handlers
   * @return this router
   */
  public Router delete(String path, RequestHandler... handlers) {
    return route("DELETE", path, handlers);
  }

  /**
   * @param path the route path
   * @param handlers the handlers
   * @return this router
   */
  public Router head(String path, RequestHandler... handlers) {
    return route("HEAD", path, handlers);
  }

  /**
   * @param path the route path
   * @param handlers the handlers
   * @return this router
   */
  public Router options(String path, RequestHandler... handlers) {
    return route("OPTIONS", path, handlers);
  }

  /**
   * {@code router.all(path, fn...)}: a route for every method.
   *
   * @param path the route path
   * @param handlers the handlers
   * @return this router
   */
  public Router all(String path, RequestHandler... handlers) {
    return route(null, path, handlers);
  }

  /**
   * {@code router[method](path, fn...)}.
   *
   * @param method the HTTP method ({@code null} for every method)
   * @param path the route path
   * @param handlers the handlers
   * @return this router
   */
  public Router route(String method, String path, RequestHandler... handlers) {
    if (handlers.length == 0) {
      throw new IllegalArgumentException("Route." + (method == null ? "all" : method.toLowerCase(Locale.ROOT))
          + "() requires a callback function but got a [object Undefined]");
    }
    List<RequestHandler> list = new ArrayList<>();
    for (RequestHandler handler : handlers) {
      list.add(Objects.requireNonNull(handler, "handler"));
    }
    stack.add(new Route(RoutePattern.compile(path, true, caseSensitive, strict),
        method == null ? null : method.toUpperCase(Locale.ROOT), List.copyOf(list)));
    return this;
  }

  /**
   * @return the number of layers (middleware and routes)
   */
  public int size() {
    return stack.size();
  }

  @Override
  public void handle(HttpRequest request, HttpResponse response, NextFunction out) {
    new Dispatch(request, response, out, List.copyOf(stack)).next(null);
  }

  /** Express catches whatever a handler throws; only errors the JVM cannot recover from escape. */
  static Throwable rethrowFatal(Throwable error) {
    if (error instanceof VirtualMachineError fatal) {
      throw fatal;
    }
    return error;
  }

  private static String normalize(String path) {
    if (path == null || path.isEmpty()) {
      return "/";
    }
    return path.startsWith("/") ? path : "/" + path;
  }

  /** One pass of a request through the stack ({@code proto.handle} and its {@code next} closure). */
  private final class Dispatch {
    private final HttpRequest request;
    private final HttpResponse response;
    private final NextFunction out;
    private final List<Layer> layers;
    private final String parentUrl;
    private final Map<String, String> parentParams;
    private int index;
    private String removed = "";
    private boolean slashAdded;

    Dispatch(HttpRequest request, HttpResponse response, NextFunction out, List<Layer> layers) {
      this.request = request;
      this.response = response;
      this.out = out;
      this.layers = layers;
      this.parentUrl = request.baseUrl();
      this.parentParams = request.params();
    }

    void next(Throwable error) {
      Throwable layerError = error;
      if (slashAdded) {
        request.url(request.url().substring(1));
        slashAdded = false;
      }
      if (!removed.isEmpty()) {
        request.baseUrl(parentUrl);
        request.url(removed + request.url());
        removed = "";
      }
      String path = Uris.pathname(request.url());
      Layer layer = null;
      RoutePattern.Match match = null;
      while (match == null && index < layers.size()) {
        layer = layers.get(index++);
        try {
          match = layer.pattern().match(path);
        } catch (URIError e) {
          layerError = layerError == null ? e : layerError;
          match = null;
          continue;
        }
        if (match == null) {
          continue;
        }
        if (layer instanceof Route route) {
          if (layerError != null || !route.handlesMethod(request.method())) {
            match = null;
          }
        }
      }
      if (match == null) {
        done(layerError);
        return;
      }
      request.params(match.params());
      if (layer instanceof Route route) {
        new RouteDispatch(route, request, response, this::next).next(null);
        return;
      }
      String layerPath = match.path();
      if (!layerPath.isEmpty()) {
        if (path.length() > layerPath.length()) {
          char c = path.charAt(layerPath.length());
          if (c != '/' && c != '.') {
            next(layerError);
            return;
          }
        }
        removed = layerPath;
        request.url(request.url().substring(removed.length()));
        if (!request.url().startsWith("/")) {
          request.url("/" + request.url());
          slashAdded = true;
        }
        request.baseUrl(parentUrl + (removed.endsWith("/") ? removed.substring(0, removed.length() - 1) : removed));
      }
      if (layer instanceof ErrorMiddleware errorMiddleware) {
        if (layerError == null) {
          next(null);
          return;
        }
        invokeErrorHandler(errorMiddleware.handler(), layerError);
      } else {
        Middleware middleware = (Middleware) layer;
        if (layerError != null) {
          next(layerError);
          return;
        }
        invoke(middleware.handler());
      }
    }

    private void invoke(RequestHandler handler) {
      try {
        handler.handle(request, response, this::next);
      } catch (RuntimeException | Error e) {
        next(rethrowFatal(e));
      }
    }

    private void invokeErrorHandler(ErrorHandler handler, Throwable error) {
      try {
        handler.handle(error, request, response, this::next);
      } catch (RuntimeException | Error e) {
        next(rethrowFatal(e));
      }
    }

    private void done(Throwable error) {
      request.baseUrl(parentUrl);
      request.params(parentParams);
      out.next(error);
    }
  }

  /** The handlers of one route ({@code Route.prototype.dispatch}). */
  private static final class RouteDispatch {
    private final Route route;
    private final HttpRequest request;
    private final HttpResponse response;
    private final NextFunction done;
    private int index;

    RouteDispatch(Route route, HttpRequest request, HttpResponse response, NextFunction done) {
      this.route = route;
      this.request = request;
      this.response = response;
      this.done = done;
    }

    void next(Throwable error) {
      if (error != null || index >= route.handlers().size()) {
        done.next(error);
        return;
      }
      RequestHandler handler = route.handlers().get(index++);
      try {
        handler.handle(request, response, this::next);
      } catch (RuntimeException | Error e) {
        next(rethrowFatal(e));
      }
    }
  }
}
