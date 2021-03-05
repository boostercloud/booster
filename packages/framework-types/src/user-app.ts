/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * `UserApp` represents the lambdas interface of a Booster app
 * when it's dynamically imported from a different module.
 */
export interface UserApp {
  boosterEventDispatcher(_: any): Promise<any>
  boosterPreSignUpChecker(_: any): Promise<any>
  boosterServeGraphQL(_: any): Promise<any>
  boosterNotifySubscribers(_: any): Promise<any>
  boosterTriggerScheduledCommand(_: any): Promise<any>
}
