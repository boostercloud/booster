import { EventHandlerInterface, BoosterConfig } from '@boostercloud/framework-types'
import { Booster } from '../booster'

export function GlobalEventHandler(): <TEventHandler extends EventHandlerInterface>(
  eventHandlerClass: TEventHandler
) => void {
  return (eventHandlerClass) => registerGlobalEventHandler(eventHandlerClass)
}

function registerGlobalEventHandler<TEventHandler extends EventHandlerInterface>(
  eventHandlerClass: TEventHandler
): void {
  Booster.configureCurrentEnv((config: BoosterConfig): void => {
    if (config.globalEventHandler) {
      throw new Error(`A global event handler is already registered.
        If you think that this is an error, try performing a clean build.`)
    }
    config.globalEventHandler = eventHandlerClass
  })
}
