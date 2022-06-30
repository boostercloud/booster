import { GraphQLScalarType } from 'graphql/type/definition'
import {
  AnyClass,
  UserEnvelope,
  UUID,
  GraphQLOperation,
  ReadModelInterface,
  ContextEnvelope,
} from '@boostercloud/framework-types'
import {
  GraphQLEnumType,
  GraphQLEnumValueConfigMap,
  GraphQLFieldResolver,
  GraphQLList,
  GraphQLObjectType,
  GraphQLType,
  Kind,
} from 'graphql'
import { ReadModelPubSub } from '../pub-sub/read-model-pub-sub'
import { PropertyMetadata, TypeMetadata } from 'metadata-booster'

export type TargetTypesMap = Record<string, TargetTypeMetadata>
export interface TargetTypeMetadata {
  class: AnyClass
  properties: Array<PropertyMetadata>
  methods: Array<PropertyMetadata>
}

export type GraphQLNonInputType = GraphQLObjectType | GraphQLScalarType | GraphQLList<GraphQLType>

export type ResolverBuilder = (objectClass: AnyClass) => GraphQLFieldResolver<unknown, GraphQLResolverContext, any>

export interface GraphQLResolverContext {
  connectionID?: string
  responseHeaders: Record<string, string>
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

export const DateScalar = new GraphQLScalarType({
  name: 'Date',
  description: 'Date custom scalar type',
  serialize(value) {
    return value.toJSON()
  },
  parseValue(value) {
    const date = new Date(value)
    if (isNaN(date.getTime())) throw new Error(`Invalid date: ${value}`)
    return date
  },
  parseLiteral(ast) {
    if (ast.kind === Kind.STRING) {
      return new Date(ast.value)
    }
    return null
  },
})

export function isExternalType(typeMetadata: Pick<TypeMetadata, 'importPath'>): boolean {
  return !!typeMetadata.importPath && !typeMetadata.importPath.startsWith('.')
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
