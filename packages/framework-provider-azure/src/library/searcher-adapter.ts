import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, FilterFor, Logger, ReadModelListResult, SortFor } from '@boostercloud/framework-types'
import * as queryHelper from '../helpers/query-helper'

export async function searchReadModel(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: FilterFor<unknown>,
  sortBy?: SortFor<unknown>,
  limit?: number,
  afterCursor?: Record<string, string> | undefined,
  paginatedVersion = false
): Promise<Array<any> | ReadModelListResult<any>> {
  return await queryHelper.search(
    cosmosDb,
    config,
    logger,
    config.resourceNames.forReadModel(readModelName),
    filters,
    limit,
    afterCursor,
    paginatedVersion,
    sortBy
  )
}
