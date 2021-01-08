import { AWSError } from 'aws-sdk'
import { PutItemOutput } from 'aws-sdk/clients/dynamodb'

export const handler = async (event: any): Promise<any> => {

  const AWS = require('aws-sdk')
  const { v4: uuidv4 } = require('uuid');
  const ddb = new AWS.DynamoDB.DocumentClient()

  const bucketName = event.Records[0].s3.bucket.name
  const objectKey = event.Records[0].s3.object.key
  const fileSize = event.Records[0].s3.object.size
  const s3uri = `s3://${bucketName}/${objectKey}`

  const boosterEvent = {
    s3uri: s3uri,
    fileSize: fileSize
  }

  const params = {
    TableName: process.env.EVENT_STORE_NAME,
    Item: {
      createdAt: new Date().toISOString(),
      entityID: s3uri,
      entityTypeName: process.env.ENTITY_TYPE_NAME,
      entityTypeName_entityID_kind: `${process.env.ENTITY_TYPE_NAME}-${s3uri}-event`,
      kind: 'event',
      requestID: uuidv4(),
      typeName: process.env.TYPE_NAME,
      value: boosterEvent,
      version: 1,
    },
  }

  await ddb
    .put(params, function(err: AWSError, data: PutItemOutput) {
      if (err) {
        console.log('Error', err)
      } else {
        console.log('Success', data)
      }
    })
    .promise()
}
