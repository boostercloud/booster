import { BoosterConfig, TokenVerifierConfig, UserEnvelope } from '@boostercloud/framework-types'
import { JWTVerifyOptions, createRemoteJWKSet, importSPKI, jwtVerify, KeyLike, jwtDecrypt } from 'jose'
import { URL } from 'url'

class TokenVerifierClient {
  private jwks?: ReturnType<typeof createRemoteJWKSet>
  private publicKey?: KeyLike
  private options?: JWTVerifyOptions

  public constructor(private tokenVerifierConfig: TokenVerifierConfig) {
    if ('jwksUri' in this.tokenVerifierConfig) {
      this.jwks = createRemoteJWKSet(new URL(this.tokenVerifierConfig.jwksUri))
    }
    this.options = { issuer: this.tokenVerifierConfig.issuer }
  }

  // https://github.com/panva/jose/blob/main/docs/functions/key_import.importSPKI.md#readme
  private async importKey(): Promise<void> {
    if (!('publicKey' in this.tokenVerifierConfig))
      throw new Error('Cannot import key as publicKey is not defined in config')
    if (typeof this.tokenVerifierConfig.publicKey === 'string') {
      this.publicKey = await importSPKI(this.tokenVerifierConfig.publicKey, 'RS256')
    } else {
      const { payload, algorithm } = this.tokenVerifierConfig.publicKey
      this.publicKey = await importSPKI(payload, algorithm)
    }
  }

  public async verify(token: string): Promise<UserEnvelope> {
    token = this.sanitizeToken(token)
    if (this.jwks) {
      const { payload } = await jwtVerify(token, this.jwks, this.options)
      return this.tokenToUserEnvelope(payload)
    }
    if ('decryptionKey' in this.tokenVerifierConfig) {
      const { payload } = await jwtDecrypt(token, this.tokenVerifierConfig.decryptionKey)
      return this.tokenToUserEnvelope(payload)
    }
    if (!this.publicKey && 'publicKey' in this.tokenVerifierConfig) await this.importKey()
    if (this.publicKey) {
      const { payload } = await jwtVerify(token, this.publicKey, this.options)
      return this.tokenToUserEnvelope(payload)
    }
    throw new Error('Token verifier not well configured')
  }

  private tokenToUserEnvelope(decodedToken: any): UserEnvelope {
    const username = decodedToken?.email || decodedToken?.phone_number || decodedToken.sub
    const id = decodedToken.sub
    const rolesClaim = this.tokenVerifierConfig.rolesClaim || 'custom:role'
    const role = decodedToken[rolesClaim]
    const roleValue = Array.isArray(role) ? role[0] : role

    return {
      id,
      username,
      role: roleValue?.trim() ?? '',
      claims: decodedToken,
    }
  }

  private sanitizeToken(token: string): string {
    return token.replace('Bearer ', '')
  }
}

export class BoosterTokenVerifier {
  private tokenVerifierClients: Array<TokenVerifierClient>

  public constructor(config: BoosterConfig) {
    this.tokenVerifierClients = config.tokenVerifiers.map(
      (tokenVerifierConfig) => new TokenVerifierClient(tokenVerifierConfig)
    )
  }

  public async verify(token: string): Promise<UserEnvelope> {
    const results = await Promise.allSettled(
      this.tokenVerifierClients.map((tokenVerifierClient) => tokenVerifierClient.verify(token))
    )
    const winner = results.find((result) => result.status === 'fulfilled')
    if (winner) return Promise.resolve((winner as PromiseFulfilledResult<UserEnvelope>).value)
    return Promise.reject(new Error(results.map((result) => (result as PromiseRejectedResult).reason).join('\n')))
  }
}
