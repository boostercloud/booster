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

    const arrayFields: { [key: string]: any } = {}
    select?.forEach((field: string) => {
      const parts = field.split('.')
      let currentLevel = arrayFields
      let isArrayField = false
      for (let i = 0; i < parts.length; ++i) {
        const part = parts[i]
        if (part.endsWith('[]')) {
          const arrayField = part.slice(0, -2)
          if (!currentLevel[arrayField]) {
            currentLevel[arrayField] = Object.create(null)
          }
          currentLevel = currentLevel[arrayField]
          isArrayField = true
        } else {
          if (i === parts.length - 1) {
            if (isArrayField) {
              if (!currentLevel['__fields']) {
                currentLevel['__fields'] = []
              }
              currentLevel['__fields'].push(part)
            }
          } else {
            if (!currentLevel[part]) {
              currentLevel[part] = Object.create(null)
            }
            currentLevel = currentLevel[part]
          }
        }
      }
    })

    // Fetch results from the cursor
    const results = await cursor.execAsync()

    // Process each result to filter the array fields
    results.forEach((result: any) => {
      result.value = this.filterObjectByArrayFields(result.value, arrayFields)
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

    select.forEach((field: string) => {
      const parts = field.split('.')
      const topLevelField = parts[0]

      if (topLevelField.endsWith('[]')) {
        const arrayField = `value.${topLevelField.slice(0, -2)}`
        if (!seenFields.has(arrayField)) {
          seenFields.add(arrayField)
          result[arrayField] = 1
        }
      } else {
        if (parts.some((part) => part.endsWith('[]'))) {
          const arrayIndex = parts.findIndex((part) => part.endsWith('[]'))
          const arrayField = `value.${parts
            .slice(0, arrayIndex + 1)
            .join('.')
            .slice(0, -2)}`
          if (!seenFields.has(arrayField)) {
            seenFields.add(arrayField)
            result[arrayField] = 1
          }
        } else {
          const fullPath = `value.${field}`
          if (!seenFields.has(fullPath)) {
            seenFields.add(fullPath)
            result[fullPath] = 1
          }
        }
      }
    })

    return result
  }

  filterArrayFields(item: any, fields: { [key: string]: any; __fields?: string[] }): any {
    const filteredItem: { [key: string]: any } = {}
    if (fields.__fields) {
      fields.__fields.forEach((field) => {
        if (field in item) {
          filteredItem[field] = item[field]
        }
      })
    }
    Object.keys(fields).forEach((key) => {
      if (key !== '__fields' && item[key] && Array.isArray(item[key])) {
        filteredItem[key] = item[key].map((subItem: any) => this.filterArrayFields(subItem, fields[key]))
      }
    })
    return filteredItem
  }

  filterObjectByArrayFields(obj: any, arrayFields: { [key: string]: any; __fields?: string[] }): any {
    const filteredObj: { [key: string]: any } = {}
    Object.keys(obj).forEach((key) => {
      if (key in arrayFields) {
        if (Array.isArray(obj[key])) {
          filteredObj[key] = obj[key].map((item: any) => this.filterArrayFields(item, arrayFields[key]))
        } else {
          filteredObj[key] = this.filterObjectByArrayFields(obj[key], arrayFields[key])
        }
      } else {
        filteredObj[key] = obj[key]
      }
    })
    return filteredObj
  }
}
