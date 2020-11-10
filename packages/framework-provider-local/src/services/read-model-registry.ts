import { ReadModelEnvelope, UUID } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { readModelsDatabase } from '../paths'

export class ReadModelRegistry {
  public readonly readModels: DataStore<ReadModelEnvelope> = new DataStore(readModelsDatabase)
  constructor() {
    this.readModels.loadDatabase()
  }

  public async query(query: object): Promise<Array<ReadModelEnvelope>> {
    const queryPromise = new Promise((resolve, reject) =>
      this.readModels.find(query).exec((err, docs) => {
        if (err) reject(err)
        else resolve(docs)
      })
    )

    return queryPromise as Promise<Array<ReadModelEnvelope>>
  }

  public async store(readModel: ReadModelEnvelope): Promise<void> {
    return new Promise((resolve, reject) => {
      this.readModels.update({ id: readModel.value.id }, readModel, { upsert: true }, (err) => {
        err ? reject(err) : resolve()
      })
    })
  }

  public async deleteById(id: UUID): Promise<number> {
    const deletePromise = new Promise((resolve, reject) =>
      this.readModels.remove({ value: { id } }, { multi: true }, (err, numRemoved: number) => {
        if (err) reject(err)
        else resolve(numRemoved)
      })
    )

    return deletePromise as Promise<number>
  }
}
