/* eslint-disable @typescript-eslint/ban-types */
const DataStore = require('@seald-io/nedb')
import { ConnectionDataEnvelope, SubscriptionEnvelope, UUID } from '@boostercloud/framework-types'

export interface ConnectionData extends ConnectionDataEnvelope {
  connectionID: UUID
}

export type SimpleRegistryTypes = ConnectionData | SubscriptionEnvelope

export class WebSocketRegistry {
  public datastore

  constructor(connectionsDatabase: string) {
    this.datastore = new DataStore({ filename: connectionsDatabase, autoload: true })
    this.addIndexes()
  }

  addIndexes(): void {
    const maxDurationInSeconds = 2 * 24 * 60 * 60 // 2 days
    this.datastore.ensureIndex({ fieldName: 'expirationTime', expireAfterSeconds: maxDurationInSeconds })
  }

  getCursor(query: object, createdAt = 1, projections?: unknown) {
    return this.datastore.findAsync(query, projections).sort({ createdAt: createdAt })
  }

  public async query(query: object, createdAt = 1, limit?: number, projections?: unknown): Promise<unknown> {
    let cursor = this.getCursor(query, createdAt, projections)
    if (limit) {
      cursor = cursor.limit(Number(limit))
    }
    return await cursor.execAsync()
  }

  public async store(envelope: SimpleRegistryTypes): Promise<void> {
    await this.datastore.insertAsync(envelope)
  }

  public async delete(query: unknown): Promise<number> {
    return await this.datastore.removeAsync(query, { multi: true })
  }

  public async deleteAll(): Promise<number> {
    return await this.datastore.removeAsync({}, { multi: true })
  }

  public async count(query?: object): Promise<number> {
    return await this.datastore.countAsync(query)
  }
}
