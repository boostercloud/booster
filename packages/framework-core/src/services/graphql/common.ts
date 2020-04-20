import { GraphQLList, GraphQLScalarType, GraphQLObjectType } from 'graphql/type/definition'
import { AnyClass, PropertyMetadata, UserEnvelope, UUID, GraphQLOperation } from '@boostercloud/framework-types'
import { GraphQLFieldResolver } from 'graphql'
import { PubSub } from 'graphql-subscriptions'

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
  pubSub: PubSub
}
