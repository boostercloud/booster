import { GraphQLList, GraphQLScalarType, GraphQLObjectType, GraphQLType } from 'graphql/type/definition'
import {
  AnyClass,
  UserEnvelope,
  UUID,
  GraphQLOperation,
  ReadModelInterface,
  ContextEnvelope,
} from '@boostercloud/framework-types'
import { GraphQLEnumType, GraphQLEnumValueConfigMap, GraphQLFieldResolver } from 'graphql'
import { ReadModelPubSub } from '../pub-sub/read-model-pub-sub'
import { PropertyMetadata } from 'metadata-booster'

export type TargetTypesMap = Record<string, TargetTypeMetadata>
export interface TargetTypeMetadata {
  class: AnyClass
  properties: Array<PropertyMetadata>
  returnClass?: AnyClass
}

export type GraphQLNonInputType = GraphQLObjectType | GraphQLScalarType | GraphQLList<GraphQLType>

export type ResolverBuilder = (objectClass: AnyClass) => GraphQLFieldResolver<unknown, GraphQLResolverContext, any>

export interface GraphQLResolverContext {
  connectionID?: string
  operation: GraphQLOperation
  requestID: UUID
  user?: UserEnvelope
  storeSubscriptions: boolean
  pubSub: ReadModelPubSub<ReadModelInterface>
  context?: ContextEnvelope
}

export const graphQLWebsocketSubprotocolHeaders = {
  'Sec-WebSocket-Protocol': 'graphql-ws',
}

export const buildGraphqlSimpleEnumFor = (enumName: string, values: Array<string>): GraphQLEnumType => {
  return new GraphQLEnumType({
    name: enumName,
    values: values.reduce((valuesRecord, value) => {
      valuesRecord[value] = { value }
      return valuesRecord
    }, {} as GraphQLEnumValueConfigMap),
  })
}
