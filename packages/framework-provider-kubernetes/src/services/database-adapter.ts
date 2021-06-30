import { EventEnvelope, Logger, UUID, ReadModelEnvelope } from '@boostercloud/framework-types'
import fetch from 'node-fetch'
import { Schema, model, models, connect, Document, Model } from 'mongoose'
import { Query } from '../types/query'

interface EventStoreDocument extends Document {
  _id: string
  value: string
}

export class DatabaseAdapter {
  private keySeparator = '||'
  private daprUrl: string
  private dbHost: string
  private dbUser: string
  private dbPassword: string | null
  private eventStoreModel: Model<EventStoreDocument>

  private dbSchema = new Schema<EventStoreDocument>(
    {
      _id: { type: String, required: true },
      value: { type: String, required: true },
    },
    { collection: 'daprCollection' }
  )

  constructor() {
    const daprPort = process.env['DAPR_HTTP_PORT'] || 3500
    this.daprUrl = `http://localhost:${daprPort}`
    this.dbHost = process.env['DB_HOST'] || 'host'
    this.dbUser = process.env['DB_USER'] || 'boosteruser'
    this.dbPassword = null
    this.eventStoreModel =
      models && models.EventStore ? models.EventStore : model<EventStoreDocument>('EventStore', this.dbSchema)
  }

  public async storeEvent(value: EventEnvelope, logger: Logger): Promise<void> {
    const eventKey = this.getEventKey(value)
    await this.store(eventKey, value, logger)
  }

  public async storeReadModel(value: ReadModelEnvelope, logger: Logger): Promise<void> {
    const readModelKey = this.getReadModelKey(value)
    await this.store(readModelKey, value, logger)
  }
  private async store(key: string, value: EventEnvelope | ReadModelEnvelope, logger: Logger): Promise<void> {
    const stateUrl = `${this.daprUrl}/v1.0/state/statestore`
    logger.debug('About to post', value)
    const data = [{ key: key, value: value }]
    const response = await fetch(stateUrl, {
      method: 'POST',
      body: JSON.stringify(data),
      headers: {
        'Content-Type': 'application/json',
      },
    })
    if (!response.ok) {
      logger.error("Couldn't store object")
      const err = response.text()
      throw err
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  public async query(keyPattern: Query, _logger: Logger): Promise<Array<unknown>> {
    console.log('*************This is the query I want ************')
    console.log(keyPattern)
    console.log('*************************')
    if (!this.dbPassword) {
      await this.getDbCredentials()
      await connect(`mongodb://${this.dbUser}:${this.dbPassword}@${this.dbHost}/booster`, {
        useNewUrlParser: true,
        useUnifiedTopology: true,
      })
    }
    const regex = new RegExp(`${keyPattern.type}`)
    const query: Array<EventStoreDocument> = await this.eventStoreModel.find({ _id: regex }).exec()
    const queryResult: unknown[] = []
    query.map((queryElement) => {
      queryResult.push(JSON.parse(queryElement.value))
    })
    return queryResult
  }

  public async hget<TResult>(key: string): Promise<TResult | null> {
    if (!this.dbPassword) {
      await this.getDbCredentials()
    }
    return null
  }

  private getEventKey(event: EventEnvelope): string {
    const keyParts = [
      'ee', // event envelope marker
      event.entityTypeName, // 'Post' entity name
      event.entityID, // entityId
      event.kind, // 'event' | 'snapshot'
      event.typeName, // 'PostCreated' event name
      event.createdAt, // timespan
      UUID.generate(), // hash to make key unique
    ]
    return keyParts.join(this.keySeparator)
  }

  private getReadModelKey(readmodel: ReadModelEnvelope): string {
    const keyParts = [
      'rm', //Read Model mark
      readmodel.typeName, //readModel type name
      readmodel.value.id, //readModel id
    ]
    return keyParts.join(this.keySeparator)
  }

  private async getDbCredentials(): Promise<void> {
    const dbSecretName = process.env['DB_SECRET_NAME'] || 'eventstore-mongodb'
    const dbSecretKey = process.env['DB_SECRET_KEY'] || 'mongodb-password'
    const namespace = process.env['NAMESPACE'] || 'mongodb-password'
    const secretUrl = `${this.daprUrl}/v1.0/secrets/kubernetes/${dbSecretName}?metadata.namespace=${namespace}`
    const response = await fetch(secretUrl, {
      headers: {
        'Content-Type': 'application/json',
      },
    })
    if (!response.ok) {
      const err = response.text()
      throw err
    }
    const parsed = await response.json()
    this.dbPassword = parsed[dbSecretKey]
  }
}
