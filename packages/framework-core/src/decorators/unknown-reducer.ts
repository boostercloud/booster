import { Class, EventInterface, ReducerMetadata } from '@boostercloud/framework-types'
import { Booster } from '../booster'

export function UnknownReducer<TEvent extends EventInterface>(): <TEntity>(
  reducerClass: Class<unknown>,
  methodName: string,
  methodDescriptor: ReducerMethod<TEvent, TEntity>
) => void {
  return (reducerClass, methodName) => {
    registerUnknownReducer({
      class: reducerClass,
      methodName: methodName,
    })
  }
}

function registerUnknownReducer(reducerMetadata: ReducerMetadata): void {
  Booster.configureCurrentEnv((config): void => {
    const reducerPath = config.unknownReducerHandler
    if (reducerPath) {
      throw new Error(
        `Error registering reducer: The Unknown reducer handler was already registered to be reduced by method ${reducerPath.methodName} in the class ${reducerPath.class.name}.
        If you think that this is an error, try performing a clean build.`
      )
    }

    config.unknownReducerHandler = reducerMetadata
  })
}

type ReducerMethod<TEvent, TEntity> =
  | TypedPropertyDescriptor<(event: TEvent, entity: TEntity) => void>
  | TypedPropertyDescriptor<(event: TEvent, entity?: TEntity) => void>
