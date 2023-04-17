import { Class } from '@boostercloud/framework-types'
import { Booster } from '../booster'

export function ignore<T>(target: Class<T>, methodName: string, parameterIndex: number) {
  Booster.configureCurrentEnv((config): void => {
    const value = config.ignoreGraphQLMetadataKey[target.name ?? target.constructor.name] || []
    config.ignoreGraphQLMetadataKey[target.name ?? target.constructor.name] = [...value, parameterIndex]
  })
}
