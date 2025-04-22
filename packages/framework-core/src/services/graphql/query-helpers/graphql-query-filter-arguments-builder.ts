import {
  GraphQLBoolean,
  GraphQLFieldConfigArgumentMap,
  GraphQLFloat,
  GraphQLID,
  GraphQLInputFieldConfig,
  GraphQLInputObjectType,
  GraphQLList,
  GraphQLScalarType,
  GraphQLString,
  ThunkObjMap,
} from 'graphql'
import { getClassMetadata } from '../../../decorators/metadata'
import { PropertyMetadata, TypeMetadata } from '@boostercloud/metadata-booster'
import { GraphQLJSON } from 'graphql-scalars'
import { AnyClass, BoosterConfig, UUID } from '@boostercloud/framework-types'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { DateScalar, isExternalType, nonExcludedFields } from '../common'

export class GraphqlQueryFilterArgumentsBuilder {
  constructor(
    private readonly typeInformer: GraphQLTypeInformer,
    protected generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {},
    private readonly config: BoosterConfig
  ) {}

  public generateFilterArguments(type: AnyClass, excludeProps: Array<string>): GraphQLFieldConfigArgumentMap {
    const metadata = getClassMetadata(type)
    const args: GraphQLFieldConfigArgumentMap = {}
    const finalFields: Array<PropertyMetadata> = nonExcludedFields(metadata.fields, excludeProps)
    finalFields
      .filter((field: PropertyMetadata) => !field.typeInfo.isGetAccessor)
      .forEach((prop: PropertyMetadata) => {
        args[prop.name] = {
          type: this.generateFilterFor(prop),
        }
      })
    return args
  }

  private generateFilterFor(prop: PropertyMetadata): GraphQLInputObjectType | GraphQLScalarType {
    let filterName = `${prop.typeInfo.name}PropertyFilter`
    filterName = filterName.replace(/[^_a-zA-Z]/g, '_')
    filterName = filterName.charAt(0).toUpperCase() + filterName.substr(1)

    if (this.generatedFiltersByTypeName[filterName]) return this.generatedFiltersByTypeName[filterName]
    if (prop.typeInfo.typeGroup === 'Array') return this.generateArrayFilterFor(prop)
    let fields: ThunkObjMap<GraphQLInputFieldConfig> = {}

    if (prop.typeInfo.name === 'UUID' || prop.typeInfo.name === 'Date') {
      fields = this.generateFilterInputTypes(prop.typeInfo)
    } else if (prop.typeInfo.type && (prop.typeInfo.typeGroup === 'Class' || prop.typeInfo.typeGroup === 'Object')) {
      if (isExternalType(prop.typeInfo)) return GraphQLJSON
      let nestedProperties: ThunkObjMap<GraphQLInputFieldConfig> = {}
      const metadata = getClassMetadata(prop.typeInfo.type)
      if (metadata.fields.length === 0) return GraphQLJSON
      const excludeProps = this.config.nonExposedGraphQLMetadataKey[prop.name]
      this.typeInformer.generateGraphQLTypeForClass(prop.typeInfo.type, excludeProps, true)

      for (const prop of metadata.fields) {
        const property = { [prop.name]: { type: this.generateFilterFor(prop) } }
        nestedProperties = { ...nestedProperties, ...property }
      }
      fields = () => ({
        ...nestedProperties,
        and: { type: new GraphQLList(this.generatedFiltersByTypeName[filterName]) },
        or: { type: new GraphQLList(this.generatedFiltersByTypeName[filterName]) },
        not: { type: this.generatedFiltersByTypeName[filterName] },
        isDefined: { type: GraphQLBoolean },
      })
    } else if (prop.typeInfo.type && prop.typeInfo.type.name !== 'Object') {
      fields = this.generateFilterInputTypes(prop.typeInfo)
    } else {
      return GraphQLJSON
    }
    this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({ name: filterName, fields })
    return this.generatedFiltersByTypeName[filterName]
  }

  private generateArrayFilterFor(property: PropertyMetadata): GraphQLInputObjectType {
    let filterName = `${property.typeInfo.parameters[0].name}ArrayPropertyFilter`
    filterName = filterName.replace(/[^_a-zA-Z]/g, '_')
    filterName = filterName.charAt(0).toUpperCase() + filterName.substr(1)

    if (!this.generatedFiltersByTypeName[filterName]) {
      const propFilters: ThunkObjMap<GraphQLInputFieldConfig> = {}
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
            graphqlType = param.type === UUID ? GraphQLID : GraphQLJSON
            break
        }
        propFilters.includes = { type: graphqlType }
      })
      propFilters.isDefined = { type: GraphQLBoolean }

      this.generatedFiltersByTypeName[filterName] = new GraphQLInputObjectType({
        name: filterName,
        fields: propFilters,
      })
    }
    return this.generatedFiltersByTypeName[filterName]
  }

  private generateFilterInputTypes(typeMetadata: TypeMetadata): ThunkObjMap<GraphQLInputFieldConfig> {
    const { name, typeGroup } = typeMetadata
    if (typeGroup === 'Boolean')
      return {
        eq: { type: GraphQLBoolean },
        ne: { type: GraphQLBoolean },
        isDefined: { type: GraphQLBoolean },
      }
    if (typeGroup === 'Number')
      return {
        eq: { type: GraphQLFloat },
        ne: { type: GraphQLFloat },
        lte: { type: GraphQLFloat },
        lt: { type: GraphQLFloat },
        gte: { type: GraphQLFloat },
        gt: { type: GraphQLFloat },
        in: { type: new GraphQLList(GraphQLFloat) },
        isDefined: { type: GraphQLBoolean },
      }
    if (typeGroup === 'String')
      return {
        eq: { type: GraphQLString },
        ne: { type: GraphQLString },
        lte: { type: GraphQLString },
        lt: { type: GraphQLString },
        gte: { type: GraphQLString },
        gt: { type: GraphQLString },
        in: { type: new GraphQLList(GraphQLString) },
        beginsWith: { type: GraphQLString },
        contains: { type: GraphQLString },
        regex: { type: GraphQLString },
        iRegex: { type: GraphQLString },
        isDefined: { type: GraphQLBoolean },
      }
    // use `name`, `typeGroup` === 'Class'
    if (name === 'UUID')
      return {
        eq: { type: GraphQLID },
        ne: { type: GraphQLID },
        in: { type: new GraphQLList(GraphQLID) },
        beginsWith: { type: GraphQLString },
        contains: { type: GraphQLString },
        isDefined: { type: GraphQLBoolean },
      }
    // use `name`, `typeGroup` === 'Interface'.
    if (name === 'Date')
      return {
        eq: { type: DateScalar },
        ne: { type: DateScalar },
        lte: { type: DateScalar },
        lt: { type: DateScalar },
        gte: { type: DateScalar },
        gt: { type: DateScalar },
        in: { type: new GraphQLList(DateScalar) },
        isDefined: { type: GraphQLBoolean },
      }
    if (typeGroup === 'Enum') {
      const EnumType = this.typeInformer.getOrCreateGraphQLType(typeMetadata, true)
      return {
        eq: { type: EnumType },
        ne: { type: EnumType },
        isDefined: { type: GraphQLBoolean },
      }
    }

    throw new Error(`Type ${name} is not supported in search filters`)
  }
}
