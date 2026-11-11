package com.boostercloud.framework.provider.local.infrastructure.web;

import com.boostercloud.framework.types.Logger;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.GracefulHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.VirtualThreads;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * The HTTP listener of the local server: a Jetty 12 server whose requests run on virtual threads and go through one
 * {@link RequestHandler} (the application router), with {@link FinalHandler} behind it. Node's {@code http.Server}
 * plus Express's {@code app.handle} in TypeScript.
 *
 * <p>Lifecycle: {@link #open()} binds the port (so the real port is known before routes are mounted),
 * {@link #start()} starts serving, {@link #stop()} shuts down gracefully: the connector stops accepting connections,
 * new requests on open connections get {@code 503}, in-flight requests may finish within the stop timeout, then every
 * connection is closed.
 *
 * <p>Jetty settings that mirror Node's defaults: no {@code Server} header, 16 KiB of request headers, and lenient URI
 * checks (encoded slashes and dot segments are passed through, like Node).
 */
public final class HttpServer implements AutoCloseable {

  /** Node's default maximum size of the request headers ({@code --max-http-header-size}). */
  public static final int MAX_REQUEST_HEADER_SIZE = 16 * 1024;

  private final RequestHandler app;
  private final Supplier<Logger> logger;
  private final Server server;
  private final ServerConnector connector;
  private boolean opened;

  /**
   * @param port the port ({@code 0} picks a free one)
   * @param app the application handler
   * @param stopTimeout how long {@link #stop()} waits for in-flight requests
   * @param logger where request errors are logged
   */
  public HttpServer(int port, RequestHandler app, Duration stopTimeout, Supplier<Logger> logger) {
    this.app = Objects.requireNonNull(app, "app");
    this.logger = Objects.requireNonNull(logger, "logger");
    com.boostercloud.framework.provider.local.library.JettyLogging.prepare();
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setName("booster-local-http");
    threadPool.setDaemon(true);
    threadPool.setVirtualThreadsExecutor(VirtualThreads.getNamedVirtualThreadsExecutor("booster-local-request"));
    this.server = new Server(threadPool);
    HttpConfiguration configuration = new HttpConfiguration();
    configuration.setSendServerVersion(false);
    configuration.setSendXPoweredBy(false);
    configuration.setRequestHeaderSize(MAX_REQUEST_HEADER_SIZE);
    configuration.setUriCompliance(UriCompliance.LEGACY);
    this.connector = new ServerConnector(server, new HttpConnectionFactory(configuration));
    connector.setName("booster-local");
    connector.setPort(port);
    server.addConnector(connector);
    server.setHandler(new GracefulHandler(new Adapter()));
    server.setStopTimeout(stopTimeout.toMillis());
    server.setStopAtShutdown(false);
  }

  /**
   * Binds the port.
   *
   * @return the bound port
   * @throws IllegalStateException when the port cannot be bound (in use, no permission)
   */
  public synchronized int open() {
    if (!opened) {
      try {
        connector.open();
      } catch (IOException e) {
        String reason = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage()
            : e.getMessage();
        throw new IllegalStateException("Could not listen on port " + connector.getPort() + ": " + reason, e);
      }
      opened = true;
    }
    return connector.getLocalPort();
  }

  /**
   * Starts serving (binding first if needed).
   */
  public synchronized void start() {
    open();
    try {
      server.start();
    } catch (Exception e) {
      try {
        server.stop();
      } catch (Exception ignored) {
        // nothing more to release
      }
      throw new IllegalStateException("Could not start the HTTP server: " + e.getMessage(), e);
    }
  }

  /**
   * @return the bound port, or {@code -1} before {@link #open()}
   */
  public synchronized int port() {
    return opened ? connector.getLocalPort() : -1;
  }

  /**
   * @return whether the server is serving
   */
  public boolean isRunning() {
    return server.isRunning();
  }

  /**
   * Graceful shutdown (idempotent).
   */
  public synchronized void stop() {
    try {
      if (server.isStarted() || server.isStarting()) {
        server.stop();
      } else if (opened) {
        connector.close();
      }
    } catch (Exception e) {
      logger.get().warn("Error stopping the local HTTP server", e);
    }
    opened = false;
  }

  @Override
  public void close() {
    stop();
  }

  /** Turns each Jetty request into an {@link HttpRequest}/{@link HttpResponse} pair and runs the application. */
  private final class Adapter extends Handler.Abstract {

    @Override
    public boolean handle(Request request, Response response, Callback callback) {
      String pathQuery = request.getHttpURI().getPathQuery();
      HttpRequest.Builder builder = HttpRequest.builder(request.getMethod(), pathQuery == null ? "/" : pathQuery);
      for (HttpField field : request.getHeaders()) {
        builder.header(field.getName(), field.getValue());
      }
      HttpRequest httpRequest = builder.remoteAddress(Request.getRemoteAddr(request))
          .body(Request.asInputStream(request))
          .build();
      HttpResponse httpResponse = new JettyHttpResponse(response, HttpMethod.HEAD.is(request.getMethod()));
      FinalHandler.Outcome[] outcome = new FinalHandler.Outcome[1];
      try {
        app.handle(httpRequest, httpResponse,
            error -> outcome[0] = FinalHandler.handle(error, httpRequest, httpResponse, logger.get()));
        if (outcome[0] == FinalHandler.Outcome.ABORT) {
          callback.failed(new IllegalStateException("The response failed after it started"));
          return true;
        }
        if (!httpResponse.finished()) {
          httpResponse.end();
        }
        callback.succeeded();
      } catch (Throwable error) {
        Router.rethrowFatal(error);
        logger.get().error(error);
        if (!httpResponse.headersSent()) {
          Response.writeError(request, response, callback, 500);
        } else {
          callback.failed(error);
        }
      }
      return true;
    }
  }
}
