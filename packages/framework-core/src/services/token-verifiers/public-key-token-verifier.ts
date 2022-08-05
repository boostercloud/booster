import { RoleBasedTokenVerifier } from './role-based-token-verifier'
import { importSPKI, jwtVerify } from 'jose'
import { DecodedToken } from '@boostercloud/framework-types'

export class PublicKeyTokenVerifier extends RoleBasedTokenVerifier {
  public constructor(
    issuer: string,
    readonly publicKeyResolver: Promise<string>,
    rolesClaim?: string,
    readonly algorithm: string = 'RS256'
  ) {
    super(issuer, rolesClaim)
  }

  public async verify(token: string): Promise<DecodedToken> {
    const key = await this.publicKeyResolver
    const publicKey = await importSPKI(key, this.algorithm)
    return await jwtVerify(token, publicKey, { issuer: this.issuer })
  }
}
