package com.boostercloud.integration.common;

/**
 * A shipping address ({@code src/common/address.ts}); GraphQL types {@code Address} and {@code AddressInput}.
 *
 * @param firstName the first name
 * @param lastName the last name
 * @param country the country
 * @param state the state
 * @param postalCode the postal code
 * @param address the street address
 */
public record Address(
    String firstName, String lastName, String country, String state, String postalCode, String address) {}
