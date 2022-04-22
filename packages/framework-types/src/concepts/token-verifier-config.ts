export type TokenVerifierConfig = {
  issuer: string
  jwksUri?: string
  publicKey?: Promise<string>
  rolesClaim?: string
  extraValidation?: (jwtToken: Record<string, unknown>, rawToken: string) => Promise<void>
}
