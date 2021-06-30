import { BoosterConfig, Logger, FilterFor } from '@boostercloud/framework-types'
import { DatabaseAdapter } from '../services/database-adapter'
import { EntityType } from '../types/query'

export async function searchReadModel(
  databaseAdapter: DatabaseAdapter,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: FilterFor<unknown>
): Promise<Array<any>> {
  logger.debug('Running search with the following filters: \n', filters)

  const query = {
    type: EntityType.ReadModel,
    typeName: readModelName,
    keyPredicate: () => true,
    valuePredicate: () => true,
    sortBy: () => 1,
  }

  const keys = await databaseAdapter.query(query, logger)
  const data: Array<any> = []
  keys.map((element: any) => data.push(element.value))
  return data
  /*logger.debug(`Obtainer following keys for query: ${keys}`)
  const results: Array<unknown> = []
  await Promise.all(
    keys.map(async (k: string) => {
      const data = await databaseAdapter.hget<ReadModelEnvelope>(k)
      if (data?.value) {
        results.push(data.value)
      }
    })
  )
  logger.debug(`Got ${results} envelopes, returning`)
  if (!Object.keys(filters).length) return results

  const [filtered] = filterResults(results, filters)
  return filtered*/
}

/*function filterResults(results: Array<unknown>, filters: FilterFor<unknown> = {}): Array<any> {
  return Object.entries(filters).map(([propName, filter]) => {
    if (['and', 'or', 'not'].includes(propName)) {
      throw new Error('Filter combinators not yet supported')
    } else {
      return filterByOperation(propName, results, filter)
    }
  })
}*/

/*function filterByOperation(propName: string, input: Array<any>, filter: Operation<any>): Array<unknown> {
  const [res] = Object.entries(filter).map(([operation, value]) => {
    switch (operation) {
      case 'eq':
        return input.filter((obj) => obj[propName] === value)
      case 'ne':
        return input.filter((obj) => obj[propName] !== value)
      case 'lt':
        return input.filter((obj) => obj[propName] < value)
      case 'gt':
        return input.filter((obj) => obj[propName] > value)
      case 'gte':
        return input.filter((obj) => obj[propName] >= value)
      case 'lte':
        return input.filter((obj) => obj[propName] <= value)
      case 'in':
        return input.filter((obj) => (value as Array<any>).includes(obj[propName]))
      case 'contains':
        return input.filter((obj) => (obj[propName] as string).includes(value))
      case 'beginsWith':
        return input.filter((obj) => (obj[propName] as string).startsWith(value))
      case 'includes':
        return input.filter((obj) => (obj[propName] as Array<any>).includes(value))
      default:
        if (typeof value === 'object') {
          throw new InvalidParameterError('Complex properties operators not yet supported on K8s')
        }
        throw new InvalidParameterError(`Operator "${operation}" is not supported`)
    }
  })
  return res
}*/
