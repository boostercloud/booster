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
  ValueNode,
} from 'graphql'
import { ReadModelPubSub } from '../pub-sub/read-model-pub-sub'
import { PropertyMetadata, TypeMetadata } from '@boostercloud/metadata-booster'

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

function parseDate(inputValue: unknown): Date {
  // TODO: Don't let this pass review.
  const date = new Date(inputValue as string)
  if (isNaN(date.getTime())) throw new Error(`Invalid date: ${inputValue}`)
  return date
}

export const DateScalar = new GraphQLScalarType<Date, string>({
  name: 'Date',
  description: 'Date custom scalar type',
  serialize(value: unknown): string {
    return (value as Date).toJSON()
  },
  parseValue: parseDate,
  parseLiteral(ast: ValueNode): Date {
    if (ast.kind === Kind.STRING) {
      return parseDate(ast.value)
    }
    // TODO: Don't let this pass review.
    return (null as unknown as Date)
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
