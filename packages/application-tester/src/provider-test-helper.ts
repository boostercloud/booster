export interface ApplicationOutputs {
  graphqlURL: string
  websocketURL: string
}

export interface Counters {
  subscriptions(): Promise<number>
  connections(): Promise<number>
  readModels(readModelName: string): Promise<number>
  events(): Promise<number>
}

export interface Queries {
  events(primaryKey: string): Promise<unknown>
  readModels(primaryKey: string, readModelName: string): Promise<unknown>
}

export interface ProviderTestHelper {
  outputs: ApplicationOutputs
  counters: Counters
  queries: Queries
}
