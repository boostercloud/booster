export type TokenVerifierConfig = {
  issuer?: string
  rolesClaim?: string
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
