export type TokenVerifierConfig = {
  issuer: string
  jwksUri?: string
  publicKey?: string
  rolesClaim?: string
  extraValidation?: (headers: unknown, payload: unknown) => void
}
