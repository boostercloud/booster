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
import { GraphQLJSON } from 'graphql-scalars'
import { ClassMetadata, ClassType, PropertyMetadata, TypeMetadata } from '@boostercloud/metadata-booster'
import { DateScalar, isExternalType, nonExcludedFields } from './common'
import { Logger } from '@boostercloud/framework-types'

export class GraphQLTypeInformer {
  private graphQLTypes: Record<string, GraphQLType> = {}

  constructor(private logger: Logger) {}

  public generateGraphQLTypeForClass(type: ClassType, excludeProps: Array<string>, inputType: true): GraphQLInputType
  public generateGraphQLTypeForClass(type: ClassType, excludeProps: Array<string>, inputType?: false): GraphQLOutputType
  public generateGraphQLTypeForClass(type: ClassType, excludeProps: Array<string>, inputType: boolean): GraphQLType
  public generateGraphQLTypeForClass(type: ClassType, excludeProps: Array<string>, inputType = false): GraphQLType {
    this.logger.debug(`Generate GraphQL ${inputType ? 'input' : 'output'} type for class ${type.name}`)
    const metadata = getClassMetadata(type)
    return this.getOrCreateObjectType(metadata, inputType, excludeProps)
  }

  public getOrCreateGraphQLType(typeMetadata: TypeMetadata, inputType: true): GraphQLInputType
  public getOrCreateGraphQLType(typeMetadata: TypeMetadata, inputType?: false): GraphQLOutputType
  public getOrCreateGraphQLType(typeMetadata: TypeMetadata, inputType: boolean): GraphQLType
  public getOrCreateGraphQLType(typeMetadata: TypeMetadata, inputType = false): GraphQLType {
    if (typeMetadata.typeName === 'Promise') {
      return this.getOrCreateGraphQLType(typeMetadata.parameters[0], inputType)
    }
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
    if (typeMetadata.typeGroup === 'Array' || typeMetadata.typeGroup === 'ReadonlyArray') {
      return this.getGraphQLName(typeMetadata.parameters[0], inputType) + 'List' + (inputType ? 'Input' : '')
    }
    if (typeMetadata.typeName && typeMetadata.typeGroup === 'Class') {
      return typeMetadata.typeName + (inputType ? 'Input' : '')
    }
    return typeMetadata.typeName || null
  }

  private createGraphQLType(typeMetadata: TypeMetadata, inputType: boolean): GraphQLType {
    this.logger.debug(`Creating GraphQL ${inputType ? 'input' : 'output'} type for type ${typeMetadata.name}`)
    const { name, typeGroup } = typeMetadata

    if (name === 'Date') return DateScalar
    if (name === 'UUID') return GraphQLID
    if (typeGroup === 'String') return GraphQLString
    if (typeGroup === 'Number') return GraphQLFloat
    if (typeGroup === 'Boolean') return GraphQLBoolean
    if (typeGroup === 'Enum') return this.createEnumType(typeMetadata)
    if (typeGroup === 'Array' || typeGroup === 'ReadonlyArray') return this.createArrayType(typeMetadata, inputType)
    if (typeGroup === 'Class' && typeMetadata.type && !isExternalType(typeMetadata)) {
      const metadata = getClassMetadata(typeMetadata.type)
      return this.createObjectType(metadata, inputType)
    }
    return GraphQLJSON
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
    return new GraphQLList(GraphQLPropType)
  }

  private getOrCreateObjectType(
    classMetadata: ClassMetadata,
    inputType: boolean,
    excludeProps: Array<string>
  ): GraphQLType {
    const typeName = classMetadata.name + (inputType ? 'Input' : '')
    if (typeName && this.graphQLTypes[typeName]) return this.graphQLTypes[typeName]
    const createdGraphQLType = this.createObjectType(classMetadata, inputType, excludeProps)
    if (typeName) this.graphQLTypes[typeName] = createdGraphQLType
    return createdGraphQLType
  }

  private createObjectType(
    classMetadata: ClassMetadata,
    inputType: boolean,
    excludeProps?: Array<string>
  ): GraphQLType {
    const finalFields: Array<PropertyMetadata> = nonExcludedFields(classMetadata.fields, excludeProps)
    if (inputType) {
      return new GraphQLInputObjectType({
        name: classMetadata.name + 'Input',
        fields: finalFields?.reduce((obj, prop) => {
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
      fields: finalFields?.reduce((obj, prop) => {
        this.logger.debug(`Get or create GraphQL output type for property ${prop.name}`)
        return {
          ...obj,
          [prop.name]: { type: this.getOrCreateGraphQLType(prop.typeInfo, inputType) },
        }
      }, {}),
    })
  }
}
