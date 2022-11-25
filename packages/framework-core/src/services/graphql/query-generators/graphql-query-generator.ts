import { GraphQLTypeInformer } from '../graphql-type-informer'
import { GraphQLResolverContext, ResolverBuilder, TargetTypesMap } from '../common'
import { GraphQLFieldConfigMap } from 'graphql'
import { GraphqlGenerateHandledFields } from '../query-helpers/graphql-generate-handled-fields'

export class GraphqlQueryGenerator {
  public constructor(
    private readonly targetTypes: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly queryResolveBuilder: ResolverBuilder
  ) {}

  public generateQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const graphqlGenerateHandledFields = new GraphqlGenerateHandledFields(
      this.targetTypes,
      this.typeInformer,
      this.queryResolveBuilder
    )
    return graphqlGenerateHandledFields.generateFields()
  }
}
