package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

/**
 * The authenticated user of a request, built by a token verifier from the JWT. Port of {@code UserEnvelope}.
 *
 * @param id optional identifier of the user
 * @param username the unique username
 * @param roles names of the roles assigned to the user (compared with role class simple names)
 * @param claims the claims of the JWT payload
 * @param header the JWT header, for further verification
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"id", "username", "roles", "claims", "header"})
public record UserEnvelope(
    String id,
    String username,
    List<String> roles,
    Map<String, Object> claims,
    Map<String, Object> header) {

  /** Canonical constructor; {@code null} roles and claims become empty. */
  public UserEnvelope {
    roles = roles == null ? List.of() : List.copyOf(roles);
    claims = claims == null ? Map.of() : claims;
  }

  /**
   * @param role a role name
   * @return whether the user has that role
   */
  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  /**
   * @return a builder initialised with the values of this record (TypeScript: object spread)
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  /**
   * @return an empty builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder of {@link UserEnvelope}. */
  public static final class Builder {
    private String id;
    private String username;
    private List<String> roles;
    private Map<String, Object> claims;
    private Map<String, Object> header;

    private Builder() {}

    private Builder(UserEnvelope source) {
      this.id = source.id();
      this.username = source.username();
      this.roles = source.roles();
      this.claims = source.claims();
      this.header = source.header();
    }

    /**
     * @param id optional identifier of the user
     * @return this builder
     */
    public Builder id(String id) {
      this.id = id;
      return this;
    }

    /**
     * @param username the unique username
     * @return this builder
     */
    public Builder username(String username) {
      this.username = username;
      return this;
    }

    /**
     * @param roles names of the roles assigned to the user (compared with role class simple names)
     * @return this builder
     */
    public Builder roles(List<String> roles) {
      this.roles = roles;
      return this;
    }

    /**
     * @param claims the claims of the JWT payload
     * @return this builder
     */
    public Builder claims(Map<String, Object> claims) {
      this.claims = claims;
      return this;
    }

    /**
     * @param header the JWT header, for further verification
     * @return this builder
     */
    public Builder header(Map<String, Object> header) {
      this.header = header;
      return this;
    }

    /**
     * @return the record
     */
    public UserEnvelope build() {
      return new UserEnvelope(id, username, roles, claims, header);
    }
  }
}
