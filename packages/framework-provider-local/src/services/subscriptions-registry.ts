import { SubscriptionEnvelope } from '@boostercloud/framework-types'
import * as DataStore from 'nedb'
import { subscriptionsDatabase } from '../paths'

export class SubscriptionsRegistry {
  public readonly subscriptions: DataStore<SubscriptionEnvelope> = new DataStore(subscriptionsDatabase)
  constructor() {
    this.subscriptions.loadDatabase()
  }

  public async query(query: object): Promise<Array<SubscriptionEnvelope>> {
    const queryPromise = new Promise((resolve, reject) =>
      this.subscriptions.find(query).exec((err, docs) => {
        if (err) reject(err)
        else resolve(docs)
      })
    )

    return queryPromise as Promise<Array<SubscriptionEnvelope>>
  }

  public async store(envelope: SubscriptionEnvelope): Promise<void> {
    const subscriptionId = idForSubscription(envelope)
    return new Promise((resolve, reject) => {
      this.subscriptions.update({ subscriptionId }, { ...envelope, subscriptionId }, { upsert: true }, (err) => {
        err ? reject(err) : resolve()
      })
    })
  }

  public async deleteById(envelope: SubscriptionEnvelope): Promise<number> {
    const deletePromise = new Promise((resolve, reject) =>
      this.subscriptions.remove(
        { subscriptionId: idForSubscription(envelope) },
        { multi: true },
        (err, numRemoved: number) => {
          if (err) reject(err)
          else resolve(numRemoved)
        }
      )
    )

    return deletePromise as Promise<number>
  }
}

const idForSubscription = (envelope: SubscriptionEnvelope): string =>
  `${envelope.connectionID}-${envelope.operation.id}`
