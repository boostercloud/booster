import { Class } from '../typelevel'

// eslint-disable-next-line @typescript-eslint/no-empty-interface
export interface RoleInterface {}

export interface RoleMetadata {
  readonly auth: AuthMetadata
}

export type SignUpMethod = 'email' | 'phone'

export interface AuthMetadata {
  readonly signUpMethods: Array<SignUpMethod>
}

export interface RoleAccess {
  readonly authorize: 'all' | Array<Class<RoleInterface>>
}
