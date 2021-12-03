import { getClassMetadata } from './../../decorators/metadata'
import {
  GraphQLBoolean,
  GraphQLEnumType,
  GraphQLFloat,
  GraphQLID,
  GraphQLInputObjectType,
  GraphQLInputType,
  GraphQLList,
  GraphQLNonNull,
  GraphQLObjectType,
  GraphQLOutputType,
  GraphQLString,
  GraphQLType,
} from 'graphql'
import { GraphQLJSONObject } from 'graphql-type-json'
import { ClassMetadata, ClassType, TypeGroup, TypeMetadata } from 'metadata-booster'
import { DateScalar, isExternalType } from './common'
import { Logger } from '@boostercloud/framework-types'

export class GraphQLTypeInformer {
  private graphQLTypes: Record<string, GraphQLType> = {}

  constructor(private logger: Logger) {}

  public generateGraphQLTypeForClass(type: ClassType, inputType: true): GraphQLInputType
  public generateGraphQLTypeForClass(type: ClassType, inputType?: false): GraphQLOutputType
  public generateGraphQLTypeForClass(type: ClassType, inputType: boolean): GraphQLType
  public generateGraphQLTypeForClass(type: ClassType, inputType = false): GraphQLType {
    this.logger.debug(`Generate GraphQL ${inputType ? 'input' : 'output'} type for class ${type.name}`)
    const metadata = getClassMetadata(type)
    return this.getOrCreateObjectType(metadata, inputType)
  }

  public getOrCreateGraphQLType(typeMetadata: TypeMetadata, inputType: true): GraphQLInputType
  public getOrCreateGraphQLType(typeMetadata: TypeMetadata, inputType?: false): GraphQLOutputType
  public getOrCreateGraphQLType(typeMetadata: TypeMetadata, inputType: boolean): GraphQLType
  public getOrCreateGraphQLType(typeMetadata: TypeMetadata, inputType = false): GraphQLType {
    const name = this.getGraphQLName(typeMetadata, inputType)

    // Check if GraphQL type already exists and reuse
    if (name && this.graphQLTypes[name]) {
      this.logger.debug(`Found existing GraphQL ${inputType ? 'input' : 'output'} type for name ${name}`)
      return typeMetadata.isNullable ? this.graphQLTypes[name] : new GraphQLNonNull(this.graphQLTypes[name])
    }

    // Create new GraphQL type from metadata
    const createdGraphQLType = this.createGraphQLType(typeMetadata, inputType)

    // Store created GraphQL type to reuse
    if (name) this.graphQLTypes[name] = createdGraphQLType

    return typeMetadata.isNullable ? createdGraphQLType : new GraphQLNonNull(createdGraphQLType)
  }

  private getGraphQLName(typeMetadata: TypeMetadata, inputType: boolean): string | null {
    if (typeMetadata.name === 'UUID' || typeMetadata.name === 'Date') {
      // UUID is a class but should result in a scalar which doesn't need a separate input type
      // Date is an interface which has no `type`, so we need to use `name` instead
      return typeMetadata.name
    }
    if (typeMetadata.typeGroup === TypeGroup.Array) {
      return this.getGraphQLName(typeMetadata.parameters[0], inputType) + 'List' + (inputType ? 'Input' : '')
    }
    if (typeMetadata.typeName && typeMetadata.typeGroup === TypeGroup.Class) {
      return typeMetadata.typeName + (inputType ? 'Input' : '')
    }
    return typeMetadata.typeName || null
  }

  private createGraphQLType(typeMetadata: TypeMetadata, inputType: boolean): GraphQLType {
    this.logger.debug(`Creating GraphQL ${inputType ? 'input' : 'output'} type for type ${typeMetadata.name}`)
    const { name, typeGroup } = typeMetadata

    if (name === 'Date') return DateScalar
    if (name === 'UUID') return GraphQLID
    if (typeGroup === TypeGroup.String) return GraphQLString
    if (typeGroup === TypeGroup.Number) return GraphQLFloat
    if (typeGroup === TypeGroup.Boolean) return GraphQLBoolean
    if (typeGroup === TypeGroup.Enum) return this.createEnumType(typeMetadata)
    if (typeGroup === TypeGroup.Array) return this.createArrayType(typeMetadata, inputType)
    if (typeGroup === TypeGroup.Class && typeMetadata.type && !isExternalType(typeMetadata)) {
      const metadata = getClassMetadata(typeMetadata.type)
      return this.createObjectType(metadata, inputType)
    }
    return GraphQLJSONObject
  }

  private createEnumType(typeMetadata: TypeMetadata): GraphQLEnumType {
    return new GraphQLEnumType({
      name: typeMetadata.name,
      values: typeMetadata.parameters.reduce((obj, el) => ({ ...obj, [el.name]: {} }), {}),
    })
  }

  private createArrayType(typeMetadata: TypeMetadata, inputType: boolean): GraphQLList<GraphQLType> {
    const param = typeMetadata.parameters[0]
    const GraphQLPropType = this.getOrCreateGraphQLType(param, inputType)
    return GraphQLList(GraphQLPropType)
  }

  private getOrCreateObjectType(classMetadata: ClassMetadata, inputType: boolean): GraphQLType {
    const typeName = classMetadata.name + (inputType ? 'Input' : '')
    if (typeName && this.graphQLTypes[typeName]) return this.graphQLTypes[typeName]
    const createdGraphQLType = this.createObjectType(classMetadata, inputType)
    if (typeName) this.graphQLTypes[typeName] = createdGraphQLType
    return createdGraphQLType
  }

  private createObjectType(classMetadata: ClassMetadata, inputType: boolean): GraphQLType {
    if (inputType) {
      return new GraphQLInputObjectType({
        name: classMetadata.name + 'Input',
        fields: classMetadata.fields.reduce((obj, prop) => {
          this.logger.debug(`Get or create GraphQL input type for property ${prop.name}`)
          return {
            ...obj,
            [prop.name]: { type: this.getOrCreateGraphQLType(prop.typeInfo, inputType) },
          }
        }, {}),
      })
    }
    return new GraphQLObjectType({
      name: classMetadata.name,
      fields: classMetadata.fields.reduce((obj, prop) => {
        this.logger.debug(`Get or create GraphQL output type for property ${prop.name}`)
        return {
          ...obj,
          [prop.name]: { type: this.getOrCreateGraphQLType(prop.typeInfo, inputType) },
        }
      }, {}),
    })
  }
}
