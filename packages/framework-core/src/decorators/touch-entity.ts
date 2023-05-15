import { Booster } from '../booster'
import { TouchEntityInterface, TouchEntityParameters } from '@boostercloud/framework-types'

export function TouchEntity(attributes: TouchEntityParameters): (touchEntityClass: TouchEntityInterface) => void {
  return (touchEntityClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.touchEntityHandlers[touchEntityClass.name]) {
        throw new Error(`A touch entity called ${touchEntityClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      config.touchEntityHandlers[touchEntityClass.name] = {
        class: touchEntityClass,
        touchOptions: attributes,
      }
    })
  }
}
