import { DynamoDB } from 'aws-sdk'
import { S3EventRecord } from 'aws-lambda'

export const handler = async (event: S3EventRecord): Promise<void> => {
  const { v4: uuidv4 } = require('uuid')
  const ddb = new DynamoDB.DocumentClient()

  const boosterEvent = s3EventToBoosterEvent(event)

  const params = {
    TableName: process.env.EVENT_STORE_NAME!,
    Item: {
      createdAt: new Date().toISOString(),
      entityID: boosterEvent.fileURI,
      entityTypeName: process.env.ENTITY_TYPE_NAME,
      entityTypeName_entityID_kind: `${process.env.ENTITY_TYPE_NAME}-${boosterEvent.fileURI}-event`,
      kind: 'event',
      requestID: uuidv4(),
      typeName: process.env.TYPE_NAME,
      value: boosterEvent,
      version: 1,
    },
  }

  try {
    await ddb.put(params).promise()
  } catch(e) {
    console.log('[ROCKET#fileuploader] An error occurred while performing a PutItem operation: ', e)
  }
}

function s3EventToBoosterEvent(event: any): {fileURI: string, fileSize: string} {
  const {
    bucket: { name },
    object: { key, size },
  } = event.Records[0].s3
  const fileSize = size
  const fileURI = `s3://${name}/${key}`
  return {
    fileURI,
    fileSize,
  }
}
