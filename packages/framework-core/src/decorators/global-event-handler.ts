import { EventHandlerInterface, BoosterConfig } from '@boostercloud/framework-types'
import { Booster } from '../booster'

export const GLOBAL_EVENT_HANDLER = 'GLOBAL_EVENT_HANDLER'

export function GlobalEventHandler(): <TEventHandler extends EventHandlerInterface>(
  eventHandlerClass: TEventHandler
) => void {
  return (eventHandlerClass) => registerGlobalEventHandler(eventHandlerClass)
}

function registerGlobalEventHandler<TEventHandler extends EventHandlerInterface>(
  eventHandlerClass: TEventHandler
): void {
  Booster.configureCurrentEnv((config: BoosterConfig): void => {
    const registeredEventHandlers = config.eventHandlers[GLOBAL_EVENT_HANDLER] || []
    if (registeredEventHandlers.length > 0) {
      throw new Error(`A global event handler is already registered.
        If you think that this is an error, try performing a clean build.`)
    }
    registeredEventHandlers.push(eventHandlerClass)
    config.eventHandlers[GLOBAL_EVENT_HANDLER] = registeredEventHandlers // todo
  })
}
