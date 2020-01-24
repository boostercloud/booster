import { Class } from '../typelevel'

// eslint-disable-next-line @typescript-eslint/no-empty-interface
export interface RoleInterface {}

export interface RoleMetadata {
  readonly allowSelfSignUp: boolean
}

export interface RoleAccess {
  readonly authorize: 'all' | Array<Class<RoleInterface>>
}
