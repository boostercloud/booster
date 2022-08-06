import { DecodedToken, TokenVerifier, UserEnvelope, UUID } from '@boostercloud/framework-types'

export const DEFAULT_ROLES_CLAIM = 'custom:role'

function rolesFromTokenRole(rolesClaim: unknown): Array<string> {
  if (!rolesClaim) {
    return []
  }
  const roles = Array.isArray(rolesClaim) ? rolesClaim : [rolesClaim]
  return roles.map((role: unknown): string => {
    if (typeof role !== 'string') {
      throw new Error(`Invalid role format ${role}. Valid format are Array<string> or string`)
    }
    return role
  })
}

export abstract class RoleBasedTokenVerifier implements TokenVerifier {
  public constructor(readonly issuer: string, readonly rolesClaim: string = DEFAULT_ROLES_CLAIM) {}

  abstract verify(token: string): Promise<DecodedToken>

  public toUserEnvelope(decodedToken: DecodedToken): UserEnvelope {
    const { payload, header } = decodedToken
    const id = payload.sub ?? (UUID.generate() as string)
    const username = (payload.email ?? payload.phone_number ?? payload.sub ?? id) as string
    const roles = rolesFromTokenRole(payload[this.rolesClaim])
    return {
      id,
      username,
      roles,
      claims: payload,
      header,
    }
  }
}
