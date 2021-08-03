import { Booster } from '../booster'
import { Class, ReadModelInterface } from '@boostercloud/framework-types'
import { getFunctionArguments } from './metadata'

export function sortBy(klass: Class<ReadModelInterface>, _functionName: string, parameterIndex: number): void {
  const args = getFunctionArguments(klass)
  const propertyName = args[parameterIndex]
  Booster.configureCurrentEnv((config): void => {
    if (config.readModelSortKeys[klass.name] && config.readModelSortKeys[klass.name] !== propertyName) {
      throw new Error(
        `Error trying to register a sort key named \`${propertyName}\` for class \`${
          klass.name
        }\`. It already had the sort key \`${
          config.readModelSortKeys[klass.name]
        }\` defined and only one sort key is allowed for each read model.`
      )
    } else {
      config.readModelSortKeys[klass.name] = propertyName
    }
  })
}
