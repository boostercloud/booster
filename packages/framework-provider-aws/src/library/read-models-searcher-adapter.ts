/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig, FilterFor, ReadModelListResult, SortFor } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { DynamoDB } from 'aws-sdk'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'
import { buildExpressionAttributeNames, buildExpressionAttributeValues, buildFilterExpression } from './query-helper'

export async function searchReadModel(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  readModelName: string,
  filters: FilterFor<unknown>,
  sortBy?: SortFor<unknown>,
  limit?: number,
  afterCursor?: DynamoDB.DocumentClient.Key | undefined,
  paginatedVersion = false
): Promise<Array<any> | ReadModelListResult<any>> {
  const logger = getLogger(config, 'read-model-searcher-adapter#searchReadModel')
  if (sortBy) {
    logger.info('SortBy not implemented for AWS provider. It will be ignored')
  }
  let params: DocumentClient.ScanInput = {
    TableName: config.resourceNames.forReadModel(readModelName),
    ConsistentRead: true,
    Limit: limit,
    ExclusiveStartKey: afterCursor,
  }
  if (filters && Object.keys(filters).length > 0) {
    params = {
      ...params,
      FilterExpression: buildFilterExpression(filters),
      ExpressionAttributeNames: buildExpressionAttributeNames(filters),
      ExpressionAttributeValues: buildExpressionAttributeValues(filters),
    }
  }

  logger.debug('Running search with the following params: \n', params)

  const result = await dynamoDB.scan(params).promise()

  logger.debug('Search result: ', result)

  if (paginatedVersion) {
    return {
      items: result.Items ?? [],
      count: result.Count,
      cursor: result.LastEvaluatedKey,
    }
  } else {
    return result.Items ?? []
  }
}
