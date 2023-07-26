/* eslint-disable @typescript-eslint/ban-types */
import { EntitySnapshotEnvelope, EventEnvelope, EventStoreEntryEnvelope } from '@boostercloud/framework-types'
import { eventsDatabase } from '../paths'
const DataStore = require('@seald-io/nedb')

export class EventRegistry {
  private readonly events

  constructor() {
    this.events = new DataStore({ filename: eventsDatabase, autoload: true })
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
    let cursor = this.getCursor(query, createdAt, projections)
    if (limit) {
      cursor = cursor.limit(Number(limit))
    }
    return await cursor.execAsync()
  }

  public async queryLatestSnapshot(query: object): Promise<EntitySnapshotEnvelope | undefined> {
    const cursor = this.events.findAsync({ ...query, kind: 'snapshot' }).sort({ snapshottedEventCreatedAt: -1 }) // Sort in descending order (newer timestamps first)
    const results = await cursor.execAsync()
    if (results.length <= 0) {
      return undefined
    }
    return results[0] as EntitySnapshotEnvelope
  }

  public async store(storableObject: EventEnvelope | EntitySnapshotEnvelope): Promise<void> {
    await this.events.insertAsync(storableObject)
  }

  public async deleteAll(): Promise<number> {
    return await this.events.removeAsync({}, { multi: true })
  }

  public async count(query?: object): Promise<number> {
    return await this.events.countAsync(query)
  }
}
