import { Class } from '../typelevel'
import { FilterFor } from '../searcher'
import { UserEnvelope } from '../envelope'
import { ReadModelInterface } from './read-model'

// eslint-disable-next-line @typescript-eslint/no-empty-interface
export interface RoleInterface {}

export interface RoleMetadata {
  readonly auth: AuthMetadata
}

export type SignUpMethod = 'email' | 'phone'

export interface AuthMetadata {
  readonly signUpMethods?: Array<SignUpMethod>
  readonly skipConfirmation?: boolean
}

export type BeforeFunction = (
  filter: FilterFor<Class<ReadModelInterface>>,
  currentUser?: UserEnvelope
) => FilterFor<Class<ReadModelInterface>>

export interface RoleAccess {
  readonly authorize: 'all' | Array<Class<RoleInterface>>
  readonly before?: Array<BeforeFunction>
}
