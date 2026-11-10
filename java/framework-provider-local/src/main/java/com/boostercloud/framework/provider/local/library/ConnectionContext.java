package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventType;

/**
 * The websocket connection a message belongs to. Port of {@code interface ConnectionContext} of
 * {@code web-socket-server-adapter.ts}.
 *
 * @param connectionId the server-generated connection id
 * @param eventType {@code CONNECT}, {@code MESSAGE} or {@code DISCONNECT}
 */
public record ConnectionContext(UUID connectionId, EventType eventType) {}
