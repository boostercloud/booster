import { DynamoDB } from 'aws-sdk'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'
import QueryOutput = DocumentClient.QueryOutput
import ScanOutput = DocumentClient.ScanOutput
import { applicationName } from '../../helper/app-helper'

const documentClient = new DynamoDB.DocumentClient()

// --- Stack Helpers ---

export function appStackName(): string {
  return `${applicationName()}-app`
}

// --- Events store helpers ---

export async function eventsStoreTableName(): Promise<string> {
  const stackName = appStackName()

  return `${stackName}-events-store`
}

export async function queryEvents(primaryKey: string, latestFirst = true): Promise<any> {
  const output: QueryOutput = await documentClient
    .query({
      TableName: await eventsStoreTableName(),
      ConsistentRead: true,
      KeyConditionExpression: 'entityTypeName_entityID_kind = :v',
      ExpressionAttributeValues: { ':v': primaryKey },
      ScanIndexForward: !latestFirst,
    })
    .promise()

  return output.Items
}

// --- Subscriptions store helpers ---
export async function countSubscriptionsItems(): Promise<number> {
  return countTableItems(`${appStackName()}-subscriptions-store`)
}

export async function countConnectionsItems(): Promise<number> {
  return countTableItems(`${appStackName()}-connections-store`)
}

// --- Read models helpers ---

export async function readModelTableName(readModelName: string): Promise<string> {
  const stackName = appStackName()

  return `${stackName}-${readModelName}`
}

export async function queryReadModels(primaryKey: string, readModelName: string, latestFirst = true): Promise<any> {
  const output: QueryOutput = await documentClient
    .query({
      TableName: await readModelTableName(readModelName),
      KeyConditionExpression: 'id = :v',
      ExpressionAttributeValues: { ':v': primaryKey },
      ScanIndexForward: !latestFirst,
    })
    .promise()

  return output.Items
}

export async function countReadModelItems(readModelName: string): Promise<number> {
  const tableName = await readModelTableName(readModelName)

  return countTableItems(tableName)
}

// --- DynamoDB helpers ---

export async function countTableItems(tableName: string): Promise<number> {
  const output: ScanOutput = await documentClient
    .scan({
      TableName: tableName,
      Select: 'COUNT',
    })
    .promise()

  return output.Count ?? -1
}

export async function countEventItems(): Promise<number> {
  // TODO: This way of counting is wrong when there are a lot of events in the event store, as COUNT will only contain
  // the number of elements in a a result set that is lower than 1MB in size:
  // "If the size of the Query result set is larger than 1 MB, then ScannedCount and Count
  // will represent only a partial count of the total items.
  // You will need to perform multiple Query operations in order to retrieve all of the results\"."
  const output: ScanOutput = await documentClient
    .scan({
      TableName: await eventsStoreTableName(),
      Select: 'COUNT',
      FilterExpression: '#k = :kind',
      ExpressionAttributeNames: { '#k': 'kind' },
      ExpressionAttributeValues: { ':kind': 'event' },
    })
    .promise()

  return output.Count ?? -1
}
// --- Other helpers ---

export async function waitForIt<TResult>(
  tryFunction: () => Promise<TResult>,
  checkResult: (result: TResult) => boolean,
  trialDelayMs = 1000,
  timeoutMs = 60000
): Promise<TResult> {
  console.debug('[waitForIt] start')
  const start = Date.now()
  return doWaitFor()

  async function doWaitFor(): Promise<TResult> {
    console.debug('.')
    const res = await tryFunction()
    if (checkResult(res)) {
      console.debug('[waitForIt] match!')
      return res
    }
    const elapsed = Date.now() - start

    if (elapsed > timeoutMs) {
      throw new Error('[waitForIt] Timeout reached')
    }

    await sleep(trialDelayMs)
    return doWaitFor()
  }
}

// This helper will create a valid token using a real private key for testing
// the tokens will be validate against the public keyset file
// located in: /keys/private.key file
export const getTokenForUser = (email: string, role: string): string => {
  const privateKey = fs.readFileSync(__dirname + '/keys/private.key')
  const keyid = 'booster'
  const issuer = 'booster'
  const token = jwt.sign(
    {
      id: email,
      'booster:role': role,
      email,
    },
    privateKey,
    {
      algorithm: 'RS256',
      subject: email,
      issuer,
      keyid,
    }
  )
  return token
}
