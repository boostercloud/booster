import { TokenVerifier, UserEnvelope } from '@boostercloud/framework-types'
import { getJwksClient, getKeyWithClient, verifyJWT } from '.'

/**
 * Environment variables that are used to configure a default JWKs URI Token Verifier
 *
 * @deprecated Explicitly initialize the JWKs URI Token Verifier in the project config.
 */
export const JWT_ENV_VARS = {
  BOOSTER_JWT_ISSUER: 'BOOSTER_JWT_ISSUER',
  BOOSTER_JWKS_URI: 'BOOSTER_JWKS_URI',
  BOOSTER_ROLES_CLAIM: 'BOOSTER_ROLES_CLAIM',
}

export class JwskUriTokenVerifier implements TokenVerifier {
  public constructor(readonly issuer: string, readonly jwksUri: string, readonly rolesClaim?: string) {}

  public async verify(token: string): Promise<UserEnvelope> {
    const client = getJwksClient(this.jwksUri)
    const key = getKeyWithClient.bind(this, client)
    return verifyJWT(token, this.issuer, key, this.rolesClaim)
  }
}
