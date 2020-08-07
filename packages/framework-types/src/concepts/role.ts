import { Class } from '../typelevel'

// eslint-disable-next-line @typescript-eslint/no-empty-interface
export interface RoleInterface {}

export interface RoleMetadata {
  readonly authentication: AuthenticationMetadata
}

export type SignUpMethod = 'email' | 'phone'

export interface AuthenticationMetadata {
  readonly signUpMethods: Array<SignUpMethod>
}

export interface RoleAccess {
  readonly authorize: 'all' | Array<Class<RoleInterface>>
}
