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

  public static async verifyToken(config: BoosterConfig, token?: string): Promise<UserEnvelope> {
    if (!config.tokenVerifier) {
      return Promise.reject('Token verifier not configured')
    }
    if (!token) {
      return Promise.reject('Empty token')
    }

    const { issuer, jwksUri } = config.tokenVerifier

    const client = new jwksRSA.JwksClient({
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

        client.getSigningKey(header.kid, function(err: any, key: jwksRSA.SigningKey) {
          if (err) {
            callback(err, '')
            return
          }
          const signingKey = key.getPublicKey()
          callback(null, signingKey)
        })
      }

      jwt.verify(token, getKey, verifyOptions, (err: any, decoded: any) => {
        if (err) {
          reject(err)
          return
        }
        try {
          resolve(BoosterAuth.tokenToUserEnvelope(decoded))
        } catch (err) {
          reject(err)
        }
      })
    })
  }

  private static tokenToUserEnvelope(decodedToken: any): UserEnvelope {
    const { phone_number, email, 'custom:role': role } = decodedToken
    const username = email ? email : phone_number

    return {
      username,
      role: role?.trim() ?? '',
    }
  }
}

function userHasSomeRole(user: UserEnvelope, authorizedRoles: Array<Class<RoleInterface>>): boolean {
  return authorizedRoles.some((roleClass) => user.role === roleClass.name)
}
