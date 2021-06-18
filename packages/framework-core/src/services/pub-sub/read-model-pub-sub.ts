import { createAsyncIterator } from 'iterall'
import {
  ReadModelRequestEnvelope,
  ReadModelInterface,
  ReadModelPropertyFilter,
  Instance,
  Operation,
  BoosterConfig,
} from '@boostercloud/framework-types'
import { getReadModelFilters } from '../../utils'

export interface ReadModelPubSub {
  asyncIterator(
    readModelRequestEnvelope: ReadModelRequestEnvelope,
    config: BoosterConfig
  ): AsyncIterator<ReadModelInterface>
}

export class FilteredReadModelPubSub implements ReadModelPubSub {
  constructor(private readModels: Array<ReadModelInterface & Instance>) {}

  public asyncIterator(
    readModelRequestEnvelope: ReadModelRequestEnvelope,
    config: BoosterConfig
  ): AsyncIterator<ReadModelInterface> {
    const readModelMetadata = config.readModels[readModelRequestEnvelope.typeName]

    const filters = getReadModelFilters(
      readModelRequestEnvelope.filters,
      readModelMetadata.before,
      readModelRequestEnvelope.currentUser
    ) as Record<string, ReadModelPropertyFilter>

    return createAsyncIterator(
      this.readModels
        .filter((readModel) => readModelRequestEnvelope.typeName == readModel.constructor.name)
        .filter((readModel) => filterReadModel(readModel, filters))
    )
  }
}

function filterReadModel(readModel: Record<string, any>, filters?: Record<string, ReadModelPropertyFilter>): boolean {
  if (!filters) {
    return true
  }
  for (const filteredProp in filters) {
    const readModelPropValue = readModel[filteredProp]
    return filterByOperation(filters[filteredProp], readModelPropValue)
  }
  return true
}

function filterByOperation(filter: ReadModelPropertyFilter, readModelPropValue: any): boolean {
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
      default:
        if (typeof value === 'object') {
          return filterByOperation(value, readModelPropValue[operation])
        }
    }
  }
  return true
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
  if (!Array.isArray(readModelPropValue)) return false
  if (readModelPropValue.includes(element)) return true
  return readModelPropValue.some((prop: any) => Object.keys(prop).some((key) => prop[key] === element[key]))
}
