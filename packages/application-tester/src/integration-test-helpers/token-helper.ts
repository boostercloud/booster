import * as fs from 'fs'
import * as jwt from 'jsonwebtoken'
import * as path from 'path'

/**
 * This helper will create a valid token using a real private key for testing
 * The keyset file is expected to be located in "<package root>/keys/private.key file"
 */
export class TokenHelper {
  private privateKey: Buffer
  constructor() {
    this.privateKey = fs.readFileSync(path.join(__dirname, '..', 'keys', 'private.key'))
  }
  public forUser(email: string, role: string, expiresIn?: number, notBefore?: number): string {
    const keyid = 'booster'
    const issuer = 'booster'
    const options = {
      algorithm: 'RS256',
      subject: email,
      issuer,
      keyid,
    } as jwt.SignOptions
    if (expiresIn || expiresIn === 0) {
      options['expiresIn'] = expiresIn
    }
    if (notBefore) {
      options['notBefore'] = notBefore
    }
    return jwt.sign(
      {
        id: email,
        'booster:role': role,
        email,
      },
      this.privateKey,
      options
    )
  }
}
