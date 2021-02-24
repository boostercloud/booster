import { DynamoDB, S3 } from 'aws-sdk'
import { S3Event } from 'aws-lambda'

const stagingBucketName = process.env.STAGING_BUCKET_NAME!
const CSV_FILE = 'csv'
const JSONL_FILE = 'jsonl'

export const handler = async (event: S3Event): Promise<void> => {
  const s3 = new S3()

  const {
    bucket: { name },
    object: { key },
  } = event.Records[0].s3

  const params = {
    Bucket: name,
    Key: key,
  }
  const data = await s3.getObject(params).promise()
  const fileExtension = key.split('.').pop()

  if (fileExtension != CSV_FILE && fileExtension != JSONL_FILE) {
    throw Error(`[ROCKET#batch-file] The provided fileExtension ${fileExtension} is not supported.`)
  }

  if (data.Body) {
    const content = data.Body.toString()

    let chunkContent: string[] = []
    const rows = content.split('\n')

    if (rows.length == 0) {
      throw Error('[ROCKET#batch-file] The input file is empty.')
    }

    if (rows.length == 1) {
      if (fileExtension == CSV_FILE) {
        throw Error('[ROCKET#batch-file] Looks like the input CSV file only contains the headers row.')
      } else {
        throw Error('[ROCKET#batch-file] Looks like the input jsonl file only contains the first metadata row.')
      }
    }

    let currentRowNumber = 0
    let fileNumber = 1
    const firstRow: string = rows.shift()!

    while (currentRowNumber < rows.length) {
      if (chunkContent.length != 0 && chunkContent.length % Number(process.env.CHUNK_SIZE) == 0) {
        const partKey = `part-${fileNumber}-${key}`
        chunkContent.unshift(firstRow)

        await saveFile(partKey, chunkContent)
        await saveEvent(partKey)

        fileNumber = fileNumber + 1
        chunkContent = []
      }
      if (rows[currentRowNumber]) {
        chunkContent.push(rows[currentRowNumber])
      }

      currentRowNumber = currentRowNumber + 1
    }

    if (chunkContent.length != 0) {
      const partKey = `part-${fileNumber}-${key}`
      chunkContent.unshift(firstRow)

      await saveFile(partKey, chunkContent)
      await saveEvent(partKey)
    }
  } else {
    console.log(`[ROCKET#batch-file] File ${key} can't be found in bucket ${name}`)
  }
}

async function saveFile(key: string, content: string[]): Promise<void> {
  const s3 = new S3()

  try {
    await s3
      .putObject({
        Bucket: stagingBucketName,
        Key: key,
        ContentType: 'text/csv',
        Body: content.join('\n'),
      })
      .promise()
  } catch (e) {
    console.log('[ROCKET#batch-file] An error occurred while performing a PutObject operation: ', e)
  }
}

async function saveEvent(key: string): Promise<void> {
  const { v4: uuidv4 } = require('uuid')
  const ddb = new DynamoDB.DocumentClient()
  const s3 = new S3()

  const fileURI = `s3://${stagingBucketName}/${key}`
  const fileSize = (await s3.headObject({ Key: key, Bucket: stagingBucketName }).promise()).ContentLength

  const boosterEvent = {
    fileURI: fileURI,
    filesize: fileSize,
  }

  const params = {
    TableName: process.env.EVENT_STORE_NAME!,
    Item: {
      createdAt: new Date().toISOString(),
      entityID: fileURI,
      entityTypeName: process.env.ENTITY_TYPE_NAME,
      entityTypeName_entityID_kind: `${process.env.ENTITY_TYPE_NAME}-${fileURI}-event`,
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
