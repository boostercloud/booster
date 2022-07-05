import { UserEnvelope } from '../envelope'

interface TokenVerierCommon {
  readonly issuer: string
  readonly rolesClaim?: string
  readonly extraValidation?: (jwtToken: Record<string, unknown>, rawToken: string) => Promise<void>

  verify?(token: string): Promise<UserEnvelope>
}

export interface JwskUriTokenVerifier extends TokenVerierCommon {
  readonly jwksUri: string
}

export interface PublicKeyTokenVerifier extends TokenVerierCommon {
  readonly publicKey: Promise<string>
}

export const isJwskUriTokenVerifier = (b: TokenVerifier): b is JwskUriTokenVerifier => {
  return (b as JwskUriTokenVerifier).jwksUri !== undefined
}

export const isPublicKeyTokenVerifier = (b: TokenVerifier): b is PublicKeyTokenVerifier => {
  return (b as PublicKeyTokenVerifier).publicKey !== undefined
}

export type TokenVerifier = JwskUriTokenVerifier | PublicKeyTokenVerifier
