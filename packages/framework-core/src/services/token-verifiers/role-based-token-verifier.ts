import { DecodedToken, TokenVerifier, UserEnvelope } from '@boostercloud/framework-types'

export const DEFAULT_ROLES_CLAIM = 'custom:role'

const rolesFromTokenRole = (rolesClaim: unknown): Array<string> =>
  (Array.isArray(rolesClaim) ? rolesClaim : [rolesClaim]).map((role: unknown): string => {
    if (typeof role !== 'string') {
      throw new Error(`Invalid role format ${role}. Valid format are Array<string> or string`)
    }
    return role
  })

export abstract class RoleBasedTokenVerifier implements TokenVerifier {
  public constructor(readonly rolesClaim: string = DEFAULT_ROLES_CLAIM) {}

  abstract verify(token: string): Promise<DecodedToken>

  public toUserEnvelope(decodedToken: DecodedToken): UserEnvelope {
    const payload = decodedToken.payload
    const username = payload?.email || payload?.phone_number || payload.sub
    const id = payload.sub
    const roles = rolesFromTokenRole(payload[this.rolesClaim])
    return {
      id,
      username,
      roles,
      claims: decodedToken.payload,
      header: decodedToken.header,
    }
  }
}
