import { BoosterConfig } from '@boostercloud/framework-types'
import {
  GraphQLBoolean,
  GraphQLFloat,
  GraphQLID,
  GraphQLObjectType,
  GraphQLScalarType,
  GraphQLSchema,
  GraphQLString,
} from 'graphql'
import { GraphQLFieldConfigMap } from 'graphql/type/definition'
import { AnyClass, EntityMetadata, UUID } from '@boostercloud/framework-types'

export class GraphqlGenerator {
  public constructor(private config: BoosterConfig) {}

  public generateSchema(): GraphQLSchema {
    const query = new GraphQLObjectType({
      name: 'Query',
      fields: this.generateTypeAndResolver(this.config.entities),
    })
    return new GraphQLSchema({ query })
  }

  private generateTypeAndResolver(entities: Record<string, EntityMetadata>): GraphQLFieldConfigMap<any, any> {
    const typeAndResolvers: GraphQLFieldConfigMap<any, any> = {}
    for (const name in entities) {
      typeAndResolvers[name] = {
        type: this.generateType(entities[name]),
        args: {
          id: { type: GraphQLString },
        },
        resolve: (source, args, context, info) => {
          console.log('Resolving!')
          console.log('source: ', source)
          console.log('args: ', args)
          console.log('context: ', context)
          console.log('info: ', info)
          return {}
        },
      }
    }

    return typeAndResolvers
  }

  private generateType(entity: EntityMetadata): GraphQLObjectType {
    const fields: GraphQLFieldConfigMap<any, any> = {}
    for (const prop of entity.properties) {
      fields[prop.name] = { type: graphQLTypeFor(prop.type) }
    }
    return new GraphQLObjectType({
      name: entity.class.name,
      fields,
    })
  }
}

function graphQLTypeFor(type: AnyClass): GraphQLScalarType | GraphQLObjectType {
  switch (type) {
    case String:
      return GraphQLString
    case Number:
      return GraphQLFloat
    case UUID:
      return GraphQLID
    case Boolean:
      return GraphQLBoolean
    default:
      // TODO: Figure out how to handle `Object` (interfaces), `Array`, and other entities
      // First I need to generate all entities, as I need to use them here.
      return GraphQLString
  }
}
