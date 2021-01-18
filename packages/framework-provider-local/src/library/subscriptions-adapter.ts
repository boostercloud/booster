import { BoosterConfig, Logger, SubscriptionEnvelope } from 'framework-types/dist'
import { SubscriptionsRegistry } from '../services/subscriptions-registry'

export async function subscribe(
  db: SubscriptionsRegistry,
  _config: BoosterConfig,
  logger: Logger,
  subscriptionEnvelope: SubscriptionEnvelope
): Promise<void> {
  logger.info('Subscribing to ReadModel with the following envelope', subscriptionEnvelope)
  await db.store(subscriptionEnvelope)
}
