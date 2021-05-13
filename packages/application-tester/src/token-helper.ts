import * as fs from 'fs'
import * as jwt from 'jsonwebtoken'
import * as path from 'path'

/**
 * This helper will create a valid token using a real private key for testing
 * The keyset file is expecgted to be located in "<package root>/keys/private.key file"
 */
export class TokenHelper {
  private privateKey: Buffer
  constructor() {
    this.privateKey = fs.readFileSync(path.join(__dirname, '..', 'keys', 'private.key'))
  }
  public forUser(email: string, role: string): string {
    const keyid = 'booster'
    const issuer = 'booster'
    return jwt.sign(
      {
        id: email,
        'booster:role': role,
        email,
      },
      this.privateKey,
      {
        algorithm: 'RS256',
        subject: email,
        issuer,
        keyid,
      }
    )
  }
}
