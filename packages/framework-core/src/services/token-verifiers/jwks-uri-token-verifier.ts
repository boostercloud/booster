import { RoleBasedTokenVerifier } from './role-based-token-verifier'
import { createRemoteJWKSet, jwtVerify } from 'jose'
import { URL } from 'url'
import { DecodedToken } from '@boostercloud/framework-types'

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
  public constructor(
    issuer: string,
    readonly jwksUri: string,
    rolesClaim?: string,
    readonly algorithm: string = 'RS256'
  ) {
    super(issuer, rolesClaim)
  }

  public async verify(token: string): Promise<DecodedToken> {
    const jwks = createRemoteJWKSet(new URL(this.jwksUri))
    const { payload, protectedHeader } = await jwtVerify(token, jwks, {
      issuer: this.issuer,
      algorithms: [this.algorithm],
    })
    return { payload, header: protectedHeader }
  }
}
