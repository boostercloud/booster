package com.boostercloud.integration.helper;

import static com.boostercloud.integration.harness.Faker.internet;

/**
 * Port of {@code integration/helper/auth-helper.ts}. Tokens are minted by the application tester's
 * {@code TokenHelper} ({@code applicationUnderTest.token().forUser(email, role, options)}).
 */
public final class AuthHelper {

  private AuthHelper() {}

  /**
   * @return 8 random characters followed by {@code Passw0rd!}, which meets the usual complexity rules
   */
  public static String createPassword() {
    return internet.password(8) + "Passw0rd!";
  }
}
