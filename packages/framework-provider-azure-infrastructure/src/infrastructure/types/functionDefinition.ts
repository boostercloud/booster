export interface Binding {
  type: string
  name: string
  direction: string
}

export type HttpBinding = Binding & {
  authLevel?: string
  methods?: Array<string>
  route?: string
}

export type ScheduleBinding = Binding & {
  schedule: string
}

export type GraphQLBinding = Binding & {
  authLevel?: string
  methods?: Array<string>
}

export type EventHandlerBinding = Binding & {
  leaseContainerName: string
  connection: string
  databaseName: string
  containerName: string
  createLeaseContainerIfNotExists: string
  [key: string]: any
}

export type SubscriptionBinding = Binding & {
  hub: string
  direction: 'out'
}

export type SocketsBinding = Binding & {
  hub: string
  direction: 'in'
  eventType: string
  eventName: string
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

export type HttpFunctionDefinition = FunctionDefinition<HttpBinding>

export type EventHandlerFunctionDefinition = FunctionDefinition<EventHandlerBinding>

export type SubscriptionsNotifierFunctionDefinition = FunctionDefinition<EventHandlerBinding | SubscriptionBinding>

export type SocketsFunctionDefinition = FunctionDefinition<GraphQLBinding | SocketsBinding>
