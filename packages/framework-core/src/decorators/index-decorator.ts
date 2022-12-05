import { Booster } from '../booster'
import { Class, ReadModelInterface } from '@boostercloud/framework-types'

export function Index() {
  return (target: Class<ReadModelInterface>, propertyKey: string): void => {
    Booster.configureCurrentEnv((config) => {
      config.readModelIndices[target.constructor.name] = [
        ...(config.readModelIndices[target.constructor.name] || []),
        propertyKey,
      ]
    })
  }
}
