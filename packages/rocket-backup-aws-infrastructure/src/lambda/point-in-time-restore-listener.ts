import { SQSEvent } from 'aws-lambda'
import { DynamoDB, SQS } from 'aws-sdk'
import { RestoreMessage } from './types'
import { StreamSpecification } from 'aws-sdk/clients/dynamodb'

export const handler = async (event: SQSEvent): Promise<void> => {
  try {
    const sqs = await new SQS()
    const dynamoDB = await new DynamoDB()
    for (let i = 0; i < event.Records.length; i++) {
      let message = JSON.parse(event.Records[i].body) as RestoreMessage
      console.log('///// START PROCESS /////')
      console.log(message)
      if (message.status === 'COMPLETED') continue
      if (!(await tableExists(message.from_table, dynamoDB))) {
        console.log('///// RESTORE ORIGINAL /////')
        console.log(message)
        await dynamoDB
          .restoreTableToPointInTime({
            SourceTableName: message.to_table,
            TargetTableName: message.from_table,
            UseLatestRestorableTime: true,
          })
          .promise()
        message = { ...message, from_table: message.to_table, to_table: message.from_table, status: 'RESTORING_TO_ORIGINAL_TABLE' }
        console.log('///// AFTER RESTORE ORIGINAL /////')
        console.log(message)
      } else if (await isStatusActive(message.to_table, dynamoDB) && message.status !== 'DELETING_ORIGINAL_TABLE') {
        console.log('///// DELETE ORIGINAL /////')
        console.log(message)
        await applySettings(message.options, message.to_table, dynamoDB)
        await dynamoDB.deleteTable({ TableName: message.from_table }).promise()
        console.log('///// AFTER DELETE ORIGINAL /////')
        console.log(message)
        if (!(message.to_table.split('-').pop() === 'restoring')) {
          console.log('///// COMPLETED /////')
          console.log(message)
          message = { ...message, status: 'COMPLETED' }
        } else {
          console.log('///// DELETING ORIGINAL PROCESS STARTED /////')
          console.log(message)
          message = { ...message, status: 'DELETING_ORIGINAL_TABLE' }
        }
      }
      await sqs.sendMessage({ QueueUrl: process.env['SQS_URL']!, MessageBody: JSON.stringify(message) }).promise()
    }
  } catch (e) {
    throw Error(`Something went wrong when consuming your restore messages: ${e.message}`)
  }
}

const applySettings = async (options: StreamSpecification, toTable: string, dynamoDB: DynamoDB): Promise<void> => {
  try {
    await dynamoDB
      .updateTable({
        TableName: toTable,
        StreamSpecification: options,
      })
      .promise()

    await dynamoDB
      .updateContinuousBackups({
        TableName: toTable,
        PointInTimeRecoverySpecification: { PointInTimeRecoveryEnabled: true },
      })
      .promise()
  } catch (e) {
    throw Error(`Unable to apply settings to ${toTable}: ${e.message}`)
  }
}

const tableExists = async (tableName: string, dynamoDB: DynamoDB): Promise<boolean> => {
  try {
    console.log('///// TABLE EXISTS? /////')
    const response = await dynamoDB.listTables().promise()
    console.log(response.TableNames?.includes(tableName)!)
    return response.TableNames?.includes(tableName)!
  } catch (e) {
    throw Error(`Something happened when checking if ${tableName} exists: ${e.message}`)
  }
}

const isStatusActive = async (tableName: string, dynamoDB: DynamoDB): Promise<boolean> => {
  try {
    console.log('///// IS STATUS ACTIVE? /////')
    console.log(tableName)
    const response = await dynamoDB.describeTable({ TableName: tableName }).promise()
    console.log(response.Table?.TableStatus === 'ACTIVE')
    return response.Table?.TableStatus === 'ACTIVE'
  } catch (e) {
    return false
  }
}