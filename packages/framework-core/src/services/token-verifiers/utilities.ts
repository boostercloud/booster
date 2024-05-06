import { DecodedToken } from '@boostercloud/framework-types'
import * as jwt from 'jsonwebtoken'
import { JwksClient, SigningKey } from 'jwks-rsa'

/**
 * Initializes a jwksRSA client that can be used to get the public key of a JWKS URI using the
 * `getKeyWithClient` function.
 *
 * @param jwksUri The JWKS URI
 * @returns A JwksRSA client
 */
export function getJwksClient(jwksUri: string): JwksClient {
  const jwksRSA = require('jwks-rsa') // Manually loading the default export here to be able to stub it
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
export function getKeyWithClient(client: JwksClient, header: jwt.JwtHeader, callback: jwt.SigningKeyCallback): void {
  if (!header.kid) {
    callback(new Error('JWT kid not found'))
    return
  }
  client.getSigningKey(header.kid, function (err: Error | null, key?: SigningKey) {
    if (err) {
      callback(err)
      return
    }
    callback(null, key?.getPublicKey())
  })
}

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
  key: jwt.Secret | jwt.GetPublicKeyOrSecret
): Promise<DecodedToken> {
  const sanitizedToken = token.replace('Bearer ', '') // Remove the 'Bearer' prefix from the token

  return await new Promise((resolve, reject) => {
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
        if (!decoded) {
          return reject(new Error('The token could not be decoded'))
        }
        return resolve(decoded as DecodedToken)
      }
    )
  })
}
