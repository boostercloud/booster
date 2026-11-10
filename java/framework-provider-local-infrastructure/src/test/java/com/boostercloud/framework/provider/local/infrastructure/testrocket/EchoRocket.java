package com.boostercloud.framework.provider.local.infrastructure.testrocket;

import com.boostercloud.framework.provider.local.infrastructure.InfrastructureRocketMetadata;
import com.boostercloud.framework.provider.local.infrastructure.LocalInfrastructureRocket;
import com.boostercloud.framework.provider.local.infrastructure.web.BodyParsers;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpRequest;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpResponse;
import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.UserAppRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A test rocket mounting routes the way the webhook and file-uploads rockets do: a sub-router at a prefix with
 * POST/GET routes (raw bodies), a streaming PUT upload and a GET download, and a dispatch to the application.
 */
public class EchoRocket implements LocalInfrastructureRocket {

  /** Every mount and unmount, across instances. */
  public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

  private final Object parameters;

  EchoRocket(Object parameters) {
    this.parameters = parameters;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void mountStack(BoosterConfig config, Router router, InfrastructureRocketMetadata metadata) {
    Map<String, Object> params = (Map<String, Object>) parameters;
    Path storage = Path.of((String) params.get("storage"));
    EVENTS.add("mount " + params.get("prefix") + " port=" + metadata.port() + " app=" + config.appName);
    Router echo = new Router()
        .post("/:name", BodyParsers.raw(), EchoRocket::echo)
        .get("/:name", BodyParsers.raw(), EchoRocket::echo);
    router.use("/" + params.get("prefix"), echo);
    router.put("/files/:directory/:fileName", (request, response, next) -> {
      Path target = storage.resolve(request.param("directory")).resolve(request.param("fileName"));
      try (InputStream in = request.inputStream()) {
        Files.createDirectories(target.getParent());
        long size = Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        response.send(Map.of("status", "success", "size", size));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
    router.get("/files/:directory/:fileName", (request, response, next) ->
        response.download(storage.resolve(request.param("directory")).resolve(request.param("fileName"))));
    router.post("/dispatch", (request, response, next) -> {
      Object result = UserAppRegistry.get().boosterRocketDispatcher(
          Map.of("BOOSTER_ROCKET_FUNCTION_ID", "echo", "req", request.toLocalHttpRequest()));
      response.json(Map.of("dispatched", true, "result", String.valueOf(result)));
    });
  }

  private static void echo(HttpRequest request, HttpResponse response, com.boostercloud.framework.provider.local
      .infrastructure.web.NextFunction next) {
    Map<String, Object> echo = new LinkedHashMap<>();
    echo.put("method", request.method());
    echo.put("url", request.url());
    echo.put("originalUrl", request.originalUrl());
    echo.put("baseUrl", request.baseUrl());
    echo.put("params", request.params());
    echo.put("query", request.query());
    Object body = request.body();
    echo.put("body", body instanceof byte[] bytes ? "bytes:" + Base64.getEncoder().encodeToString(bytes) : body);
    echo.put("rawBody", request.rawBody() == null ? null : new String(request.rawBody(), StandardCharsets.UTF_8));
    response.status(201).json(echo);
  }

  @Override
  public void unmountStack() {
    EVENTS.add("unmount " + ((Map<?, ?>) parameters).get("prefix"));
  }
}
