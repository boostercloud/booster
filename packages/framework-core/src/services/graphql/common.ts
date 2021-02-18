import { GraphQLList, GraphQLScalarType, GraphQLObjectType, GraphQLType } from 'graphql/type/definition'
import { AnyClass, UserEnvelope, UUID, GraphQLOperation } from '@boostercloud/framework-types'
import { GraphQLFieldResolver } from 'graphql'
import { ReadModelPubSub } from '../pub-sub/read-model-pub-sub'
import { PropertyMetadata } from 'metadata-booster'

export type TargetTypesMap = Record<string, TargetTypeMetadata>
export interface TargetTypeMetadata {
  class: AnyClass
  properties: Array<PropertyMetadata>
}

export type GraphQLNonInputType = GraphQLObjectType | GraphQLScalarType | GraphQLList<GraphQLType>

export type ResolverBuilder = (objectClass: AnyClass) => GraphQLFieldResolver<unknown, GraphQLResolverContext, any>

export interface GraphQLResolverContext {
  connectionID?: string
  operation: GraphQLOperation
  requestID: UUID
  user?: UserEnvelope
  storeSubscriptions: boolean
  pubSub: ReadModelPubSub
}

export const graphQLWebsocketSubprotocolHeaders = {
  'Sec-WebSocket-Protocol': 'graphql-ws',
}
