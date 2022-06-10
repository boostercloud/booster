import { UserEnvelope } from '../envelope'

export interface TokenVerifier {
  verify(token: string): Promise<UserEnvelope>
}

/**
 * @deprecated Use an object that matches the `TokenVerifier` interface instead. You can use a `JwskUriTokenVerifier` or `PublicKeyTokenVerifier` instance or a custom implementation.
 */
export type TokenVerifierConfig = {
  issuer: string
  jwksUri?: string
  publicKey?: Promise<string>
  rolesClaim?: string
}
