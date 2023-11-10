/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterApp } from '.'

/**
 * `UserApp` represents the interface of the value when
 * we `require` the `dist/index.js` file of the user.
 */
export interface UserApp {
  Booster: BoosterApp
  boosterEventDispatcher(_: any): Promise<any>
  boosterPreSignUpChecker(_: any): Promise<any>
  boosterServeGraphQL(_: any): Promise<any>
  boosterNotifySubscribers(_: any): Promise<any>
  boosterHealth(_: any): Promise<any>
}
