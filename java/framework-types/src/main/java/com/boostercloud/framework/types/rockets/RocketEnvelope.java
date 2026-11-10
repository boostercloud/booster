package com.boostercloud.framework.types.rockets;

/**
 * Identifies the rocket function a request is for. Port of {@code RocketEnvelope}.
 *
 * @param rocketId the registered rocket function id, or {@code null} when missing
 */
public record RocketEnvelope(String rocketId) {}
