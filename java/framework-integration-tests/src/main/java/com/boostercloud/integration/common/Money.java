package com.boostercloud.integration.common;

/**
 * An amount of money ({@code src/common/money.ts}); GraphQL types {@code Money} and {@code MoneyInput}.
 *
 * @param cents the amount in cents
 * @param currency the currency
 */
public record Money(double cents, String currency) {}
