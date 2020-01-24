import { Class, ReadModelInterface } from '@boostercloud/framework-types'
import { Booster } from '../booster'

/**
 * Decorator to register a class as a ReadModel
 * @param readModelClass
 */
export function ReadModel(readModelClass: Class<ReadModelInterface>): void {
  Booster.configure((config): void => {
    if (config.readModels[readModelClass.name]) {
      throw new Error(`A read model called ${readModelClass.name} is already registered.`)
    } else {
      config.readModels[readModelClass.name] = { class: readModelClass }
    }
  })
}
