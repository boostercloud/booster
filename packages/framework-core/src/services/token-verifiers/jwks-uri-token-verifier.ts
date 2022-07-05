import { DecodedToken } from '@boostercloud/framework-types'
import { getJwksClient, getKeyWithClient, verifyJWT } from './utilities'
import { RoleBasedTokenVerifier } from './role-based-token-verifier'

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

export class JwksUriTokenVerifier extends RoleBasedTokenVerifier {
  public constructor(readonly issuer: string, readonly jwksUri: string, rolesClaim?: string) {
    super(rolesClaim)
  }

  public async verify(token: string): Promise<DecodedToken> {
    const client = getJwksClient(this.jwksUri)
    const key = getKeyWithClient.bind(this, client)
    return verifyJWT(token, this.issuer, key)
  }
}
