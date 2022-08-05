import * as fs from 'fs'
import * as path from 'path'
import { SignJWT, KeyLike, importPKCS8 } from 'jose'

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
  private privateKey: string

  constructor() {
    this.privateKey = fs.readFileSync(path.join(__dirname, '..', 'keys', 'private.key')).toString('utf8')
  }

  public async forUser(email: string, role?: string, tokenOptions?: TokenOptions): Promise<string> {
    const keyid = 'booster'
    const issuer = 'booster'

    const payload = {
      id: email,
      email,
      ...tokenOptions?.customClaims,
    }
    const rolesClaim = role ? { 'booster:role': role } : {}

    let tokenBuilder = new SignJWT({ ...payload, ...rolesClaim })
      .setProtectedHeader({
        alg: 'RS256',
        kid: keyid,
      })
      .setIssuedAt()
      .setIssuer(issuer)
      .setSubject(email)

    if (tokenOptions?.expiresIn !== undefined) {
      tokenBuilder = tokenBuilder.setExpirationTime(tokenOptions.expiresIn)
    }

    if (tokenOptions?.notBefore !== undefined) {
      tokenBuilder = tokenBuilder.setNotBefore(tokenOptions.notBefore)
    }

    const key: KeyLike = await importPKCS8(this.privateKey, 'RS256')
    return await tokenBuilder.sign(key)
  }
}
