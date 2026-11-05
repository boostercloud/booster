package io.boostercloud.booster.core;
import java.util.List;
public class QueryContext {
  protected final Booster runtime;
  private final RequestContext context;
  QueryContext(Booster runtime, RequestContext context) { this.runtime = runtime; this.context = context; }
  public RequestContext context() { return context; }
  public void responseHeader(String name, String value) { context.response().set(name, value); }
  public java.util.Map<String,String> responseHeaders() { return context.response().snapshot(); }
  public <S> S entity(Class<S> type, String id) { return runtime.loadEntity(type, id); }
  public List<StoredEvent> events(Class<?> entityType, String id) { return runtime.history(entityType, id); }
}
