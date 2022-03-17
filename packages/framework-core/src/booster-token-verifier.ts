import {
  BoosterConfig,
  NotAuthorizedError,
  TokenExpiredOrNotBeforeError,
  TokenVerifierConfig,
  UserEnvelope,
} from '@boostercloud/framework-types'

import * as jwksRSA from 'jwks-rsa'
import * as jwt from 'jsonwebtoken'
import { NotBeforeError, TokenExpiredError } from 'jsonwebtoken'

class TokenVerifierClient {
  private client?: jwksRSA.JwksClient
  private options?: jwt.VerifyOptions

  public constructor(private tokenVerifierConfig: TokenVerifierConfig) {
    if (this.tokenVerifierConfig.jwksUri) {
      this.client = jwksRSA({
        jwksUri: this.tokenVerifierConfig.jwksUri,
        cache: true,
        cacheMaxAge: 15 * 60 * 1000, // 15 Minutes, at least to be equal to AWS max lambda limit runtime
      })
    }

    this.options = {
      algorithms: ['RS256'],
      issuer: this.tokenVerifierConfig.issuer,
      complete: true, // To return headers, payload and other useful token information
    }
  }

  public async verify(token: string): Promise<UserEnvelope> {
    const getKey = (header: jwt.JwtHeader, callback: jwt.SigningKeyCallback): void => {
      if (!header.kid) {
        callback(new Error('JWT kid not found'))
        return
      }
      this.client?.getSigningKey(header.kid, function (err: Error | null, key: jwksRSA.SigningKey) {
        if (err) {
          // This callback doesn't accept null so an empty string is enough here
          callback(err, '')
          return
        }
        const signingKey = key.getPublicKey()
        callback(null, signingKey)
      })
    }

    let key: jwt.Secret | jwt.GetPublicKeyOrSecret = getKey
    if (!this.client) {
      if (this.tokenVerifierConfig.publicKey) {
        key = this.tokenVerifierConfig.publicKey
      } else {
        throw new Error('Token verifier not well configured')
      }
    }

    token = TokenVerifierClient.sanitizeToken(token)

    return new Promise((resolve, reject) => {
      jwt.verify(token, key, this.options, (err, decoded) => {
        if (err) {
          if (err instanceof TokenExpiredError || err instanceof NotBeforeError) {
            return reject(new TokenExpiredOrNotBeforeError(err.message))
          }
          return reject(err)
        }
        const jwtToken = decoded as any
        const extraValidation = this.tokenVerifierConfig?.extraValidation ?? (() => Promise.resolve())
        extraValidation(jwtToken, token)
          .then(() => {
            resolve(this.tokenToUserEnvelope(jwtToken))
          })
          .catch(reject)
      })
    })
  }

  private tokenToUserEnvelope(decodedToken: any): UserEnvelope {
    const payload = decodedToken.payload
    const username = payload?.email || payload?.phone_number || payload.sub
    const id = payload.sub
    const rolesClaim = this.tokenVerifierConfig.rolesClaim || 'custom:role'
    const role = payload[rolesClaim]
    const roleValues = TokenVerifierClient.rolesFromTokenRole(role)
    return {
      id,
      username,
      roles: roleValues,
      claims: decodedToken.payload,
      header: decodedToken.header,
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
    const tokenExpiredOrNotBeforeErrors = results
      .filter((result) => result?.status && result?.status !== 'fulfilled')
      .map((result) => result as PromiseRejectedResult)
      .filter((result) => result.reason instanceof TokenExpiredOrNotBeforeError)
    if (tokenExpiredOrNotBeforeErrors && tokenExpiredOrNotBeforeErrors.length > 0) {
      const reasons = tokenExpiredOrNotBeforeErrors.map((error) => error.reason).join('\n')
      return Promise.reject(new TokenExpiredOrNotBeforeError(reasons))
    }
    return Promise.reject(
      new NotAuthorizedError(results.map((result) => (result as PromiseRejectedResult).reason).join('\n'))
    )
  }
}
