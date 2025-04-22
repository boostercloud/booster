import {
  BoosterConfig,
  Class,
  FinderByKeyFunction,
  ReadModelInterface,
  Searcher,
  SearcherFunction,
} from '@boostercloud/framework-types'
import { BoosterReadModelsReader } from '../booster-read-models-reader'

export function readModelSearcher<TReadModel extends ReadModelInterface>(
  config: BoosterConfig,
  readModelClass: Class<TReadModel>
): Searcher<TReadModel> {
  const boosterReadModelsReader = new BoosterReadModelsReader(config)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const searcherFunction: SearcherFunction<TReadModel, any> =
    boosterReadModelsReader.readModelSearch.bind(boosterReadModelsReader)
  const finderByIdFunction: FinderByKeyFunction<TReadModel> =
    boosterReadModelsReader.finderByIdFunction.bind(boosterReadModelsReader)
  return new Searcher(readModelClass, searcherFunction, finderByIdFunction)
}
