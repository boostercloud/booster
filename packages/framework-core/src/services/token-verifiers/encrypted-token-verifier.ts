import { RoleBasedTokenVerifier } from './role-based-token-verifier'
import { jwtDecrypt, KeyLike } from 'jose'
import { DecodedToken } from '@boostercloud/framework-types'

export class EncryptedTokenVerifier extends RoleBasedTokenVerifier {
  public constructor(
    issuer: string,
    readonly decryptionKeyResolver: Promise<Uint8Array | KeyLike>,
    rolesClaim?: string
  ) {
    super(issuer, rolesClaim)
  }

  public async verify(token: string): Promise<DecodedToken> {
    const decryptionKey = await this.decryptionKeyResolver
    return await jwtDecrypt(token, decryptionKey)
  }
}
