import * as fs from 'fs'
import * as jwt from 'jsonwebtoken'
import * as path from 'path'

type TokenOptions = {
  expiresIn?: number
  notBefore?: number
  customClaims?: Record<string, any>
}

/**
 * This helper will create a valid token using a real private key for testing
 * The keyset file is expecgted to be located in "<package root>/keys/private.key file"
 */
export class TokenHelper {
  private privateKey: Buffer
  constructor() {
    this.privateKey = fs.readFileSync(path.join(__dirname, '..', 'keys', 'private.key'))
  }
  public forUser(email: string, role: string, tokenOptions?: TokenOptions): string {
    const keyid = 'booster'
    const issuer = 'booster'
    const options = {
      algorithm: 'RS256',
      subject: email,
      issuer,
      keyid,
    } as jwt.SignOptions
    if (tokenOptions?.expiresIn || tokenOptions?.expiresIn === 0) {
      options['expiresIn'] = tokenOptions?.expiresIn
    }
    if (tokenOptions?.notBefore) {
      options['notBefore'] = tokenOptions?.notBefore
    }
    return jwt.sign(
      {
        id: email,
        'booster:role': role,
        email,
        ...tokenOptions?.customClaims,
      },
      this.privateKey,
      options
    )
  }
}
