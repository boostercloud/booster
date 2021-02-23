import { S3Event } from 'aws-lambda'
import { DynamoDB, S3 } from 'aws-sdk'

const CSV_FILE = 'csv'
const JSONL_FILE = 'jsonl'

const s3 = new S3()
const ddb = new DynamoDB.DocumentClient()
const { v4: uuidv4 } = require('uuid')

const ENTITY_ID_NAME = process.env.ENTITY_ID!

export const handler = async (event: S3Event): Promise<void> => {
  const {
    bucket: { name },
    object: { key },
  } = event.Records[0].s3

  const params = {
    Bucket: name,
    Key: key,
  }

  const data = await s3.getObject(params).promise()
  if (data.Body) {
    const fileExtension = key.split('.').pop()
    const rows = data.Body.toString().split('\n')

    if (fileExtension == CSV_FILE) {
      await csvRowsToEvents(rows)
    } else if (fileExtension == JSONL_FILE) {
      await jsonlRowsToEvents(rows)
    } else {
      throw Error('File Format is not supported')
    }
  }
}

async function csvRowsToEvents(rows: string[]): Promise<void> {
  const headers: string[] = rows.shift()!.split(',')
  const indexOfEntityId = headers.indexOf(ENTITY_ID_NAME)

  for (let rowNumber = 0; rowNumber < rows.length; rowNumber++) {
    const rowValues: string[] = rows[rowNumber].split(',')
    const entityIdValue = rowValues[indexOfEntityId]

    let index = 0
    const boosterEvent = {} as any

    for (let headerNumber = 0; headerNumber < headers.length; headerNumber++) {
      boosterEvent[headers[headerNumber]] = rowValues[index]
      index++
    }

    await saveEvent(entityIdValue, boosterEvent)
  }
}

async function jsonlRowsToEvents(rows: string[]): Promise<void> {
  rows.shift()!

  for (let rowNumber = 0; rowNumber < rows.length; rowNumber++) {
    const boosterEvent = JSON.parse(rows[rowNumber])
    const entityIdValue = boosterEvent[ENTITY_ID_NAME]
    await saveEvent(entityIdValue, boosterEvent)
  }
}

async function saveEvent(entityIdValue: string, boosterEvent: any): Promise<void> {
  const params = {
    TableName: process.env.EVENT_STORE_NAME!,
    Item: {
      createdAt: new Date().toISOString(),
      entityID: entityIdValue,
      entityTypeName: process.env.ENTITY_TYPE_NAME,
      entityTypeName_entityID_kind: `${process.env.ENTITY_TYPE_NAME}-${entityIdValue}-event`,
      kind: 'event',
      requestID: uuidv4(),
      typeName: process.env.TYPE_NAME,
      value: boosterEvent,
      version: 1,
    },
  }

  try {
    await ddb.put(params).promise()
  } catch (e) {
    console.log('[ROCKET#batch-file] An error occurred while performing a PutItem operation: ', e)
  }
}
