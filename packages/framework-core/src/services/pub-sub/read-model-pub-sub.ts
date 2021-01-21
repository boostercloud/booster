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
    console.log('*** pub-sub', { filteredProp, filters })
    const readModelPropValue = readModel[filteredProp]
    // const operation: ReadModelPropertyFilter = filters[filteredProp]
    for (const [operation, value] of Object.entries(filters[filteredProp] as Operation<any>)) {
      console.log({ filters, filteredProp, operation, value })
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
          // if (!values.includes(readModelPropValue)) return false
          break
        case 'between':
          // if (readModelPropValue < value || readModelPropValue > values[1]) return false
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
