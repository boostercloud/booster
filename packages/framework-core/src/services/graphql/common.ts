import { GraphQLList, GraphQLScalarType, GraphQLObjectType } from 'graphql/type/definition'
import { AnyClass, PropertyMetadata, UserEnvelope, UUID, GraphQLOperation } from '@boostercloud/framework-types'
import { GraphQLFieldResolver } from 'graphql'
import { ReadModelPubSub } from '../pub-sub/read-model-pub-sub'

export type TargetTypesMap = Record<string, TargetTypeMetadata>
export interface TargetTypeMetadata {
  class: AnyClass
  properties: Array<PropertyMetadata>
}

export type GraphQLNonInputType = GraphQLObjectType | GraphQLScalarType | GraphQLList<any>

export type ResolverBuilder = (objectClass: AnyClass) => GraphQLFieldResolver<any, GraphQLResolverContext, any>

export interface GraphQLResolverContext {
  // TODO: check this to allow for any extra params for example in GQL_CONNECTION_INIT
  // [key: string]: any
  connectionID?: string
  operation: GraphQLOperation
  requestID: UUID
  user?: UserEnvelope
  storeSubscriptions: boolean
  pubSub: ReadModelPubSub
}

export function throwIfGraphQLErrors(errors?: ReadonlyArray<Error>): void {
  // We could have multiple errors, but there is not way to merge errors and keep its stack traces, so we
  // just throw the first error
  const firstError = errors?.[0]
  if (firstError) {
    throw firstError
  }
}
