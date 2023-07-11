import {
  EventSearchRequest,
  ReadModelRequestEnvelope,
  UserEnvelope,
  CommandEnvelope,
  QueryEnvelope,
  HealthEnvelope,
} from '../envelope'
import { ReadModelInterface } from './read-model'

export type CommandAuthorizer = (currentUser?: UserEnvelope, commandEnvelope?: CommandEnvelope) => Promise<void>

export type QueryAuthorizer = (currentUser?: UserEnvelope, queryEnvelope?: QueryEnvelope) => Promise<void>

export type HealthAuthorizer = (currentUser?: UserEnvelope, healthEnvelope?: HealthEnvelope) => Promise<void>

export type ReadModelAuthorizer = (
  currentUser?: UserEnvelope,
  readModelRequestEnvelope?: ReadModelRequestEnvelope<ReadModelInterface>
) => Promise<void>

export type EventStreamAuthorizer = (
  currentUser?: UserEnvelope,
  eventSearchRequest?: EventSearchRequest
) => Promise<void>
