/* eslint-disable @typescript-eslint/ban-types */
import { EventEnvelope } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { eventsDatabase } from '../paths'

export class EventRegistry {
  public readonly events: DataStore<EventEnvelope> = new DataStore(eventsDatabase)
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
    skip?: number,
    projections?: unknown
  ): Promise<unknown> {
    const cursor = this.getCursor(query, createdAt, projections)
    if (limit) {
      cursor.limit(Number(limit))
    }
    if (skip) {
      cursor.skip(skip)
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
      this.events
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

  public async store(event: EventEnvelope): Promise<void> {
    return new Promise((resolve, reject) => {
      this.events.insert(event, (err) => {
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
