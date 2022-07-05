import { UserEnvelope } from '../envelope'

export interface DecodedToken {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  header: any
  payload: {
    email: string
    phone_number: string
    sub: string
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    [key: string]: any
  }
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
