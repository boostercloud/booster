package com.boostercloud.framework.provider.local.infrastructure.web;

import com.boostercloud.framework.types.BoosterJson;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * An {@link HttpResponse} that records what was sent, for unit tests of handlers (the role
 * {@code sinon-express-mock}'s {@code mockRes()} plays in the TypeScript tests).
 */
public class MemoryHttpResponse extends HttpResponse {

  private final ByteArrayOutputStream body = new ByteArrayOutputStream();
  private volatile int sentStatus = -1;
  private volatile List<Map.Entry<String, String>> sentHeaders = List.of();

  /** A response to a non-{@code HEAD} request. */
  public MemoryHttpResponse() {
    this(false);
  }

  /**
   * @param head whether it answers a {@code HEAD} request
   */
  public MemoryHttpResponse(boolean head) {
    super(head);
  }

  @Override
  protected void writeComplete(int status, List<Map.Entry<String, String>> headerList, byte[] bytes) {
    sentStatus = status;
    sentHeaders = List.copyOf(headerList);
    body.writeBytes(bytes);
  }

  @Override
  protected OutputStream openStream(int status, List<Map.Entry<String, String>> headerList) {
    sentStatus = status;
    sentHeaders = List.copyOf(headerList);
    return new FilterOutputStream(body) {
      @Override
      public void write(byte[] bytes, int offset, int length) throws IOException {
        out.write(bytes, offset, length);
      }
    };
  }

  /**
   * @return the status that was sent, or {@code -1} before the response was committed
   */
  public int sentStatus() {
    return sentStatus;
  }

  /**
   * @return the headers that were sent, in order
   */
  public List<Map.Entry<String, String>> sentHeaders() {
    return sentHeaders;
  }

  /**
   * @return the body bytes written so far
   */
  public byte[] bodyBytes() {
    return body.toByteArray();
  }

  /**
   * @return the body as UTF-8 text
   */
  public String bodyAsString() {
    return body.toString(StandardCharsets.UTF_8);
  }

  /**
   * @return the body parsed as JSON (a raw value), or {@code null} for an empty body
   */
  public Object bodyAsJson() {
    String text = bodyAsString();
    return text.isEmpty() ? null : BoosterJson.parse(text);
  }
}
