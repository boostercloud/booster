export interface ApplicationOutputs {
  graphqlURL: string
  websocketURL: string
}

export interface ProviderTestHelper {
  outputs: ApplicationOutputs
}
