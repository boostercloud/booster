package com.boostercloud.framework.core.services.tokenverifiers;

import com.boostercloud.framework.types.concepts.DecodedToken;

/**
 * Port of {@code services/token-verifiers/jwks-uri-token-verifier.ts}: verifies RS256 tokens with the key of the
 * token's {@code kid}, fetched from a JWKS URL. Unlike TypeScript (which creates a client, hence an HTTP request, per
 * verification), the client and its 15-minute key cache live as long as the verifier; results are the same.
 */
public class JwksUriTokenVerifier extends RoleBasedTokenVerifier {

  private final String issuer;
  private final String jwksUri;
  private volatile JwksClient client;

  /**
   * @param issuer the expected {@code iss}
   * @param jwksUri the JWKS URL
   */
  public JwksUriTokenVerifier(String issuer, String jwksUri) {
    this(issuer, jwksUri, null);
  }

  /**
   * @param issuer the expected {@code iss}
   * @param jwksUri the JWKS URL
   * @param rolesClaim the roles claim, {@code null} for {@code custom:role}
   */
  public JwksUriTokenVerifier(String issuer, String jwksUri, String rolesClaim) {
    super(rolesClaim);
    this.issuer = issuer;
    this.jwksUri = jwksUri;
  }

  /**
   * @return the expected issuer
   */
  public String issuer() {
    return issuer;
  }

  /**
   * @return the JWKS URL
   */
  public String jwksUri() {
    return jwksUri;
  }

  @Override
  public DecodedToken verify(String token) {
    JwksClient jwksClient = client();
    KeyResolver key = header -> Utilities.getKeyWithClient(jwksClient, header);
    return Utilities.verifyJWT(token, issuer, key);
  }

  private JwksClient client() {
    JwksClient current = client;
    if (current == null) {
      synchronized (this) {
        if (client == null) {
          client = Utilities.getJwksClient(jwksUri);
        }
        current = client;
      }
    }
    return current;
  }
}
