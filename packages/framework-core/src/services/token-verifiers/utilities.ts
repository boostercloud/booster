import { UserEnvelope } from '@boostercloud/framework-types'
import * as jwksRSA from 'jwks-rsa'
import * as jwt from 'jsonwebtoken'

const defaultRolesClaim = 'custom:role'

/**
 * Creates a valid UserEnvelope from a decoded JWT token. This is an utility function that can be used
 * if you want to create your own TokenVerifier implementation and you implement a custom
 * decoding algorithm.
 *
 * @param decodedToken The decoded JWT token
 * @param rolesClaim The name of the claim containing the roles
 * @returns The `UserEnvelope` object that will be injected in the register object in command handlers
 */
export function tokenToUserEnvelope(decodedToken: any, rolesClaim = defaultRolesClaim): UserEnvelope {
  const payload = decodedToken.payload
  const username = payload?.email || payload?.phone_number || payload.sub
  const id = payload.sub
  const roles = rolesFromTokenRole(payload[rolesClaim])
  return {
    id,
    username,
    roles,
    claims: decodedToken.payload,
    header: decodedToken.header,
  }
}

const rolesFromTokenRole = (rolesClaim: unknown): Array<string> =>
  (Array.isArray(rolesClaim) ? rolesClaim : [rolesClaim]).map((role: unknown): string => {
    if (typeof role !== 'string') {
      throw new Error(`Invalid role format ${role}. Valid format are Array<string> or string`)
    }
    return role
  })

/**
 * Initializes a jwksRSA client that can be used to get the public key of a JWKS URI using the
 * `getKeyWithClient` function.
 *
 * @param jwksUri The JWKS URI
 * @returns A JwksRSA client
 */
export function getJwksClient(jwksUri: string): jwksRSA.JwksClient {
  return jwksRSA({
    jwksUri,
    cache: true,
    cacheMaxAge: 15 * 60 * 1000, // 15 Minutes, at least to be equal to AWS max lambda limit runtime
  })
}

/**
 * Initializes a function that can be used to get the public key from a JWKS URI with the signature
 * required by the `verifyJWT` function. You can create a client using the `getJwksClient` function.
 *
 * @param client A JwksClient instance
 * @param header The JWT header
 * @param callback The callback function that will be called when the public key is ready
 * @returns A function that can be used to get the public key
 */
export function getKeyWithClient(
  client: jwksRSA.JwksClient,
  header: jwt.JwtHeader,
  callback: jwt.SigningKeyCallback
): void {
  if (!header.kid) {
    callback(new Error('JWT kid not found'))
    return
  }
  client.getSigningKey(header.kid, function (err: Error | null, key: jwksRSA.SigningKey) {
    if (err) {
      // This callback doesn't accept null so an empty string is enough here
      callback(err, '')
      return
    }
    const signingKey = key.getPublicKey()
    callback(null, signingKey)
  })
}

/**
 * Strips the `Bearer` prefix from the Authorization header so it can be fed to a TokenVerifier `verify` method.
 * @param authorizationHeader The value of the Authorization header
 * @returns The token part of the Authorization header
 */
export const sanitizeAuthorizationHeader = (authorizationHeader: string): string =>
  authorizationHeader.replace('Bearer ', '')

/**
 * Verifies a JWT token using a key or key resolver function and returns a Booster UserEnvelope.
 *
 * @param token The token to verify
 * @param issuer The issuer of the token
 * @param key The public key to use to verify the token or a function that will resolve a jwksUri to get the public key. The function can be generated using the `getKeyWithClient` function.
 * @param rolesClaim The name of the claim containing the roles
 * @returns A promise that resolves to the UserEnvelope object
 */
export async function verifyJWT(
  token: string,
  issuer: string,
  key: jwt.Secret | jwt.GetPublicKeyOrSecret,
  rolesClaim?: string
): Promise<UserEnvelope> {
  const sanitizedToken = sanitizeAuthorizationHeader(token)

  const decodedToken = await new Promise((resolve, reject) => {
    jwt.verify(
      sanitizedToken,
      key,
      {
        algorithms: ['RS256'],
        issuer,
        complete: true, // To return headers, payload and other useful token information
      },
      (err, decoded) => {
        if (err) {
          return reject(err)
        }
        return resolve(decoded)
      }
    )
  })

  return tokenToUserEnvelope(decodedToken, rolesClaim)
}
