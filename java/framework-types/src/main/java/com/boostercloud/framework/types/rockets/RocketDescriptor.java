package com.boostercloud.framework.types.rockets;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A rocket used by the application ({@code config.rockets}). Port of {@code RocketDescriptor}. {@code packageName} is
 * the Java package of the rocket implementation for the current provider; the infrastructure rocket is loaded by
 * {@code RocketLoader} from {@code <packageName>.Rocket}.
 *
 * @param packageName the Java package of the rocket
 * @param parameters rocket-specific parameters (a raw value or any object)
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record RocketDescriptor(String packageName, Object parameters) {}
