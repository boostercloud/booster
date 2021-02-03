import { AWSError, DynamoDB } from 'aws-sdk'
import { DocumentClient, PutItemOutput } from 'aws-sdk/clients/dynamodb'

const stagingBucketName = process.env.STAGING_BUCKET_NAME

export const handler = async (event: any): Promise<any> => {
  const AWS = require('aws-sdk')
  const s3 = new AWS.S3()
  const ddb = new DynamoDB.DocumentClient()

  const {
    bucket: { name },
    object: { key },
  } = event.Records[0].s3

  const params = {
    Bucket: name,
    Key: key,
  }
  const data = await s3.getObject(params).promise()
  const content = data.Body.toString()

  let arrayLines = []
  const lines = content.split('\n')
  let itr = 0
  let fileNumber = 1

  while (itr < lines.length) {
    if (arrayLines.length != 0 && arrayLines.length % Number(process.env.CHUNK_SIZE) == 0) {
      const newKey = `part-${fileNumber}-${key}`
      const s3uri = `s3://${stagingBucketName}/${newKey}`

      const fileSize = await save(newKey, arrayLines)
      await addEvent(s3uri, fileSize, ddb)

      fileNumber = fileNumber + 1
      arrayLines = []
    }
    if (lines[itr]) {
      arrayLines.push(lines[itr])
    }

    itr = itr + 1
  }

  if (arrayLines.length != 0) {
    const newKey = `part-${fileNumber}-${key}`
    const s3uri = `s3://${stagingBucketName}/${newKey}`

    const fileSize = await save(newKey, arrayLines)
    await addEvent(s3uri, fileSize, ddb)
  }
}

async function save(newKey: string, arrayLines: any): Promise<number> {
  const AWS = require('aws-sdk')
  const s3 = new AWS.S3()

  await s3
    .putObject({
      Bucket: stagingBucketName,
      Key: newKey,
      ContentType: 'text/csv',
      Body: arrayLines.join('\n'),
    })
    .promise()

  return s3.headObject({ Key: newKey, Bucket: stagingBucketName }).promise().ContentLength
}

async function addEvent(s3uri: string, fileSize: number, ddb: DocumentClient): Promise<any> {
  const { v4: uuidv4 } = require('uuid')

  const boosterEvent = {
    s3uri: s3uri,
    filesize: fileSize,
  }
  const params = {
    TableName: process.env.EVENT_STORE_NAME!,
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
