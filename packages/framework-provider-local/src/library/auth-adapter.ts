import { UUID, UserEnvelope } from '@boostercloud/framework-types'
import { Logger } from '@boostercloud/framework-types/dist'
import { UserRegistry } from '../services'

export interface User {
  username: string
  password: string
  userAttributes: {
    roles: Array<string>
  }
  token: UUID
  confirmed: boolean
}

export type LoginCredentials = Pick<User, 'username' | 'password'>
export type SignUpUser = Pick<User, 'username' | 'password' | 'userAttributes'>
export type RegisteredUser = Pick<User, 'username' | 'password' | 'userAttributes' | 'confirmed'>
export type AuthenticatedUser = Pick<User, 'username' | 'token'>

export function authorizeRequest(_userRegistry: UserRegistry, _request: any, _logger: Logger): any {
  return {}
}

export function rawSignUpDataToUserEnvelope(rawMessage: SignUpUser): UserEnvelope {
  return { email: rawMessage.username, roles: rawMessage.userAttributes.roles }
}
