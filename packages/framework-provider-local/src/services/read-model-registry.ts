import { ReadModelEnvelope, SortFor, UUID } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { readModelsDatabase } from '../paths'

interface LocalSortedFor {
  [key: string]: number
}

export type NedbError = Error & { [key: string | number | symbol]: unknown }

export const UNIQUE_VIOLATED_ERROR_TYPE = 'uniqueViolated'

export class ReadModelRegistry {
  public readonly readModels: DataStore<ReadModelEnvelope> = new DataStore(readModelsDatabase)
  constructor() {
    this.readModels.loadDatabase()
    this.readModels.ensureIndex({ fieldName: 'uniqueKey', unique: true, sparse: true })
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

  public async store(readModel: ReadModelEnvelope, expectedCurrentVersion: number): Promise<void> {
    const uniqueReadModel: ReadModelEnvelope & { uniqueKey?: string } = readModel
    uniqueReadModel.uniqueKey = `${readModel.typeName}_${readModel.value.id}_${readModel.value.boosterMetadata?.version}`
    if (uniqueReadModel.value.boosterMetadata?.version === 1) {
      return this.insert(readModel)
    }
    return this.update(uniqueReadModel, expectedCurrentVersion)
  }

  private insert(readModel: ReadModelEnvelope): Promise<void> {
    return new Promise((resolve, reject) => {
      this.readModels.insert(readModel, (err: unknown) => {
        err ? reject(err) : resolve()
      })
    })
  }

  private update(readModel: ReadModelEnvelope, expectedCurrentVersion: number): Promise<void> {
    return new Promise((resolve, reject) => {
      this.readModels.update(
        {
          typeName: readModel.typeName,
          'value.id': readModel.value.id,
          'value.boosterMetadata.version': expectedCurrentVersion,
        },
        readModel,
        { upsert: false, returnUpdatedDocs: true },
        (err: unknown, numAffected: number) => {
          if (numAffected === 0) {
            const error: NedbError = new Error(
              `Can't update readModel ${JSON.stringify(
                readModel
              )} with expectedCurrentVersion = ${expectedCurrentVersion} . Optimistic concurrency error`
            ) as NedbError
            error.errorType = UNIQUE_VIOLATED_ERROR_TYPE
            reject(error)
          }
          err ? reject(err) : resolve()
        }
      )
    })
  }

  public async deleteById(id: UUID, typeName: string): Promise<number> {
    const deletePromise = new Promise((resolve, reject) =>
      this.readModels.remove({ typeName: typeName, 'value.id': id }, { multi: false }, (err, numRemoved: number) => {
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
        sortedList[`value.${parentKey}${key}`] = (value as string) === 'ASC' ? 1 : -1
      } else {
        this.toLocalSortFor(value as SortFor<unknown>, `${parentKey}${key}.`, sortedList)
      }
    })
    return sortedList
  }
}
