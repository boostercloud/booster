/* eslint-disable @typescript-eslint/camelcase */
import {
  BoosterConfig,
  Class,
  Logger,
  UserEnvelope,
  RoleInterface,
  RoleAccess,
  InvalidParameterError,
} from '@boostercloud/framework-types'

import * as jwksRSA from 'jwks-rsa'
import * as jwt from 'jsonwebtoken'
import * as validator from 'validator'

export class BoosterAuth {
  public static checkSignUp(rawMessage: unknown, config: BoosterConfig, logger: Logger): unknown {
    const userEnvelope = config.provider.auth.rawToEnvelope(rawMessage)
    logger.info('User envelope: ', userEnvelope)

    const roleName = userEnvelope.role
    if (roleName) {
      const roleMetadata = config.roles[userEnvelope.role]
      if (!roleMetadata) {
        throw new InvalidParameterError(`Unknown role ${roleName}`)
      }

      const authMetadata = roleMetadata.auth
      if (!authMetadata?.signUpMethods?.length) {
        throw new InvalidParameterError(
          `User with role ${roleName} can't sign up by themselves. Choose a different role or contact and administrator`
        )
      }

      const signUpOptions = authMetadata.signUpMethods
      const username = userEnvelope.username

      if (validator.default.isEmail(username) && !signUpOptions.includes('email')) {
        throw new InvalidParameterError(
          `User with role ${roleName} can't sign up with an email, a phone number is expected`
        )
      }

      if (!validator.default.isEmail(username) && !signUpOptions.includes('phone')) {
        throw new InvalidParameterError(
          `User with role ${roleName} can't sign up with a phone number, an email is expected`
        )
      }
    }

    return config.provider.auth.handleSignUpResult(config, rawMessage, userEnvelope)
  }

  public static isUserAuthorized(authorizedRoles: RoleAccess['authorize'], user?: UserEnvelope): boolean {
    // Is the current user authorized?
    if (authorizedRoles == 'all') {
      return true // Sure! This is public access
    }

    if (!user) {
      return false // Nope! No user signed-in and this is NOT public access
    }

    // Okay, user is signed-in and this is private access.
    // Check the roles then
    return userHasSomeRole(user, authorizedRoles)
  }

  public static async verifyToken(config: BoosterConfig, token: string): Promise<UserEnvelope> {
    if (!config.tokenVerifier) {
      throw new Error('Token verifier not configured')
    }
    const { issuer, jwksUri } = config.tokenVerifier

    const client = jwksRSA({
      jwksUri,
    })

    const verifyOptions: jwt.VerifyOptions = {
      algorithms: ['RS256'],
      issuer,
    }

    return new Promise((resolve, reject) => {
      const getKey = (header: jwt.JwtHeader, callback: jwt.SigningKeyCallback): void => {
        if (!header.kid) {
          callback(new Error('JWT kid not found.'))
          return
        }

        client.getSigningKey(header.kid, function(err: Error | null, key: jwksRSA.SigningKey) {
          if (err) {
            // This callback doesn't accept null so an empty string is enough here
            callback(err, '')
            return
          }
          const signingKey = key.getPublicKey()
          callback(null, signingKey)
        })
      }

      token = BoosterAuth.sanitizeToken(token)
      jwt.verify(token, getKey, verifyOptions, (err: Error | null, decoded: object | undefined) => {
        if (err) {
          return reject(err)
        }
        return resolve(BoosterAuth.tokenToUserEnvelope(decoded))
      })
    })
  }

  private static tokenToUserEnvelope(decodedToken: any): UserEnvelope {
    const username = decodedToken.email ?? decodedToken.phone_number
    const id = decodedToken.sub
    const role = decodedToken['custom:role']
    return {
      id,
      username,
      role: role?.trim() ?? '',
    }
  }

  private static sanitizeToken(token: string): string {
    return token.replace('Bearer ', '')
  }
}

function userHasSomeRole(user: UserEnvelope, authorizedRoles: Array<Class<RoleInterface>>): boolean {
  return authorizedRoles.some((roleClass) => user.role === roleClass.name)
}
