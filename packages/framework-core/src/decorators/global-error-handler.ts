import { Booster } from '../booster'
import { GlobalErrorHandlerInterface } from '@boostercloud/framework-types'

export function GlobalErrorHandler(): (errorHandlerClass: GlobalErrorHandlerInterface) => void {
  return (errorHandlerClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.globalErrorsHandler) {
        throw new Error(`An error handler called ${errorHandlerClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      config.globalErrorsHandler = { class: errorHandlerClass }
    })
  }
}
