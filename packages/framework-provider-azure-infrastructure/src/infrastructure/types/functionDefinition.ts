export interface Binding {
  type: string
  name: string
  direction: string
}

export type ScheduleBinding = Binding & {
  schedule: string
}

export type GraphQLBinding = Binding & {
  authLevel?: string
  methods?: Array<string>
}

export type EventHandlerBinding = Binding & {
  leaseCollectionName: string
  connectionStringSetting: string
  databaseName: string
  collectionName: string
  createLeaseCollectionIfNotExists: string
  [key: string]: any
}

export interface FunctionDefinition<T extends Binding = Binding> {
  name: string
  config: {
    bindings: Array<T>
    scriptFile: string
    entryPoint: string
  }
}

export type ScheduleFunctionDefinition = FunctionDefinition<ScheduleBinding>

export type GraphQLFunctionDefinition = FunctionDefinition<GraphQLBinding>

export type EventHandlerFunctionDefinition = FunctionDefinition<EventHandlerBinding>
