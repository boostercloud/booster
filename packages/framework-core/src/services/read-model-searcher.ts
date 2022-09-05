import {
  BoosterConfig,
  Class,
  FilterFor,
  FinderByKeyFunction,
  ReadModelInterface,
  ReadOnlyNonEmptyArray,
  Searcher,
  SearcherFunction,
  SequenceKey,
  SortFor,
  UUID,
} from '@boostercloud/framework-types'
import { createInstances } from '@boostercloud/framework-common-helpers'

export function readModelSearcher<TReadModel extends ReadModelInterface>(
  config: BoosterConfig,
  readModelClass: Class<TReadModel>
): Searcher<TReadModel> {
  const searchFunction: SearcherFunction<TReadModel> = async (
    readModelName: string,
    filters: FilterFor<unknown>,
    sort?: SortFor<unknown>,
    limit?: number,
    afterCursor?: any,
    paginatedVersion?: boolean
  ) => {
    const searchResult = await config.provider.readModels.search(
      config,
      readModelName,
      filters,
      sort,
      limit,
      afterCursor,
      paginatedVersion
    )

    if (!Array.isArray(searchResult)) {
      return {
        ...searchResult,
        items: createInstances(readModelClass, searchResult.items),
      }
    }
    return createInstances(readModelClass, searchResult)
  }

  const finderByIdFunction: FinderByKeyFunction<TReadModel> = async (
    readModelName: string,
    id: UUID,
    sequenceKey?: SequenceKey
  ) => {
    const readModels = await config.provider.readModels.fetch(config, readModelName, id, sequenceKey)
    if (sequenceKey) {
      return readModels as ReadOnlyNonEmptyArray<TReadModel>
    }
    return readModels[0] as TReadModel
  }
  return new Searcher(readModelClass, searchFunction, finderByIdFunction)
}
