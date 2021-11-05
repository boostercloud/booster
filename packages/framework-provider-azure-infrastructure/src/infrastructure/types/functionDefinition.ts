export interface IBinding {
  type: string
  name: string
  direction: string
}

export interface IScheduleBinding extends IBinding {
  schedule: string
}

export interface IGraphQLBinding extends IBinding {
  authLevel?: string
  methods?: Array<string>
}

export interface IEventHandlerBinding extends IBinding {
  leaseCollectionName: string
  connectionStringSetting: string
  databaseName: string
  collectionName: string
  createLeaseCollectionIfNotExists: string
}

export interface FunctionDefinition<T extends IBinding = IBinding> {
  name: string
  config: {
    bindings: Array<T>
    scriptFile: string
    entryPoint: string
  }
}

export type ScheduleFunctionDefinition = FunctionDefinition<IScheduleBinding>

export type GraphQLFunctionDefinition = FunctionDefinition<IGraphQLBinding>

export type EventHandlerFunctionDefinition = FunctionDefinition<IEventHandlerBinding>
