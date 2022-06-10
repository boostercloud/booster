import {
  NotAuthorizedError,
  BoosterTokenExpiredError,
  BoosterTokenNotBeforeError,
  UserEnvelope,
  TokenVerifier,
  BoosterConfig,
} from '@boostercloud/framework-types'
import { NotBeforeError, TokenExpiredError } from 'jsonwebtoken'
import { JwskUriTokenVerifier, PublicKeyTokenVerifier } from './services/token-verifiers/jwsk-token-verifier'

export class BoosterTokenVerifier {
  private tokenVerifiers: Array<TokenVerifier>

  public constructor(config: BoosterConfig) {
    this.tokenVerifiers =
      config.tokenVerifiers?.map((tokenVerifier): TokenVerifier => {
        if ('verify' in tokenVerifier) {
          // Implements the TokenVerifier interface
          return tokenVerifier
        } else if (tokenVerifier.jwksUri) {
          return new JwskUriTokenVerifier(tokenVerifier.issuer, tokenVerifier.jwksUri, tokenVerifier.rolesClaim)
        } else if (tokenVerifier.publicKey) {
          return new PublicKeyTokenVerifier(tokenVerifier.issuer, tokenVerifier.publicKey, tokenVerifier.rolesClaim)
        } else {
          throw new Error('Invalid token verifier configuration')
        }
      }) ?? []
  }

  public async verify(token: string): Promise<UserEnvelope> {
    const results = await Promise.allSettled(this.tokenVerifiers.map((tokenVerifier) => tokenVerifier.verify(token)))
    const winner = results.find((result) => result.status === 'fulfilled')
    if (!winner) return this.rejectVerification(results)
    return Promise.resolve((winner as PromiseFulfilledResult<UserEnvelope>).value)
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
    return this.getErrors(results).filter((result) => result.reason instanceof NotBeforeError)
  }

  private getTokenExpiredErrors(results: Array<PromiseSettledResult<UserEnvelope>>): Array<PromiseRejectedResult> {
    return this.getErrors(results).filter((result) => result.reason instanceof TokenExpiredError)
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
