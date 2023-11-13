export type EventStreamConfiguration =
  | {
      enabled: true
      parameters: {
        streamTopic: string
        partitionCount: number
        messageRetention: number // Specifies the number of days to retain the events for this Event Hub.
        dedupTtl?: number // Time to live in seconds
        maxRetries?: number
        mode?: 'exponential' | 'fixed'
      }
    }
  | {
      enabled: false
      parameters?: never
    }
