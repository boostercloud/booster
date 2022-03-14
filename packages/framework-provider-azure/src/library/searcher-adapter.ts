import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, FilterFor, Logger, ReadModelListResult, SortFor } from '@boostercloud/framework-types'
import * as queryHelper from '../helpers/query-helper'

export async function searchReadModel(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: FilterFor<unknown>,
  sortByList?: Array<SortFor>,
  limit?: number,
  afterCursor?: Record<string, string> | undefined,
  paginatedVersion = false
): Promise<Array<any> | ReadModelListResult<any>> {
  const order = sortByList?.reduce((a, v) => ({ ...a, [v.field]: v.order }), {})
  return await queryHelper.search(
    cosmosDb,
    config,
    logger,
    config.resourceNames.forReadModel(readModelName),
    filters,
    limit,
    afterCursor,
    paginatedVersion,
    order
  )
}
