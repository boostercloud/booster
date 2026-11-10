package com.boostercloud.framework.core.services.tokenverifiers;

import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.concepts.DecodedToken;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Port of {@code services/token-verifiers/public-key-token-verifier.ts}: verifies RS256 tokens with a PEM public key
 * that may be resolved asynchronously (TypeScript {@code Promise<string>}). The key is awaited on every verification;
 * a failed resolution fails every verification with its error.
 */
public class PublicKeyTokenVerifier extends RoleBasedTokenVerifier {

  private final String issuer;
  private final CompletionStage<String> publicKeyResolver;

  /**
   * @param issuer the expected {@code iss}
   * @param publicKeyResolver resolves the PEM public key
   */
  public PublicKeyTokenVerifier(String issuer, CompletionStage<String> publicKeyResolver) {
    this(issuer, publicKeyResolver, null);
  }

  /**
   * @param issuer the expected {@code iss}
   * @param publicKeyResolver resolves the PEM public key
   * @param rolesClaim the roles claim, {@code null} for {@code custom:role}
   */
  public PublicKeyTokenVerifier(String issuer, CompletionStage<String> publicKeyResolver, String rolesClaim) {
    super(rolesClaim);
    this.issuer = issuer;
    this.publicKeyResolver = Objects.requireNonNull(publicKeyResolver, "publicKeyResolver");
  }

  /**
   * @param issuer the expected {@code iss}
   * @param publicKey the PEM public key
   * @param rolesClaim the roles claim, {@code null} for {@code custom:role}
   */
  public PublicKeyTokenVerifier(String issuer, String publicKey, String rolesClaim) {
    this(issuer, CompletableFuture.completedFuture(publicKey), rolesClaim);
  }

  /**
   * @return the expected issuer
   */
  public String issuer() {
    return issuer;
  }

  /**
   * @return the key resolver
   */
  public CompletionStage<String> publicKeyResolver() {
    return publicKeyResolver;
  }

  @Override
  public DecodedToken verify(String token) {
    String key = (String) UserCode.await(publicKeyResolver);
    return Utilities.verifyJWT(token, issuer, (Object) key);
  }
}
