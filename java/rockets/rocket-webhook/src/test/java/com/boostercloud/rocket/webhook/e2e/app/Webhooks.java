package com.boostercloud.rocket.webhook.e2e.app;

import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.rocket.webhook.types.AllowedHttpMethod;
import com.boostercloud.rocket.webhook.types.MultiPartConfig;
import com.boostercloud.rocket.webhook.types.WebhookEvent;
import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import com.boostercloud.rocket.webhook.types.WebhookParsedField;
import com.boostercloud.rocket.webhook.types.WebhookParsedFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The webhooks of the end-to-end application and their handlers (classes with a static {@code handle}). */
public final class Webhooks {

  /** The file size limit of the {@code upload} webhook. */
  public static final long UPLOAD_LIMIT = 256 * 1024;

  /** The API key of the {@code partner/notify} webhook. */
  public static final String API_KEY = "s3cr3t";

  private Webhooks() {}

  /**
   * @return every webhook, in the order they are matched
   */
  public static List<WebhookParamsEvent> params() {
    return List.of(
        WebhookParamsEvent.builder().route("orders").handlerClass(PlaceOrder.class).build(),
        WebhookParamsEvent.builder().route("inspect").handlerClass(Inspect.class)
            .allowedMethods(AllowedHttpMethod.GET, AllowedHttpMethod.POST).build(),
        WebhookParamsEvent.builder().route("status").handlerClass(Status.class)
            .allowedMethods(AllowedHttpMethod.GET).build(),
        WebhookParamsEvent.builder().route("reports/daily").handlerClass(DailyReport.class)
            .allowedMethods(AllowedHttpMethod.GET).build(),
        WebhookParamsEvent.builder().route("upload").handlerClass(Upload.class)
            .multiPartConfig(MultiPartConfig.of(MultiPartConfig.Limits.fileSize(UPLOAD_LIMIT))).build(),
        WebhookParamsEvent.builder().route("admin/ping").handlerClass(AdminPing.class)
            .authorizeRoles(Admin.class).build(),
        WebhookParamsEvent.builder().route("partner/notify").handlerClass(PartnerNotify.class)
            .authorize((currentUser, request) -> {
              if (!API_KEY.equals(request.req().header("x-api-key"))) {
                throw new NotAuthorizedError("Invalid API key");
              }
            }).build(),
        WebhookParamsEvent.builder().route("fail").handlerClass(Fail.class).build());
  }

  /** {@code POST /orders}: places an order from a JSON body. */
  public static final class PlaceOrder {
    private PlaceOrder() {}

    public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
      Map<?, ?> body = event.body() instanceof Map<?, ?> map ? map : Map.of();
      if (!(body.get("product") instanceof String product) || product.isBlank()) {
        throw new InvalidParameterError("The product is required");
      }
      UUID orderId = body.get("orderId") instanceof String id ? UUID.of(id) : UUID.generate();
      int quantity = body.get("quantity") instanceof Number number ? number.intValue() : 1;
      String source = event.header("x-source") == null ? "webhook" : event.header("x-source");
      register.events(new OrderPlaced(orderId, product, quantity, source));
      if (Boolean.TRUE.equals(body.get("failAfterRegistering"))) {
        throw new InvalidParameterError("The order was rejected after registering its event");
      }
      Map<String, Object> answer = new LinkedHashMap<>();
      answer.put("orderId", orderId.value());
      answer.put("status", "accepted");
      return WebhookHandlerReturnType.json(answer).withHeaders(Map.of("Location", "/orders/" + orderId.value()));
    }
  }

  /** {@code GET|POST /inspect}: answers what the handler received. */
  public static final class Inspect {
    private Inspect() {}

    public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
      Map<String, Object> answer = new LinkedHashMap<>();
      answer.put("route", event.route());
      answer.put("method", event.method());
      answer.put("url", event.url());
      answer.put("originalUrl", event.originalUrl());
      answer.put("query", event.query());
      answer.put("params", event.params());
      answer.put("contentType", event.header("content-type"));
      answer.put("rawBodyType", event.rawBody() == null ? "none" : event.rawBody().getClass().getSimpleName());
      answer.put("rawBody", event.rawBodyText());
      answer.put("bodyType", event.body() == null ? "none" : event.body() instanceof Map ? "object"
          : event.body().getClass().getSimpleName());
      answer.put("body", event.body() instanceof byte[] bytes ? bytes.length : event.body());
      answer.put("multipart", event.multipart() != null);
      answer.put("user", register.currentUser() == null ? null : register.currentUser().username());
      return WebhookHandlerReturnType.json(answer);
    }
  }

  /** {@code GET /status?name=...}: a text answer with a custom header. */
  public static final class Status {
    private Status() {}

    public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
      Object name = event.query().get("name");
      return WebhookHandlerReturnType.text("Hello " + (name == null ? "stranger" : name))
          .withHeaders(Map.of("X-Webhook", "status"));
    }
  }

  /** {@code GET /reports/daily}: a CSV download. */
  public static final class DailyReport {
    /** The report content. */
    public static final String CSV = "id,product,quantity\n1,keyboard,2\n2,mouse,1\n";

    private DailyReport() {}

    public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
      return WebhookHandlerReturnType.file(CSV.getBytes(StandardCharsets.UTF_8), "daily.csv", "text/csv");
    }
  }

  /** {@code POST /upload}: one document per file of a multipart form. */
  public static final class Upload {
    private Upload() {}

    public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
      if (event.multipart() == null) {
        throw new InvalidParameterError("A multipart/form-data body is required");
      }
      String title = event.multipart().fields().stream().filter(field -> "title".equals(field.name()))
          .map(WebhookParsedField::value).findFirst().orElse("untitled");
      List<Map<String, Object>> documents = new ArrayList<>();
      for (WebhookParsedFile file : event.multipart().files()) {
        UUID id = UUID.generate();
        String sha256 = sha256(file.bufferFile());
        register.events(new DocumentUploaded(id, title, file.filename(), file.mimeType(), file.size(), sha256));
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", id.value());
        document.put("fileName", file.filename());
        document.put("size", file.size());
        document.put("sha256", sha256);
        documents.add(document);
      }
      return WebhookHandlerReturnType.json(Map.of("documents", documents));
    }
  }

  /** {@code POST /admin/ping}: only for admins; answers nothing. */
  public static final class AdminPing {
    private AdminPing() {}

    public static void handle(WebhookEvent event, Register register) {}
  }

  /** {@code POST /partner/notify}: behind a custom authorizer. */
  public static final class PartnerNotify {
    private PartnerNotify() {}

    public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
      return WebhookHandlerReturnType.text("ok");
    }
  }

  /** {@code POST /fail?kind=...}: failing handlers. */
  public static final class Fail {
    private Fail() {}

    public static void handle(WebhookEvent event, Register register) {
      if ("notfound".equals(event.query().get("kind"))) {
        throw new NotFoundError("Nothing here");
      }
      throw new IllegalStateException("boom");
    }
  }

  /**
   * @param bytes content
   * @return its SHA-256, hex
   */
  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
