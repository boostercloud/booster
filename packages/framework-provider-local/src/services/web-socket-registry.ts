/* eslint-disable @typescript-eslint/ban-types */
import * as DataStore from 'nedb'
import { ConnectionDataEnvelope, EventEnvelope, SubscriptionEnvelope, UUID } from '@boostercloud/framework-types'

export interface ConnectionData extends ConnectionDataEnvelope {
  connectionID: UUID
}

export type SimpleRegistryTypes = ConnectionData | SubscriptionEnvelope

export class WebSocketRegistry {
  public datastore: DataStore<SimpleRegistryTypes>

  constructor(connectionsDatabase: string) {
    this.datastore = new DataStore(connectionsDatabase)
    this.datastore.loadDatabase()
    this.addIndexes()
  }

  addIndexes(): void {
    const maxDurationInSeconds = 2 * 24 * 60 * 60 // 2 days
    this.datastore.ensureIndex({ fieldName: 'expirationTime', expireAfterSeconds: maxDurationInSeconds })
  }

  getCursor(query: object, createdAt = 1, projections?: unknown) {
    return this.datastore.find(query, projections).sort({ createdAt: createdAt })
  }

  public async query(query: object, createdAt = 1, limit?: number, projections?: unknown): Promise<unknown> {
    const cursor = this.getCursor(query, createdAt, projections)
    if (limit) {
      cursor.limit(Number(limit))
    }
    const queryPromise = new Promise((resolve, reject) => {
      cursor.exec((err, docs) => {
        if (err) reject(err)
        else resolve(docs)
      })
    })

    return await queryPromise
  }

  public async queryLatest(query: object): Promise<EventEnvelope | null> {
    const queryPromise = new Promise((resolve, reject) =>
      this.datastore
        .find(query)
        .sort({ createdAt: -1 }) // Sort in descending order (newer timestamps first)
        .exec((err, docs) => {
          if (err) reject(err)
          else resolve(docs)
        })
    )

    const events = (await queryPromise) as Array<EventEnvelope>
    if (events.length <= 0) {
      return null
    }
    return events[0]
  }

  public async store(envelope: SimpleRegistryTypes): Promise<void> {
    return new Promise((resolve, reject) => {
      this.datastore.insert(envelope, (err) => {
        err ? reject(err) : resolve()
      })
    })
  }

  public async delete(query: unknown): Promise<number> {
    const deletePromise = new Promise((resolve, reject) =>
      this.datastore.remove(query, { multi: true }, (err, numRemoved: number) => {
        if (err) reject(err)
        else resolve(numRemoved)
      })
    )

    return (await deletePromise) as number
  }

  public async deleteAll(): Promise<number> {
    const deletePromise = new Promise((resolve, reject) =>
      this.datastore.remove({}, { multi: true }, (err, numRemoved: number) => {
        if (err) reject(err)
        else resolve(numRemoved)
      })
    )

    return (await deletePromise) as number
  }
}
