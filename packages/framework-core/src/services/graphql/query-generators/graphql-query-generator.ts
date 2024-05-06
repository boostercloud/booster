import { GraphQLTypeInformer } from '../graphql-type-informer'
import { GraphQLResolverContext, ResolverBuilder, TargetTypesMap } from '../common'
import { GraphQLFieldConfigMap } from 'graphql'
import { GraphQLHandledFieldsGenerator } from '../query-helpers/graphql-handled-fields-generator'
import { BoosterConfig } from '@boostercloud/framework-types'

export class GraphqlQueryGenerator {
  public constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly queryResolveBuilder: ResolverBuilder,
    private readonly config: BoosterConfig
  ) {}

  public generateQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const graphqlGenerateHandledFields = new GraphQLHandledFieldsGenerator(
      this.targetTypes,
      this.typeInformer,
      this.queryResolveBuilder,
      this.config
    )
    return graphqlGenerateHandledFields.generateFields(false)
  }
}
