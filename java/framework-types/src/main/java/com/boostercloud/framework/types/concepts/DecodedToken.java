package com.boostercloud.framework.types.concepts;

import java.util.Map;

/**
 * A verified JWT, port of {@code DecodedToken}: the JOSE header (with {@code kid}) and the payload claims (with
 * {@code sub}, optional {@code email} and {@code phone_number}), both as open raw maps.
 *
 * @param header the token header
 * @param payload the token claims
 */
public record DecodedToken(Map<String, Object> header, Map<String, Object> payload) {
  /** Canonical constructor; {@code null} maps become empty. */
  public DecodedToken {
    header = header == null ? Map.of() : header;
    payload = payload == null ? Map.of() : payload;
  }

  /**
   * @return the {@code kid} header, or {@code null}
   */
  public String kid() {
    return header.get("kid") instanceof String kid ? kid : null;
  }

  /**
   * @return the {@code sub} claim, or {@code null}
   */
  public String sub() {
    return payload.get("sub") instanceof String sub ? sub : null;
  }

  /**
   * @return the {@code email} claim, or {@code null}
   */
  public String email() {
    return payload.get("email") instanceof String email ? email : null;
  }

  /**
   * @return the {@code phone_number} claim, or {@code null}
   */
  public String phoneNumber() {
    return payload.get("phone_number") instanceof String phone ? phone : null;
  }
}
