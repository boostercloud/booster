import { AnyClass, BoosterConfig } from '@boostercloud/framework-types'
import { GraphQLBoolean, GraphQLFieldConfigArgumentMap, GraphQLInputObjectType, GraphQLList } from 'graphql'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { GraphqlQueryFilterArgumentsBuilder } from './graphql-query-filter-arguments-builder'

export class GraphqlQueryFilterFieldsBuilder {
  private graphqlQueryFilterArgumentsBuilder: GraphqlQueryFilterArgumentsBuilder

  constructor(
    protected readonly typeInformer: GraphQLTypeInformer,
    protected generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {},
    protected readonly config: BoosterConfig
  ) {
    this.graphqlQueryFilterArgumentsBuilder = new GraphqlQueryFilterArgumentsBuilder(
      typeInformer,
      generatedFiltersByTypeName,
      config
    )
  }

  public generateFilterQueriesFields(
    name: string,
    type: AnyClass,
    excludeProps: Array<string>
  ): GraphQLFieldConfigArgumentMap {
    const filterArguments = this.graphqlQueryFilterArgumentsBuilder.generateFilterArguments(type, excludeProps)
    const filter: GraphQLInputObjectType = new GraphQLInputObjectType({
      name: `${name}Filter`,
      fields: () => ({
        ...filterArguments,
        and: { type: new GraphQLList(filter) },
        or: { type: new GraphQLList(filter) },
        not: { type: filter },
        isDefined: { type: GraphQLBoolean },
      }),
    })
    return { filter: { type: filter } }
  }
}
