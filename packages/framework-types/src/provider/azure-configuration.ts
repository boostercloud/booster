export const DEFAULT_CHUNK_SIZE = 100

export interface AzureConfiguration {
  enableEventBatching: boolean
  cosmos: {
    // Maximum number of operations in a single batch (default 100)
    batchSize: number

    requestOptions?: {
      // Override consistency level for specific operations
      consistencyLevel?: 'Strong' | 'BoundedStaleness' | 'Session' | 'Eventual' | 'ConsistentPrefix'

      // Enable/disable RU/minute usage when RU/second is exhausted
      disableRUPerMinuteUsage?: boolean

      // Specify indexing directives
      indexingDirective?: 'Include' | 'Exclude'
    }
  }
}
