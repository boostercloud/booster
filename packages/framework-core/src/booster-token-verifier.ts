/* eslint-disable @typescript-eslint/camelcase */
import { BoosterConfig, UserEnvelope } from '@boostercloud/framework-types'

import * as jwksRSA from 'jwks-rsa'
import * as jwt from 'jsonwebtoken'

export class BoosterTokenVerifier {
  private client?: jwksRSA.JwksClient
  private options?: jwt.VerifyOptions

  public constructor(private config: BoosterConfig) {
    if (this.config.tokenVerifier) {
      if (this.config.tokenVerifier.jwksUri) {
        this.client = jwksRSA({
          jwksUri: this.config.tokenVerifier.jwksUri,
          cache: true,
          cacheMaxAge: 900000, // 15 Minutes, at least to be equal to AWS max lambda limit runtime
        })
      }

      this.options = {
        algorithms: ['RS256'],
        issuer: this.config.tokenVerifier.issuer,
      }
    }
  }

  public async verify(token: string): Promise<UserEnvelope> {
    return new Promise((resolve, reject) => {
      const getKey = (header: jwt.JwtHeader, callback: jwt.SigningKeyCallback): void => {
        if (!header.kid) {
          callback(new Error('JWT kid not found'))
          return
        }
        this.client?.getSigningKey(header.kid, function(err: Error | null, key: jwksRSA.SigningKey) {
          if (err) {
            // This callback doesn't accept null so an empty string is enough here
            callback(err, '')
            return
          }
          const signingKey = key.getPublicKey()
          callback(null, signingKey)
        })
      }

      let keys: jwt.Secret | jwt.GetPublicKeyOrSecret = getKey
      if (!this.client) {
        if (this.config.tokenVerifier?.publicKey) {
          keys = this.config.tokenVerifier.publicKey
        } else {
          throw new Error('Token verifier not well configured')
        }
      }

      token = this.sanitizeToken(token)
      jwt.verify(token, keys, this.options, (err: Error | null, decoded: object | undefined) => {
        if (err) {
          return reject(err)
        }
        return resolve(this.tokenToUserEnvelope(decoded))
      })
    })
  }

  private tokenToUserEnvelope(decodedToken: any): UserEnvelope {
    const username = decodedToken.email ?? decodedToken.phone_number
    const id = decodedToken.sub
    const role = decodedToken['custom:role']
    return {
      id,
      username,
      role: role?.trim() ?? '',
    }
  }

  private sanitizeToken(token: string): string {
    return token.replace('Bearer ', '')
  }
}
