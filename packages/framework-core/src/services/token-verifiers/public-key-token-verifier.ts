import { TokenVerifier, UserEnvelope } from '@boostercloud/framework-types'
import { verifyJWT } from '.'

export class PublicKeyTokenVerifier implements TokenVerifier {
  public constructor(
    readonly issuer: string,
    readonly publicKeyResolver: Promise<string>,
    readonly rolesClaim?: string
  ) {}

  public async verify(token: string): Promise<UserEnvelope> {
    const key = await this.publicKeyResolver
    return verifyJWT(token, this.issuer, key, this.rolesClaim)
  }
}
