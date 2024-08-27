/* eslint-disable @typescript-eslint/ban-types */
import { EntitySnapshotEnvelope, EventEnvelope, EventStoreEntryEnvelope } from '@boostercloud/framework-types'
import { eventsDatabase } from '../paths'

const DataStore = require('@seald-io/nedb')

export class EventRegistry {
  public readonly events
  public isLoaded = false

  constructor() {
    this.events = new DataStore({ filename: eventsDatabase })
  }

  async loadDatabaseIfNeeded(): Promise<void> {
    if (!this.isLoaded) {
      this.isLoaded = true
      await this.events.loadDatabaseAsync()
    }
  }

  getCursor(query: object, createdAt = 1, projections?: unknown) {
    const cursor = this.events.findAsync(query, projections)
    return cursor.sort({ createdAt: createdAt })
  }

  public async query(
    query: object,
    createdAt = 1,
    limit?: number,
    projections?: unknown
  ): Promise<EventStoreEntryEnvelope[]> {
    await this.loadDatabaseIfNeeded()
    let cursor = this.getCursor(query, createdAt, projections)
    if (limit) {
      cursor = cursor.limit(Number(limit))
    }
    return await cursor.execAsync()
  }

  public async replaceOrDeleteItem(id: string, newValue?: EventEnvelope | EntitySnapshotEnvelope): Promise<void> {
    if (newValue) {
      await new Promise((resolve, reject) =>
        this.events.update({ _id: id }, newValue, { multi: true }, (err: any, numRemoved: number) => {
          if (err) reject(err)
          else resolve(numRemoved)
        })
      )
    } else {
      await new Promise((resolve, reject) =>
        this.events.remove({ _id: id }, { multi: true }, (err: any, numRemoved: number) => {
          if (err) reject(err)
          else resolve(numRemoved)
        })
      )
    }
  }

  public async queryLatestSnapshot(query: object): Promise<EntitySnapshotEnvelope | undefined> {
    await this.loadDatabaseIfNeeded()
    const cursor = this.events.findAsync({ ...query, kind: 'snapshot' }).sort({ snapshottedEventCreatedAt: -1 }) // Sort in descending order (newer timestamps first)
    const results = await cursor.execAsync()
    if (results.length <= 0) {
      return undefined
    }
    return results[0] as EntitySnapshotEnvelope
  }

  public async store(storableObject: EventEnvelope | EntitySnapshotEnvelope): Promise<void> {
    await this.loadDatabaseIfNeeded()
    await this.events.insertAsync(storableObject)
  }

  public async deleteAll(): Promise<number> {
    await this.loadDatabaseIfNeeded()
    return await this.events.removeAsync({}, { multi: true })
  }

  public async count(query?: object): Promise<number> {
    await this.loadDatabaseIfNeeded()
    return await this.events.countAsync(query)
  }
}
