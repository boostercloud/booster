import { Booster } from '../booster'
import { AnyClass } from '@boostercloud/framework-types'
import { getFunctionArguments } from './metadata'

export function NonExposed(
  target: AnyClass | InstanceType<AnyClass>,
  methodName: string | undefined,
  parameterIndex?: number
) {
  Booster.configureCurrentEnv((config): void => {
    const className = target.name ?? target.constructor.name
    const value: Array<string> = config.nonExposedGraphQLMetadataKey[className] || []

    const fieldName = getFieldName(methodName, target, parameterIndex)
    config.nonExposedGraphQLMetadataKey[className] = [...value, fieldName]
  })
}

function getFieldName(
  methodName: string | undefined,
  target: AnyClass | InstanceType<AnyClass>,
  parameterIndex: number | undefined
): string {
  if (methodName) {
    return methodName
  }
  if (!parameterIndex) {
    throw new Error(`We could not get field name information in ${target} for method ${methodName}`)
  }

  const argumentNames = getFunctionArguments(target)
  return argumentNames[parameterIndex]
}
