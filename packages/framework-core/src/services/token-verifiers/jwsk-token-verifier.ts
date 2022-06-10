import { TokenVerifier, UserEnvelope } from '@boostercloud/framework-types'
import { getJwksClient, getKeyWithClient, verifyJWT } from '.'

export abstract class JwksTokenVerifier implements TokenVerifier {
  public constructor(readonly issuer: string, readonly rolesClaim?: string) {}

  abstract verify(token: string): Promise<UserEnvelope>
}

export class JwskUriTokenVerifier extends JwksTokenVerifier {
  public constructor(readonly issuer: string, readonly jwksUri: string, readonly rolesClaim?: string) {
    super(issuer, rolesClaim)
  }

  public async verify(token: string): Promise<UserEnvelope> {
    const client = getJwksClient(this.jwksUri)
    const key = getKeyWithClient.bind(this, client)
    return verifyJWT(token, this.issuer, key, this.rolesClaim)
  }
}

export class PublicKeyTokenVerifier extends JwksTokenVerifier {
  public constructor(
    readonly issuer: string,
    readonly publicKeyResolver: Promise<string>,
    readonly rolesClaim?: string
  ) {
    super(issuer, rolesClaim)
  }

  public async verify(token: string): Promise<UserEnvelope> {
    const key = await this.publicKeyResolver
    return verifyJWT(token, this.issuer, key, this.rolesClaim)
  }
}
