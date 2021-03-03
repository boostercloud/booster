import { BoosterApp } from './booster-app'
import { BoosterEventDispatcher } from './booster-event-dispatcher'
import { BoosterAuth } from './booster-auth'
import { BoosterGraphQLDispatcher } from './booster-graphql-dispatcher'
import { BoosterScheduledCommandDispatcher } from './booster-scheduled-command-dispatcher'
import { BoosterSubscribersNotifier } from './booster-subscribers-notifier'

/**
 * root-level function to dispatch events.
 * Usually triggered after an event is inserted in the events store.
 */
export async function boosterEventDispatcher(rawEvent: unknown): Promise<unknown> {
  return BoosterEventDispatcher.dispatch(rawEvent, BoosterApp.config, BoosterApp.logger)
}

/**
 * root-level function to check for credentials.
 * This function is called by the cloud provider to verify the JWT tokens in API calls.
 */
export async function boosterPreSignUpChecker(signUpRequest: unknown): Promise<unknown> {
  return BoosterAuth.checkSignUp(signUpRequest, BoosterApp.config, BoosterApp.logger)
}

/**
 * root-level function to serve GraphQL mutations, queries and subscriptions.
 */
export async function boosterServeGraphQL(rawRequest: unknown): Promise<unknown> {
  return new BoosterGraphQLDispatcher(BoosterApp.config, BoosterApp.logger).dispatch(rawRequest)
}

/**
 * root-level function that triggers a scheduled command (the cloud provider decides when to trigger it sending an event)
 */
export async function boosterTriggerScheduledCommand(rawRequest: unknown): Promise<unknown> {
  return new BoosterScheduledCommandDispatcher(BoosterApp.config, BoosterApp.logger).dispatch(rawRequest)
}

/**
 * root-level function that the cloud provider calls when there's an update in the event stream to
 * trigger a broadcast to all subscribers using the websockets GraphQL interface.
 */
export async function boosterNotifySubscribers(rawRequest: unknown): Promise<unknown> {
  return new BoosterSubscribersNotifier(BoosterApp.config, BoosterApp.logger).dispatch(rawRequest)
}

/**
 * Project-wide accessible reference to the current BoosterApp
 */
export const Booster = BoosterApp

/**
 * reexport public objects
 */
export * from './decorators'
