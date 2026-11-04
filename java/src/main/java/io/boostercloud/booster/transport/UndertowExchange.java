package io.boostercloud.booster.transport;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/** Adapts existing Java HttpHandlers to the HTTP/WebSocket listener without introducing a second port. */
final class UndertowExchange extends HttpExchange {
    private final HttpServerExchange exchange;
    private final Headers request = new Headers();
    private final Headers response = new Headers();
    private final Map<String,Object> attributes = new HashMap<>();
    private InputStream input;
    private OutputStream output;
    private boolean committed;
    private boolean closed;
    UndertowExchange(HttpServerExchange exchange) {
        this.exchange = exchange;
        exchange.startBlocking();
        exchange.getRequestHeaders().forEach(h -> request.put(h.getHeaderName().toString(), new java.util.ArrayList<>(h)));
        input = exchange.getInputStream(); output = exchange.getOutputStream();
    }
    @Override public Headers getRequestHeaders() { return request; }
    @Override public Headers getResponseHeaders() { return response; }
    @Override public URI getRequestURI() {
        return URI.create(exchange.getRequestURI() + (exchange.getQueryString().isEmpty() ? "" : "?" + exchange.getQueryString()));
    }
    @Override public String getRequestMethod() { return exchange.getRequestMethod().toString(); }
    @Override public HttpContext getHttpContext() { return null; }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { output.close(); } catch (IOException ignored) { }
        finally { exchange.endExchange(); }
    }
    @Override public InputStream getRequestBody() { return input; }
    @Override public OutputStream getResponseBody() { return output; }
    @Override public void sendResponseHeaders(int status, long responseLength) throws IOException {
        if (committed) throw new IOException("Response headers already sent");
        committed = true;
        exchange.setStatusCode(status);
        response.forEach((key, values) -> exchange.getResponseHeaders().putAll(new HttpString(key), values));
        if (responseLength > 0) exchange.setResponseContentLength(responseLength);
        else if (responseLength < 0) exchange.setResponseContentLength(0);
    }
    @Override public InetSocketAddress getRemoteAddress() { return exchange.getSourceAddress(); }
    @Override public int getResponseCode() { return committed ? exchange.getStatusCode() : -1; }
    @Override public InetSocketAddress getLocalAddress() { return exchange.getDestinationAddress(); }
    @Override public String getProtocol() { return exchange.getProtocol().toString(); }
    @Override public Object getAttribute(String name) { return attributes.get(name); }
    @Override public void setAttribute(String name, Object value) { attributes.put(name, value); }
    @Override public void setStreams(InputStream input, OutputStream output) {
        if (input != null) this.input = input;
        if (output != null) this.output = output;
    }
    @Override public HttpPrincipal getPrincipal() { return null; }
}
