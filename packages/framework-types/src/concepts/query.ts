import { Class } from '../typelevel'
import { PropertyMetadata } from '@boostercloud/metadata-booster'
import { CommandAuthorizer } from './.'
export type QueryInput = Record<string, any>
export interface QueryInterface<TQuery = unknown, THandleResult = unknown> extends Class<TQuery> {
  handle(query: TQuery): Promise<THandleResult>
}

export interface QueryMetadata<TCommand = unknown> {
  readonly class: QueryInterface<TCommand>
  readonly properties: Array<PropertyMetadata>
  readonly methods: Array<PropertyMetadata>
  readonly authorizer: CommandAuthorizer
}
