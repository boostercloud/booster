import { AnyClass, UUID } from '@boostercloud/framework-types'
import {
  GraphQLBoolean,
  GraphQLFieldConfigArgumentMap,
  GraphQLFloat,
  GraphQLID,
  GraphQLInputFieldConfigMap,
  GraphQLInputObjectType,
  GraphQLList,
  GraphQLScalarType,
  GraphQLString,
  Thunk,
} from 'graphql'
import { PropertyMetadata, TypeMetadata } from 'metadata-booster'
import { getClassMetadata } from '../../../../decorators/metadata'
import { DateScalar, isExternalType } from '../../common'
import { GraphQLJSONObject } from 'graphql-type-json'
import { GraphQLTypeInformer } from '../../graphql-type-informer'

export class GraphqlQueryFilterArgumentsBuilder {
  private generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}

  public constructor(private readonly typeInformer: GraphQLTypeInformer) {}

  public generateFilterArguments(type: AnyClass): GraphQLFieldConfigArgumentMap {
    const metadata = getClassMetadata(type)
    const args: GraphQLFieldConfigArgumentMap = {}
    metadata.fields.forEach((prop: PropertyMetadata) => {
      args[prop.name] = {
        type: this.generateFilterFor(prop),
      }
    })
    return args
  }

  private generateFilterFor(prop: PropertyMetadata): GraphQLInputObjectType | GraphQLScalarType {
    let filterName = `${prop.typeInfo.name}PropertyFilter`
    filterName = filterName.charAt(0).toUpperCase() + filterName.substr(1)

    if (this.generatedFiltersByTypeName[filterName]) return this.generatedFiltersByTypeName[filterName]
    if (prop.typeInfo.typeGroup === 'Array') return this.generateArrayFilterFor(prop)
    let fields: Thunk<GraphQLInputFieldConfigMap> = {}

    if (prop.typeInfo.name === 'UUID' || prop.typeInfo.name === 'Date') {
      fields = this.generateFilterInputTypes(prop.typeInfo)
    } else if (prop.typeInfo.type && prop.typeInfo.typeGroup === 'Class') {
      if (isExternalType(prop.typeInfo)) return GraphQLJSONObject
      let nestedProperties: GraphQLInputFieldConfigMap = {}
      const metadata = getClassMetadata(prop.typeInfo.type)
      if (metadata.fields.length === 0) return GraphQLJSONObject

      this.typeInformer.generateGraphQLTypeForClass(prop.typeInfo.type, true)

      for (const prop of metadata.fields) {
        const property = { [prop.name]: { type: this.generateFilterFor(prop) } }
        nestedProperties = { ...nestedProperties, ...property }
      }
      fields = () => ({
        ...nestedProperties,
        and: { type: new GraphQLList(this.generatedFiltersByTypeName[filterName]) },
        or: { type: new GraphQLList(this.generatedFiltersByTypeName[filterName]) },
        not: { type: this.generatedFiltersByTypeName[filterName] },
      })
    } else if (prop.typeInfo.type && prop.typeInfo.type.name !== 'Object') {
      fields = this.generateFilterInputTypes(prop.typeInfo)
    } else {
      return GraphQLJSONObject
    }
    this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({ name: filterName, fields })
    return this.generatedFiltersByTypeName[filterName]
  }

  private generateArrayFilterFor(property: PropertyMetadata): GraphQLInputObjectType {
    let filterName = `${property.typeInfo.parameters[0].name}ArrayPropertyFilter`
    filterName = filterName.charAt(0).toUpperCase() + filterName.substr(1)

    if (!this.generatedFiltersByTypeName[filterName]) {
      const propFilters: GraphQLInputFieldConfigMap = {}
      property.typeInfo.parameters.forEach((param) => {
        let graphqlType: GraphQLScalarType
        switch (param.typeGroup) {
          case 'Boolean':
            graphqlType = GraphQLBoolean
            break
          case 'String':
            graphqlType = GraphQLString
            break
          case 'Number':
            graphqlType = GraphQLFloat
            break
          default:
            graphqlType = param.type === UUID ? GraphQLID : GraphQLJSONObject
            break
        }
        propFilters.includes = { type: graphqlType }
      })

      this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({
        name: filterName,
        fields: propFilters,
      })
    }
    return this.generatedFiltersByTypeName[filterName]
  }

  private generateFilterInputTypes(typeMetadata: TypeMetadata): GraphQLInputFieldConfigMap {
    const { name, typeGroup } = typeMetadata
    if (typeGroup === 'Boolean')
      return {
        eq: { type: GraphQLBoolean },
        ne: { type: GraphQLBoolean },
      }
    if (typeGroup === 'Number')
      return {
        eq: { type: GraphQLFloat },
        ne: { type: GraphQLFloat },
        lte: { type: GraphQLFloat },
        lt: { type: GraphQLFloat },
        gte: { type: GraphQLFloat },
        gt: { type: GraphQLFloat },
        in: { type: GraphQLList(GraphQLFloat) },
      }
    if (typeGroup === 'String')
      return {
        eq: { type: GraphQLString },
        ne: { type: GraphQLString },
        lte: { type: GraphQLString },
        lt: { type: GraphQLString },
        gte: { type: GraphQLString },
        gt: { type: GraphQLString },
        in: { type: GraphQLList(GraphQLString) },
        beginsWith: { type: GraphQLString },
        contains: { type: GraphQLString },
      }
    // use `name`, `typeGroup` === 'Class'
    if (name === 'UUID')
      return {
        eq: { type: GraphQLID },
        ne: { type: GraphQLID },
        in: { type: GraphQLList(GraphQLID) },
      }
    // use `name`, `typeGroup` === 'Interface'
    if (name === 'Date')
      return {
        eq: { type: DateScalar },
        ne: { type: DateScalar },
        lte: { type: DateScalar },
        lt: { type: DateScalar },
        gte: { type: DateScalar },
        gt: { type: DateScalar },
        in: { type: GraphQLList(DateScalar) },
      }
    if (typeGroup === 'Enum') {
      const EnumType = this.typeInformer.getOrCreateGraphQLType(typeMetadata, true)
      return {
        eq: { type: EnumType },
        ne: { type: EnumType },
      }
    }

    throw new Error(`Type ${name} is not supported in search filters`)
  }
}
