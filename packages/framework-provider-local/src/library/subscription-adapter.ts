import { BoosterConfig, SubscriptionEnvelope } from '@boostercloud/framework-types'
import { WebSocketRegistry } from '../services/web-socket-registry'

export async function subscribeToReadModel(
  db: WebSocketRegistry,
  config: BoosterConfig,
  subscriptionEnvelope: SubscriptionEnvelope
): Promise<void> {
  await db.store(subscriptionEnvelope)
}

export async function fetchSubscriptions(
  db: WebSocketRegistry,
  config: BoosterConfig,
  subscriptionName: string
): Promise<Array<SubscriptionEnvelope>> {
  return (await db.query({ className: subscriptionName })) as Array<SubscriptionEnvelope>
}

export async function deleteSubscription(
  db: WebSocketRegistry,
  config: BoosterConfig,
  connectionID: string,
  subscriptionID: string
): Promise<void> {
  await db.delete({
    connectionID: connectionID,
    subscriptionID: subscriptionID,
  })
}

export async function deleteAllSubscriptions(
  db: WebSocketRegistry,
  config: BoosterConfig,
  connectionID: string
): Promise<void> {
  await db.delete({
    connectionID: connectionID,
  })
}
