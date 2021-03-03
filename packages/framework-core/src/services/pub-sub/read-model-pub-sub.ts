import { createAsyncIterator } from 'iterall'
import {
  ReadModelRequestEnvelope,
  ReadModelInterface,
  ReadModelPropertyFilter,
  Instance,
  Operation,
} from '@boostercloud/framework-types'

export interface ReadModelPubSub {
  asyncIterator(readModelRequestEnvelope: ReadModelRequestEnvelope): AsyncIterator<ReadModelInterface>
}

export class FilteredReadModelPubSub implements ReadModelPubSub {
  constructor(private readModels: Array<ReadModelInterface & Instance>) {}

  public asyncIterator(readModelRequestEnvelope: ReadModelRequestEnvelope): AsyncIterator<ReadModelInterface> {
    return createAsyncIterator(
      this.readModels
        .filter((readModel) => readModelRequestEnvelope.typeName == readModel.constructor.name)
        .filter((readModel) => filterReadModel(readModel, readModelRequestEnvelope.filters))
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
      case '=':
        if (readModelPropValue !== value) return false
        break
      case '!=':
        if (readModelPropValue === value) return false
        break
      case '<':
        if (readModelPropValue >= value) return false
        break
      case '>':
        if (readModelPropValue <= value) return false
        break
      case '>=':
        if (readModelPropValue < value) return false
        break
      case '<=':
        if (readModelPropValue > value) return false
        break
      case 'in':
        if (!value.includes(readModelPropValue)) return false
        break
      case 'contains':
        if (!contains(readModelPropValue, value)) return false
        break
      case 'not-contains':
        if (contains(readModelPropValue, value)) return false
        break
      case 'begins-with':
        if (!beginWith(readModelPropValue, value as string)) return false
        break
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
