import { Class } from '../typelevel'
import { PropertyMetadata } from '@boostercloud/metadata-booster'
import { QueryAuthorizer, QueryFilterHooks } from '.'
import { FilterFor } from '../searcher'

export type QueryResult = Record<string, any>

export interface QueryInterface<TQuery = unknown> extends Class<TQuery> {
  // The query's return type is `unknown` because the QueryInterface type specifies the
  // structure of the class, rather than the instance of the querys, which is what
  // arrives to the `handle` static method.
  handle(filter: Record<string, FilterFor<TQuery>>): Promise<TQuery>
}

// We set the TQuery type to `unknown` because at the time of execution of the
// query handlers, we don't really know what's the type, nor we do care about it.
// The type correctness is ensured by the decorator, which ensures all of this.
export interface QueryMetadata<TQuery = unknown> {
  // For the class, we care that it has the static methods specified by QueryInterface
  // and it has at least the properties of a class (like name, constructor, etc...)
  // We don't care about the properties of the instance, so we set the type parameter of
  // Class to unknown.
  readonly class: QueryInterface<TQuery>
  readonly properties: Array<PropertyMetadata>
  readonly methods: Array<PropertyMetadata>
  readonly authorizer: QueryAuthorizer
  readonly before: NonNullable<QueryFilterHooks['before']>
}
