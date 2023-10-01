import { createAsyncIterator } from 'iterall'
import {
  ReadModelRequestEnvelope,
  ReadModelInterface,
  Instance,
  Operation,
  BoosterConfig,
  ReadModelRequestProperties,
  FilterFor,
} from '@boostercloud/framework-types'
import { applyReadModelRequestBeforeFunctions } from '../filter-helpers'

export interface ReadModelPubSub<TReadModel extends ReadModelInterface> {
  asyncIterator(
    readModelRequestEnvelope: ReadModelRequestEnvelope<TReadModel>,
    config: BoosterConfig
  ): Promise<AsyncIterator<ReadModelInterface>>
}

export class FilteredReadModelPubSub<TReadModel extends ReadModelInterface> implements ReadModelPubSub<TReadModel> {
  constructor(private readModels: Array<ReadModelInterface & Instance>) {}

  public async asyncIterator(
    readModelRequestEnvelope: ReadModelRequestEnvelope<TReadModel>,
    config: BoosterConfig
  ): Promise<AsyncIterator<ReadModelInterface>> {
    const readModelMetadata = config.readModels[readModelRequestEnvelope.class.name]

    const newReadModelRequestEnvelope = await applyReadModelRequestBeforeFunctions(
      readModelRequestEnvelope,
      readModelMetadata.before,
      readModelRequestEnvelope.currentUser
    )

    return createAsyncIterator(
      this.readModels
        .filter((readModel) => newReadModelRequestEnvelope.class.name == readModel.constructor.name)
        .filter((readModel) => filterReadModel(readModel, newReadModelRequestEnvelope.filters))
    )
  }
}

function filterReadModel<TReadModel extends ReadModelInterface>(
  readModel: TReadModel,
  filters: ReadModelRequestProperties<TReadModel>
): boolean {
  for (const filteredProp in filters) {
    const readModelPropValue = readModel[filteredProp]
    return filterByOperation<TReadModel>(filters[filteredProp], readModelPropValue)
  }
  return true
}

function filterByOperation<TReadModel extends ReadModelInterface>(
  filter: FilterFor<TReadModel>,
  readModelPropValue: any
): boolean {
  for (const [operation, value] of Object.entries(filter as Operation<any>)) {
    switch (operation) {
      case 'eq':
        if (readModelPropValue !== value) return false
        break
      case 'ne':
        if (readModelPropValue === value) return false
        break
      case 'lt':
        if (readModelPropValue >= value) return false
        break
      case 'gt':
        if (readModelPropValue <= value) return false
        break
      case 'gte':
        if (readModelPropValue < value) return false
        break
      case 'lte':
        if (readModelPropValue > value) return false
        break
      case 'in':
        if (!value.includes(readModelPropValue)) return false
        break
      case 'contains':
        if (!contains(readModelPropValue, value)) return false
        break
      case 'beginsWith':
        if (!beginWith(readModelPropValue, value as string)) return false
        break
      case 'includes':
        return includes(readModelPropValue, value)
      case 'regex':
        if (!regex(readModelPropValue, value)) return false
        break
      case 'iRegex':
        if (!regex(readModelPropValue, value, 'i')) return false
        break
      default:
        if (typeof value === 'object') {
          return filterByOperation(value, readModelPropValue[operation])
        }
    }
  }
  return true
}

function regex(readModelPropValue: any, element: any, flags?: string): boolean {
  if (!Array.isArray(readModelPropValue) && typeof readModelPropValue === 'string') {
    const expression = new RegExp(element, flags)
    return expression.test(readModelPropValue)
  }
  return false
}

function contains(readModelPropValue: any, element: any): boolean {
  if (Array.isArray(readModelPropValue) || typeof readModelPropValue === 'string') {
    return readModelPropValue.includes(element)
  }
  return false
}

function beginWith(readModelPropValue: any, element: string): boolean {
  if (typeof readModelPropValue === 'string') {
    return readModelPropValue.startsWith(element)
  }
  return false
}

function includes(readModelPropValue: any, element: any): boolean {
  if (Array.isArray(readModelPropValue)) {
    // Check that array includes an object
    if (typeof element === 'object') {
      return readModelPropValue.some((item) => Object.keys(element).every((key) => item[key] === element[key]))
    }
    // If not, do a regular includes
    return readModelPropValue.includes(element)
  }
  return false
}
