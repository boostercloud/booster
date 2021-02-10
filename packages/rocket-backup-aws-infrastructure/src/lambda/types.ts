import { StreamSpecification } from 'aws-sdk/clients/dynamodb'

export type RestoreMessage = {
  from_table: string
  to_table: string
  status: 'RESTORING_TEMPORAL_TABLE' | 'DELETING_ORIGINAL_TABLE' | 'RESTORING_TO_ORIGINAL_TABLE' | 'COMPLETED'
  options: StreamSpecification
}