package com.boostercloud.framework.types.envelope;

/**
 * Port of {@code EventType = 'CONNECT' | 'MESSAGE' | 'DISCONNECT'} (websocket lifecycle; HTTP is always
 * {@code MESSAGE}).
 */
public enum EventType {
  /** A websocket connection opened. */
  CONNECT,
  /** A message (every HTTP request, or a websocket message). */
  MESSAGE,
  /** A websocket connection closed. */
  DISCONNECT
}
