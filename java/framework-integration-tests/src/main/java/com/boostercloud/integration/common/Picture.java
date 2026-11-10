package com.boostercloud.integration.common;

import java.net.URI;

/**
 * A product picture ({@code src/common/picture.ts}). TypeScript uses Node's {@code URL}, an external class exposed as
 * {@code JSON} and serialised as its {@code href}; {@link URI} is the JDK counterpart (also external, serialised as
 * its string).
 *
 * @param baseUrl where the picture lives
 */
public record Picture(URI baseUrl) {}
