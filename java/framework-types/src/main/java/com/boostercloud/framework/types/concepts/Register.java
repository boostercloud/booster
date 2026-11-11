package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The object injected into command, event, scheduled-command and data-migration handlers to collect the events
 * (and notifications) they emit. Port of the TypeScript {@code Register} class, with the same constructor order and
 * the same reference semantics:
 *
 * <ul>
 *   <li>{@link #responseHeaders()} is the <em>same mutable map</em> the GraphQL dispatcher sends back (handlers may
 *       add headers to it); it is never copied;
 *   <li>{@link #eventList()} is a mutable list, appended by {@link #events(Object...)} and cleared in place by
 *       {@link #flush()} after the flusher succeeds.
 * </ul>
 *
 * <p>A register belongs to one request and is not thread-safe (TypeScript runs it on a single thread). Events added
 * from another thread while {@link #flush()} is running may be cleared without having been stored, exactly like in
 * TypeScript when a handler does not await {@code flush()}.
 */
public final class Register {

  private final UUID requestID;
  private final Map<String, String> responseHeaders;
  private final FlusherFunction flusher;
  private final UserEnvelope currentUser;
  private final ContextEnvelope context;
  private final List<Object> eventList = new ArrayList<>();

  /**
   * {@code new Register(requestID, responseHeaders, flusher, currentUser?, context?)}.
   *
   * @param requestID id of the request (any string is valid)
   * @param responseHeaders mutable response headers shared with the dispatcher
   * @param flusher function that stores the registered events
   * @param currentUser the user making the request, or {@code null}
   * @param context the request context, or {@code null}
   */
  public Register(
      UUID requestID,
      Map<String, String> responseHeaders,
      FlusherFunction flusher,
      UserEnvelope currentUser,
      ContextEnvelope context) {
    this.requestID = requestID;
    this.responseHeaders = responseHeaders;
    this.flusher = Objects.requireNonNull(flusher, "flusher");
    this.currentUser = currentUser;
    this.context = context;
  }

  /**
   * Constructor without user and context.
   *
   * @param requestID id of the request
   * @param responseHeaders mutable response headers
   * @param flusher function that stores the registered events
   */
  public Register(UUID requestID, Map<String, String> responseHeaders, FlusherFunction flusher) {
    this(requestID, responseHeaders, flusher, null, null);
  }

  /**
   * Constructor without context.
   *
   * @param requestID id of the request
   * @param responseHeaders mutable response headers
   * @param flusher function that stores the registered events
   * @param currentUser the user making the request, or {@code null}
   */
  public Register(UUID requestID, Map<String, String> responseHeaders, FlusherFunction flusher, UserEnvelope currentUser) {
    this(requestID, responseHeaders, flusher, currentUser, null);
  }

  /**
   * Appends events and notifications to {@link #eventList()}, in argument order (no validation, no
   * de-duplication, no copies).
   *
   * @param events event or notification instances
   * @return this register (fluent: {@code register.events(a).events(b, c)})
   */
  public Register events(Object... events) {
    eventList.addAll(Arrays.asList(events));
    return this;
  }

  /**
   * Calls the flusher with this register, then, only if it succeeds, clears {@link #eventList()} in place. If the
   * flusher throws, the exception propagates and the events stay registered.
   */
  public void flush() {
    flusher.flush(this);
    eventList.clear();
  }

  /**
   * @return id of the request
   */
  public UUID requestID() {
    return requestID;
  }

  /**
   * @return the mutable response headers shared with the dispatcher
   */
  public Map<String, String> responseHeaders() {
    return responseHeaders;
  }

  /**
   * @return the current user, or {@code null} for anonymous requests
   */
  public UserEnvelope currentUser() {
    return currentUser;
  }

  /**
   * @return the request context, or {@code null}
   */
  public ContextEnvelope context() {
    return context;
  }

  /**
   * @return the mutable list of registered events and notifications
   */
  public List<Object> eventList() {
    return eventList;
  }

  @Override
  public String toString() {
    return "Register{requestID=" + requestID + ", events=" + eventList.size() + "}";
  }
}
