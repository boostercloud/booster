import { AnyClass } from '@boostercloud/framework-types'
import { GraphQLInputObjectType, GraphQLList, GraphQLNonNull, GraphQLString, GraphQLType } from 'graphql'
import { getClassMetadata } from '../../../../decorators/metadata'
import { buildGraphqlSimpleEnumFor } from '../../common'

export class GraphqlQuerySortBuilder {
  private orderType = new GraphQLNonNull(buildGraphqlSimpleEnumFor('orderProperty', ['ASC', 'DESC']))

  public generateSortArguments(type: AnyClass): GraphQLList<GraphQLType> {
    const metadata = getClassMetadata(type)
    const fieldInputObjectType = new GraphQLInputObjectType({
      name: `${metadata.name}SortBy`,
      fields: {
        field: { type: new GraphQLNonNull(GraphQLString) },
        order: { type: this.orderType },
      },
    })
    return new GraphQLList(fieldInputObjectType)
  }
}
