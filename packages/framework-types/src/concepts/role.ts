import { Class } from '../typelevel'

// eslint-disable-next-line @typescript-eslint/no-empty-interface
export interface RoleInterface {}

export interface RoleMetadata {
  readonly authentication: AuthenticationMetadata
}

export interface AuthenticationMetadata {
  readonly signUpMethods: 'email' | 'phone' | ['email', 'phone'] | ['phone', 'email'] | []
}

export interface RoleAccess {
  readonly authorize: 'all' | Array<Class<RoleInterface>>
}
