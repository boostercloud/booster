import { UserEnvelope } from '../envelope'
import { JWTVerifyResult } from 'jose'

export type DecodedToken = JWTVerifyResult

export interface TokenVerifier {
  /**
   * Verify asd deserialize a stringified token with this token verifier.
   * @param token The token to verify
   */
  verify(token: string): Promise<DecodedToken>
  /**
   * Build a valid `UserEnvelope` from a decoded token.
   * @param decodedToken The decoded token
   */
  toUserEnvelope(decodedToken: DecodedToken): UserEnvelope
}
