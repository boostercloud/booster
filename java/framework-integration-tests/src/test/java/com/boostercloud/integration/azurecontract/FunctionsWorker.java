package com.boostercloud.integration.azurecontract;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * What the Azure Functions Java worker hands to the entry points of
 * {@code com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions}: the {@link ExecutionContext} of an
 * invocation (function name from {@code function.json}'s folder, a fresh invocation id) and, for HTTP triggers, the
 * {@link HttpRequestMessage} with the worker's response builder. The contract tests call the entry points in-process
 * with these, exactly as the worker would with the bindings the synthesized {@code function.json} files declare.
 */
final class FunctionsWorker {

  private FunctionsWorker() {}

  /**
   * @param functionName the function (folder) name
   * @return the context of a new invocation
   */
  static ExecutionContext context(String functionName) {
    String invocationId = UUID.randomUUID().toString();
    Logger logger = Logger.getLogger("Function." + functionName);
    return new ExecutionContext() {
      @Override
      public Logger getLogger() {
        return logger;
      }

      @Override
      public String getInvocationId() {
        return invocationId;
      }

      @Override
      public String getFunctionName() {
        return functionName;
      }
    };
  }

  /**
   * @param url the request URL
   * @param headers the request headers
   * @param body the body
   * @return a POST request
   */
  static HttpRequestMessage<Optional<String>> post(String url, Map<String, String> headers, String body) {
    return new Request(HttpMethod.POST, URI.create(url), headers, Map.of(), body);
  }

  /** An HTTP trigger request. */
  record Request(HttpMethod method, URI uri, Map<String, String> headers, Map<String, String> query, String body)
      implements HttpRequestMessage<Optional<String>> {

    @Override
    public URI getUri() {
      return uri;
    }

    @Override
    public HttpMethod getHttpMethod() {
      return method;
    }

    @Override
    public Map<String, String> getHeaders() {
      return headers;
    }

    @Override
    public Map<String, String> getQueryParameters() {
      return query;
    }

    @Override
    public Optional<String> getBody() {
      return Optional.ofNullable(body);
    }

    @Override
    public HttpResponseMessage.Builder createResponseBuilder(HttpStatus status) {
      return new Response(status);
    }

    @Override
    public HttpResponseMessage.Builder createResponseBuilder(HttpStatusType status) {
      return new Response(status);
    }
  }

  /** The response an HTTP entry point builds with the worker's builder. */
  static final class Response implements HttpResponseMessage, HttpResponseMessage.Builder {
    private HttpStatusType status;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private Object body;

    Response(HttpStatusType status) {
      this.status = status;
    }

    @Override
    public HttpResponseMessage.Builder status(HttpStatusType newStatus) {
      this.status = newStatus;
      return this;
    }

    @Override
    public HttpResponseMessage.Builder header(String key, String value) {
      headers.put(key, value);
      return this;
    }

    @Override
    public HttpResponseMessage.Builder body(Object newBody) {
      this.body = newBody;
      return this;
    }

    @Override
    public HttpResponseMessage build() {
      return this;
    }

    @Override
    public HttpStatusType getStatus() {
      return status;
    }

    @Override
    public String getHeader(String key) {
      return headers.get(key);
    }

    @Override
    public Object getBody() {
      return body;
    }

    /**
     * @return the headers set by the entry point
     */
    Map<String, String> headers() {
      return Map.copyOf(headers);
    }
  }
}
