import { UserEnvelope } from '../envelope'
import { JWTHeaderParameters, JWTPayload } from 'jose'

export interface DecodedToken {
  payload: JWTPayload
  header: JWTHeaderParameters
}

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
