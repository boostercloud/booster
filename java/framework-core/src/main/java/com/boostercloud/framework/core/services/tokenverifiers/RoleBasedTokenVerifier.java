package com.boostercloud.framework.core.services.tokenverifiers;

import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.types.concepts.DecodedToken;
import com.boostercloud.framework.types.concepts.TokenVerifier;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/token-verifiers/role-based-token-verifier.ts}: a {@link TokenVerifier} that builds the
 * {@link UserEnvelope} from the decoded token: {@code id} = {@code sub}, {@code username} = the first truthy of
 * {@code email}, {@code phone_number}, {@code sub}, {@code roles} from the roles claim (a string or a list of
 * strings; anything else fails with {@code Invalid role format <value>. Valid format are Array<string> or string}),
 * {@code claims} = the payload and {@code header} = the header.
 */
public abstract class RoleBasedTokenVerifier implements TokenVerifier {

  /** {@code DEFAULT_ROLES_CLAIM}. */
  public static final String DEFAULT_ROLES_CLAIM = "custom:role";

  private final String rolesClaim;

  /** Uses {@link #DEFAULT_ROLES_CLAIM}. */
  protected RoleBasedTokenVerifier() {
    this(null);
  }

  /**
   * @param rolesClaim the claim holding the roles; {@code null} for {@link #DEFAULT_ROLES_CLAIM}
   */
  protected RoleBasedTokenVerifier(String rolesClaim) {
    this.rolesClaim = rolesClaim == null ? DEFAULT_ROLES_CLAIM : rolesClaim;
  }

  /**
   * @return the claim holding the roles
   */
  public String rolesClaim() {
    return rolesClaim;
  }

  @Override
  public abstract DecodedToken verify(String token);

  @Override
  public UserEnvelope toUserEnvelope(DecodedToken decodedToken) {
    Map<String, Object> payload = decodedToken.payload();
    Object username = firstTruthy(payload.get("email"), payload.get("phone_number"), payload.get("sub"));
    Object id = payload.get("sub");
    List<String> roles = rolesFromTokenRole(payload.get(rolesClaim));
    return new UserEnvelope(id == null ? null : Js.toString(id), username == null ? null : Js.toString(username), roles,
        decodedToken.payload(), decodedToken.header());
  }

  private static Object firstTruthy(Object... values) {
    for (Object value : values) {
      if (Js.isTruthy(value)) {
        return value;
      }
    }
    return values[values.length - 1];
  }

  /**
   * {@code rolesFromTokenRole(rolesClaim)}.
   *
   * @param rolesClaim the claim value
   * @return the roles ({@code []} for a falsy value)
   * @throws IllegalStateException for a non-string role
   */
  static List<String> rolesFromTokenRole(Object rolesClaim) {
    if (!Js.isTruthy(rolesClaim)) {
      return List.of();
    }
    List<?> roles = rolesClaim instanceof List<?> list ? list : List.of(rolesClaim);
    List<String> result = new ArrayList<>();
    for (Object role : roles) {
      if (!(role instanceof String text)) {
        throw new IllegalStateException(
            "Invalid role format " + Js.toString(role) + ". Valid format are Array<string> or string");
      }
      result.add(text);
    }
    return result;
  }
}
