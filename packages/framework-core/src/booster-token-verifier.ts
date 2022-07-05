import {
  NotAuthorizedError,
  BoosterTokenExpiredError,
  BoosterTokenNotBeforeError,
  UserEnvelope,
  BoosterConfig,
} from '@boostercloud/framework-types'
import { NotBeforeError, TokenExpiredError } from 'jsonwebtoken'

export class BoosterTokenVerifier {
  public constructor(private config: BoosterConfig) {}

  public async verify(token: string): Promise<UserEnvelope> {
    const results = await Promise.allSettled(
      this.config.tokenVerifiers.map((tokenVerifier) => tokenVerifier.verify(token))
    )
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
