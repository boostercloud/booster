export type TokenVerifierConfig = {
  issuer?: string
  rolesClaim?: string
  extraValidation?: (jwtToken: Record<string, unknown>, rawToken: string) => Promise<void>
} & (
  | {
      jwksUri: string
    }
  | {
      publicKey: string | { algorithm: string; payload: string }
    }
  | {
      decryptionKey: Uint8Array
    }
)
