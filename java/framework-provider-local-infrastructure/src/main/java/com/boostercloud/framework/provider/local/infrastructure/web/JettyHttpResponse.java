package com.boostercloud.framework.provider.local.infrastructure.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Response;

/** The {@link HttpResponse} of a Jetty request: writes block the (virtual) request thread. */
final class JettyHttpResponse extends HttpResponse {

  private final Response response;

  JettyHttpResponse(Response response, boolean head) {
    super(head);
    this.response = response;
  }

  private void applyHead(int status, List<Map.Entry<String, String>> headerList) {
    response.setStatus(status);
    HttpFields.Mutable fields = response.getHeaders();
    for (Map.Entry<String, String> header : headerList) {
      fields.add(header.getKey(), header.getValue());
    }
  }

  @Override
  protected void writeComplete(int status, List<Map.Entry<String, String>> headerList, byte[] body) {
    applyHead(status, headerList);
    try {
      Content.Sink.write(response, true, ByteBuffer.wrap(body));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  protected OutputStream openStream(int status, List<Map.Entry<String, String>> headerList) {
    applyHead(status, headerList);
    return Content.Sink.asOutputStream(response);
  }
}
