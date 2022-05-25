import { TokenVerifier, UserEnvelope, isJwskUriTokenVerifier } from '@boostercloud/framework-types'

import * as jwksRSA from 'jwks-rsa'
import * as jwt from 'jsonwebtoken'

const validateRoleFormat = (role: unknown): void => {
  if (typeof role !== 'string') {
    throw new Error(`Invalid role format ${role}. Valid format are Array<string> or string`)
  }
}

const rolesFromTokenRole = (role: unknown): Array<string> => {
  const roleValues = []
  if (Array.isArray(role)) {
    role.forEach((r) => validateRoleFormat(r))
    roleValues.push(...role)
  } else {
    validateRoleFormat(role)
    roleValues.push((role as string)?.trim() ?? '')
  }
  return roleValues
}

export const sanitizeToken = (token: string): string => {
  return token.replace('Bearer ', '')
}

export const getKey = (context: TokenVerifier) => (header: jwt.JwtHeader, callback: jwt.SigningKeyCallback): void => {
  if (!header.kid) {
    callback(new Error('JWT kid not found'))
    return
  }
  if (!isJwskUriTokenVerifier(context)) {
    callback(new Error('JwskUri not found in context'))
    return
  }

  const jwksUri = context.jwksUri
  const client = jwksRSA({
    jwksUri,
    cache: true,
    cacheMaxAge: 15 * 60 * 1000, // 15 Minutes, at least to be equal to AWS max lambda limit runtime
  })

  client.getSigningKey(header.kid, function (err: Error | null, key: jwksRSA.SigningKey) {
    if (err) {
      callback(err, '')
      return
    }
    const signingKey = key.getPublicKey()
    callback(null, signingKey)
  })
}

export const tokenToUserEnvelope = (decodedToken: any, rolesClaim?: string): UserEnvelope => {
  const payload = decodedToken.payload
  const username = payload?.email || payload?.phone_number || payload.sub
  const id = payload.sub
  const role = payload[rolesClaim || 'custom:role']
  const roleValues = rolesFromTokenRole(role)

  return {
    id,
    username,
    roles: roleValues,
    claims: decodedToken.payload,
    header: decodedToken.header,
  }
}

export const validateToken = (
  token: string,
  key: jwt.Secret | jwt.GetPublicKeyOrSecret,
  options?: jwt.VerifyOptions,
  extraValidation?: TokenVerifier['extraValidation']
): Promise<any> => {
  return new Promise((resolve, reject) => {
    jwt.verify(token, key, options, (err, decoded) => {
      if (err) {
        return reject(err)
      }

      const jwtToken = decoded as any
      (extraValidation ?? (() => Promise.resolve()))(jwtToken, token)
          .then(() => {
            resolve(jwtToken)
          })
          .catch(reject)
    })
  })
}
