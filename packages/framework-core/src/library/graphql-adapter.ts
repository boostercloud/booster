// Import statements remain the same

export class GraphQLAdapter {
  // Other methods remain the same

  private static getGraphQLTypeFor(type: AnyClass): GraphQLNonInputType {
    if (type === Array) {
      throw new Error('Arrays must be used with the specific type they contain, use `Array<SomeType>` instead')
    }
    if (type === Boolean) {
      return GraphQLBoolean
    }
    if (type === String) {
      return GraphQLString
    }
    if (type === Number) {
      return GraphQLFloat
    }
    if (type === Date) {
      return GraphQLString
    }
    if (type.prototype && Object.getPrototypeOf(type.prototype) === Array.prototype) {
      const elementType = type.prototype[0]
      return new GraphQLList(this.getGraphQLTypeFor(elementType))
    }
    if (type.prototype instanceof UUID) {
      return GraphQLString
    }
    if (type.prototype instanceof ReadModelInterface) {
      return this.getReadModelGraphQLType(type as Class<ReadModelInterface>)
    }
    throw new Error(`Don't know how to build a GraphQL type for ${type.name}`)
  }
  // Other methods remain the same
}

  // Other methods remain the same
}
