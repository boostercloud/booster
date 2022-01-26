export type TokenVerifierConfig = {
  issuer: string
  jwksUri?: string
  publicKey?: string
  rolesClaim?: string
  extraValidation?: (jwtToken: Record<string, unknown>, rawToken: string) => void
}
