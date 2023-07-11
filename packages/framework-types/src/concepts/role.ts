import { Class } from '../typelevel'
import {
  CommandAuthorizer,
  EventStreamAuthorizer,
  HealthAuthorizer,
  QueryAuthorizer,
  ReadModelAuthorizer,
} from './authorizers'

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
export interface CommandRoleAccess {
  readonly authorize?: 'all' | Array<Class<RoleInterface>> | CommandAuthorizer
}

export interface QueryRoleAccess {
  readonly authorize?: 'all' | Array<Class<RoleInterface>> | QueryAuthorizer
}

export interface HealthRoleAccess {
  authorize?: 'all' | Array<Class<RoleInterface>> | HealthAuthorizer
}

export interface ReadModelRoleAccess {
  readonly authorize?: 'all' | Array<Class<RoleInterface>> | ReadModelAuthorizer
}

export interface EventStreamRoleAccess {
  readonly authorizeReadEvents: 'all' | Array<Class<RoleInterface>> | EventStreamAuthorizer
}
