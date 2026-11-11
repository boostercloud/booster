package com.boostercloud.framework.provider.local.library;

/**
 * The raw request the websocket server hands to core for every websocket event. Port of
 * {@code interface ExpressWebSocketMessage} of {@code web-socket-server-adapter.ts} (the name is kept for
 * traceability; nothing here depends on Express).
 *
 * <ul>
 *   <li>{@code CONNECT}: {@code { incomingMessage, connectionContext }}
 *   <li>{@code MESSAGE}: {@code { data, incomingMessage, connectionContext }}
 *   <li>{@code DISCONNECT}: {@code { connectionContext }}
 * </ul>
 *
 * @param connectionContext the connection and event type
 * @param incomingMessage the HTTP upgrade request of the connection (its headers), or {@code null} on disconnect
 * @param data the parsed JSON message (a raw value), or {@code null}
 */
public record ExpressWebSocketMessage(ConnectionContext connectionContext, LocalHttpRequest incomingMessage,
    Object data) {}
