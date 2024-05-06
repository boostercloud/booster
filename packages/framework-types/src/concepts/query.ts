import { Class } from '../typelevel'
import { PropertyMetadata } from '@boostercloud/metadata-booster'
import { QueryAuthorizer, QueryFilterHooks, UUID } from './.'
import { ContextEnvelope, UserEnvelope } from '../envelope'

export type QueryInput = Record<string, any>

export interface QueryInterface<TQuery = unknown, THandleResult = unknown> extends Class<TQuery> {
  handle(query: TQuery, queryInfo?: QueryInfo): Promise<THandleResult>
}

export interface QueryMetadata<TCommand = unknown> {
  readonly class: QueryInterface<TCommand>
  readonly properties: Array<PropertyMetadata>
  readonly methods: Array<PropertyMetadata>
  readonly authorizer: QueryAuthorizer
  readonly before: NonNullable<QueryFilterHooks['before']>
}

export interface QueryInfo {
  requestID: UUID
  responseHeaders: Record<string, string>
  currentUser?: UserEnvelope
  context?: ContextEnvelope
}
