import { UserEnvelope } from '@boostercloud/framework-types'
import { UserRegistry } from '../services'

export async function fetchUserFromRequest(
  request: { headers: Record<string, string> },
  userRegistry: UserRegistry
): Promise<UserEnvelope | undefined> {
  const accessToken = getTokenFromRequest(request)
  if (!accessToken) {
    return undefined
  }
  return userRegistry.getAuthenticatedUser(accessToken)
}

function getTokenFromRequest(request: { headers: Record<string, string> }): string | undefined {
  return request.headers['authorization']?.replace('Bearer ', '')
}
