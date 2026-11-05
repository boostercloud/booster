package io.boostercloud.booster.core;

/** A joined projection receives the new and previous entity and the selected read-model identity. */
public record ProjectionContext<S,R>(String id, S entity, S previousEntity, R previous, boolean removed) {}
