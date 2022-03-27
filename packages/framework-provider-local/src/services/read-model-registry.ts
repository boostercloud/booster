import { ReadModelEnvelope, SortFor, UUID } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { readModelsDatabase } from '../paths'

interface LocalSortedFor {
  [key: string]: number
}

export class ReadModelRegistry {
  public readonly readModels: DataStore<ReadModelEnvelope> = new DataStore(readModelsDatabase)
  constructor() {
    this.readModels.loadDatabase()
  }

  public async query(
    query: object,
    sortBy?: SortFor<unknown>,
    skip?: number,
    limit?: number
  ): Promise<Array<ReadModelEnvelope>> {
    let cursor = this.readModels.find(query)
    const sortByList = this.toLocalSortFor(sortBy)
    if (sortByList) {
      cursor = cursor.sort(sortByList)
    }
    if (skip) {
      cursor = cursor.skip(skip)
    }
    if (limit) {
      cursor = cursor.limit(limit)
    }
    const queryPromise = new Promise((resolve, reject) =>
      cursor.exec((err, docs) => {
        if (err) reject(err)
        else resolve(docs)
      })
    )

    return queryPromise as Promise<Array<ReadModelEnvelope>>
  }

  public async store(readModel: ReadModelEnvelope): Promise<void> {
    return new Promise((resolve, reject) => {
      this.readModels.update(
        //use nedb dot notation value.id to match the record (see https://github.com/louischatriot/nedb#finding-documents)
        { typeName: readModel.typeName, 'value.id': readModel.value.id },
        readModel,
        { upsert: true },
        (err) => {
          err ? reject(err) : resolve()
        }
      )
    })
  }

  public async deleteById(id: UUID): Promise<number> {
    const deletePromise = new Promise((resolve, reject) =>
      this.readModels.remove({ 'value.id': { id } }, { multi: true }, (err, numRemoved: number) => {
        if (err) reject(err)
        else resolve(numRemoved)
      })
    )

    return deletePromise as Promise<number>
  }

  toLocalSortFor(
    sortBy?: SortFor<unknown>,
    parentKey = '',
    sortedList: LocalSortedFor = {}
  ): undefined | LocalSortedFor {
    if (!sortBy || Object.keys(sortBy).length === 0) return
    const elements = sortBy!

    Object.entries(elements).forEach(([key, value]) => {
      if (typeof value === 'string') {
        sortedList[`${parentKey}${key}`] = (value as string) === 'ASC' ? 1 : -1
      } else {
        this.toLocalSortFor(value as SortFor<unknown>, `${parentKey}${key}.`, sortedList)
      }
    })
    return sortedList
  }
}
