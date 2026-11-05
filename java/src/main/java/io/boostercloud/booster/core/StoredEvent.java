package io.boostercloud.booster.core;

public record StoredEvent(long position, long streamVersion, PendingEvent event) {}
