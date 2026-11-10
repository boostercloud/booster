package com.boostercloud.framework.types.concepts;

/**
 * Extra argument of entity-property projections. Port of {@code ProjectionInfo}.
 *
 * @param reason why the projection runs
 */
public record ProjectionInfo(ProjectionInfoReason reason) {}
