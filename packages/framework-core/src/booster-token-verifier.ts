import {
  BoosterConfig,
  NotAuthorizedError,
  BoosterTokenExpiredError,
  BoosterTokenNotBeforeError,
  TokenVerifierConfig,
  UserEnvelope,
} from '@boostercloud/framework-types'
import {
  JWTVerifyOptions,
  JWTDecryptResult,
  JWTVerifyResult,
  createRemoteJWKSet,
  importSPKI,
  jwtVerify,
  KeyLike,
  jwtDecrypt,
  errors,
} from 'jose'
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
    const publicKey = await Promise.resolve(this.tokenVerifierConfig.publicKey)
    if (typeof publicKey === 'string') {
      this.publicKey = await importSPKI(publicKey, 'RS256')
    } else {
      const { payload, algorithm } = publicKey
      this.publicKey = await importSPKI(payload, algorithm)
    }
  }

  public async verify(token: string): Promise<UserEnvelope> {
    token = TokenVerifierClient.sanitizeToken(token)
    let decodedToken = null
    if (this.jwks) {
      decodedToken = await jwtVerify(token, this.jwks, this.options)
    }
    if ('decryptionKey' in this.tokenVerifierConfig) {
      decodedToken = await jwtDecrypt(token, this.tokenVerifierConfig.decryptionKey)
    }
    if (!this.publicKey && 'publicKey' in this.tokenVerifierConfig) await this.importKey()
    if (this.publicKey) {
      decodedToken = await jwtVerify(token, this.publicKey, this.options)
    }
    if (decodedToken == null) throw new Error('Token verifier not well configured')
    if (this.tokenVerifierConfig?.extraValidation) {
      await this.tokenVerifierConfig?.extraValidation({ ...decodedToken }, token)
    }
    return this.tokenToUserEnvelope(decodedToken)
  }

  private tokenToUserEnvelope({ payload, protectedHeader }: JWTVerifyResult | JWTDecryptResult): UserEnvelope {
    const username = payload.email || payload.phone_number || payload.sub
    if (typeof username !== 'string') throw Error('No username found in token')
    const rolesClaim = this.tokenVerifierConfig.rolesClaim || 'custom:role'
    const role = payload[rolesClaim]
    const roleValues = TokenVerifierClient.rolesFromTokenRole(role)
    return {
      id: payload.sub,
      username,
      roles: roleValues,
      claims: payload,
      header: protectedHeader,
    }
  }

  private static sanitizeToken(token: string): string {
    return token.replace('Bearer ', '')
  }

  private static rolesFromTokenRole(role: unknown): Array<string> {
    const roleValues = []
    if (Array.isArray(role)) {
      role.forEach((r) => TokenVerifierClient.validateRoleFormat(r))
      roleValues.push(...role)
    } else {
      TokenVerifierClient.validateRoleFormat(role)
      roleValues.push((role as string)?.trim() ?? '')
    }
    return roleValues
  }

  private static validateRoleFormat(role: unknown): void {
    if (typeof role !== 'string') {
      throw new Error(`Invalid role format ${role}. Valid format are Array<string> or string`)
    }
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

    return this.rejectVerification(results)
  }

  private rejectVerification(results: Array<PromiseSettledResult<UserEnvelope>>): Promise<UserEnvelope> {
    const tokenExpiredErrors = this.getTokenExpiredErrors(results)
    if (tokenExpiredErrors && tokenExpiredErrors.length > 0) {
      const reasons = BoosterTokenVerifier.joinReasons(tokenExpiredErrors)
      return Promise.reject(new BoosterTokenExpiredError(reasons))
    }

    const tokenNotBeforeErrors = this.getTokenNotBeforeErrors(results)
    if (tokenNotBeforeErrors && tokenNotBeforeErrors.length > 0) {
      const reasons = BoosterTokenVerifier.joinReasons(tokenNotBeforeErrors)
      return Promise.reject(new BoosterTokenNotBeforeError(reasons))
    }

    const reasons = BoosterTokenVerifier.joinReasons(results as Array<PromiseRejectedResult>)
    return Promise.reject(new NotAuthorizedError(reasons))
  }

  private getTokenNotBeforeErrors(results: Array<PromiseSettledResult<UserEnvelope>>): Array<PromiseRejectedResult> {
    return this.getErrors(results).filter(
      (result) => result.reason instanceof errors.JWTClaimValidationFailed && result.reason.claim === 'nbf'
    )
  }

  private getTokenExpiredErrors(results: Array<PromiseSettledResult<UserEnvelope>>): Array<PromiseRejectedResult> {
    return this.getErrors(results).filter((result) => result.reason instanceof errors.JWTExpired)
  }

  private getErrors(results: Array<PromiseSettledResult<UserEnvelope>>): Array<PromiseRejectedResult> {
    return results
      .filter((result) => result?.status && result?.status !== 'fulfilled')
      .map((result) => result as PromiseRejectedResult)
  }

  private static joinReasons(errors: Array<PromiseRejectedResult>): string {
    return errors.map((error) => error.reason).join('\n')
  }
}
