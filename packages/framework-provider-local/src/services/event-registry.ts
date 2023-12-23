/* eslint-disable @typescript-eslint/ban-types */
import { EntitySnapshotEnvelope, EventEnvelope, EventStoreEntryEnvelope } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { eventsDatabase } from '../paths'

export class EventRegistry {
  public readonly events: DataStore<EventStoreEntryEnvelope> = new DataStore(eventsDatabase)
  constructor() {
    this.events.loadDatabase()
  }

  getCursor(query: object, createdAt = 1, projections?: unknown) {
    return this.events.find(query, projections).sort({ createdAt: createdAt })
  }

  public async query(
    query: object,
    createdAt = 1,
    limit?: number,
    projections?: unknown
  ): Promise<EventStoreEntryEnvelope[]> {
    const cursor = this.getCursor(query, createdAt, projections)
    if (limit) {
      cursor.limit(Number(limit))
    }
    const queryPromise = await new Promise<EventStoreEntryEnvelope[]>((resolve, reject) => {
      cursor.exec((err, docs) => {
        if (err) reject(err)
        else resolve(docs)
      })
    })

    return queryPromise
  }

  public async replaceOrDeleteItem(id: string, newValue?: EventEnvelope | EntitySnapshotEnvelope): Promise<void> {
    if (newValue) {
      await new Promise((resolve, reject) =>
        this.events.update({ _id: id }, newValue, { multi: true }, (err, numRemoved: number) => {
          if (err) reject(err)
          else resolve(numRemoved)
        })
      )
    } else {
      await new Promise((resolve, reject) =>
        this.events.remove({ _id: id }, { multi: true }, (err, numRemoved: number) => {
          if (err) reject(err)
          else resolve(numRemoved)
        })
      )
    }
  }

  public async queryLatestSnapshot(query: object): Promise<EntitySnapshotEnvelope | undefined> {
    const results = await new Promise<EventStoreEntryEnvelope[]>((resolve, reject) =>
      this.events
        .find({ ...query, kind: 'snapshot' })
        .sort({ snapshottedEventCreatedAt: -1 }) // Sort in descending order (newer timestamps first)
        .exec((err, docs) => {
          if (err) reject(err)
          else resolve(docs)
        })
    )

    if (results.length <= 0) {
      return undefined
    }
    return results[0] as EntitySnapshotEnvelope
  }

  public async store(storableObject: EventEnvelope | EntitySnapshotEnvelope): Promise<void> {
    return new Promise((resolve, reject) => {
      this.events.insert(storableObject, (err) => {
        err ? reject(err) : resolve()
      })
    })
  }

  public async deleteAll(): Promise<number> {
    const deletePromise = new Promise((resolve, reject) =>
      this.events.remove({}, { multi: true }, (err, numRemoved: number) => {
        if (err) reject(err)
        else resolve(numRemoved)
      })
    )

    return (await deletePromise) as number
  }
}
