import { GraphQLList, GraphQLScalarType, GraphQLObjectType } from 'graphql/type/definition'
import { AnyClass, PropertyMetadata } from '@boostercloud/framework-types'

export type TargetTypesMap = Record<string, TargetTypeMetadata>
export interface TargetTypeMetadata {
  class: AnyClass
  properties: Array<PropertyMetadata>
}
export type GraphQLNonInputType = GraphQLObjectType | GraphQLScalarType | GraphQLList<any>
