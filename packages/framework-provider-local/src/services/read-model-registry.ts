import { ProjectionFor, ReadModelEnvelope, SortFor, UUID } from '@boostercloud/framework-types'
import { readModelsDatabase } from '../paths'

const DataStore = require('@seald-io/nedb')

interface LocalSortedFor {
  [key: string]: number
}

interface LocalSelectFor {
  [key: string]: number
}

export type NedbError = Error & { [key: string | number | symbol]: unknown }

export const UNIQUE_VIOLATED_ERROR_TYPE = 'uniqueViolated'

export class ReadModelRegistry {
  public readonly readModels
  public isLoaded = false

  constructor() {
    this.readModels = new DataStore({ filename: readModelsDatabase })
  }

  async loadDatabaseIfNeeded(): Promise<void> {
    if (!this.isLoaded) {
      this.isLoaded = true
      await this.readModels.loadDatabaseAsync()
      await this.readModels.ensureIndexAsync({ fieldName: 'uniqueKey', unique: true, sparse: true })
    }
  }

  public async query(
    query: object,
    sortBy?: SortFor<unknown>,
    skip?: number,
    limit?: number,
    select?: ProjectionFor<unknown>
  ): Promise<Array<ReadModelEnvelope>> {
    await this.loadDatabaseIfNeeded()
    let cursor = this.readModels.find(query, this.toLocalSelectFor(select))
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

    const arrayFields: { [key: string]: string[] } = {}
    select?.forEach((field: string) => {
      const parts = field.split('.')
      const topLevelField = parts[0]
      if (topLevelField.endsWith('[]')) {
        const arrayField = topLevelField.slice(0, -2)
        if (!arrayFields[arrayField]) {
          arrayFields[arrayField] = []
        }
        const subField = parts.slice(1).join('.')
        if (subField) {
          arrayFields[arrayField].push(subField)
        }
      }
    })

    // Fetch results from the cursor
    const results = await cursor.execAsync()

    // Process each result to filter the array fields
    results.forEach((result: any) => {
      Object.keys(arrayFields).forEach((arrayField) => {
        const subFields = arrayFields[arrayField]
        if (result.value && Array.isArray(result.value[arrayField])) {
          result.value[arrayField] = result.value[arrayField].map((item: any) => {
            const filteredItem: { [key: string]: any } = {}
            subFields.forEach((subField) => {
              if (subField in item) {
                filteredItem[subField] = item[subField]
              }
            })
            return filteredItem
          })
        }
      })
    })

    return results
  }

  public async store(readModel: ReadModelEnvelope, expectedCurrentVersion: number): Promise<void> {
    await this.loadDatabaseIfNeeded()
    const uniqueReadModel: ReadModelEnvelope & { uniqueKey?: string } = readModel
    uniqueReadModel.uniqueKey = `${readModel.typeName}_${readModel.value.id}_${readModel.value.boosterMetadata?.version}`
    if (uniqueReadModel.value.boosterMetadata?.version === 1) {
      return this.insert(readModel)
    }
    return this.update(uniqueReadModel, expectedCurrentVersion)
  }

  private async insert(readModel: ReadModelEnvelope): Promise<void> {
    await this.loadDatabaseIfNeeded()
    await this.readModels.insertAsync(readModel)
  }

  private async update(readModel: ReadModelEnvelope, expectedCurrentVersion: number): Promise<void> {
    await this.loadDatabaseIfNeeded()
    const { numAffected } = await this.readModels.updateAsync(
      {
        typeName: readModel.typeName,
        'value.id': readModel.value.id,
        'value.boosterMetadata.version': expectedCurrentVersion,
      },
      readModel,
      { upsert: false, returnUpdatedDocs: true }
    )
    if (numAffected === 0) {
      const error: NedbError = new Error(
        `Can't update readModel ${JSON.stringify(
          readModel
        )} with expectedCurrentVersion = ${expectedCurrentVersion} . Optimistic concurrency error`
      ) as NedbError
      error.errorType = UNIQUE_VIOLATED_ERROR_TYPE
      throw error
    }
  }

  public async deleteById(id: UUID, typeName: string): Promise<number> {
    await this.loadDatabaseIfNeeded()
    return await this.readModels.removeAsync({ typeName: typeName, 'value.id': id }, { multi: false })
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

  toLocalSelectFor(select?: ProjectionFor<unknown>): LocalSelectFor {
    if (!select || select.length === 0) return {}

    const result: LocalSelectFor = {}
    const seenFields = new Set<string>()

    return select.reduce((acc: LocalSelectFor, field: string) => {
      // Split the field into parts
      const parts = field.split('.')
      const topLevelField = parts[0]

      // Check if the field is an array field
      if (topLevelField.endsWith('[]')) {
        const arrayField = `value.${topLevelField.slice(0, -2)}`

        // Only add the array field if it hasn't been added yet
        if (!seenFields.has(arrayField)) {
          seenFields.add(arrayField)
          return { ...acc, [arrayField]: 1}
        }
      } else {
        // Handle non-array fields normally
        return { ...acc, [`value.${field}`]: 1 }
      }
      return acc
    }, result)
  }
}
