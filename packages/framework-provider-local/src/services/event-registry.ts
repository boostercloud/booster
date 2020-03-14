import { EventEnvelope } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { eventsDatabase } from '../constants'

export class EventRegistry {
  public readonly events: DataStore<EventEnvelope> = new DataStore(eventsDatabase)
  constructor() {
    this.events.loadDatabase()
  }

  public async publish(event: EventEnvelope): Promise<void> {
    return new Promise((resolve, reject) => {
      this.events.insert(event, (err) => {
        err ? reject(err) : resolve()
      })
    })
  }
}
