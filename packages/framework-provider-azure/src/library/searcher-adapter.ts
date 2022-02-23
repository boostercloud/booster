import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, Logger, FilterFor, ReadModelListResult } from '@boostercloud/framework-types'
import { search } from '../helpers/query-helper'

export async function searchReadModel(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: FilterFor<unknown>,
  limit?: number,
  afterCursor?: Record<string, string> | undefined,
  paginatedVersion = false
): Promise<Array<any> | ReadModelListResult<any>> {
  return await search(
    cosmosDb,
    config,
    logger,
    config.resourceNames.forReadModel(readModelName),
    filters,
    limit,
    afterCursor,
    paginatedVersion
  )
}
