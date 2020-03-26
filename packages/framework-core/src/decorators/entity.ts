/* eslint-disable @typescript-eslint/no-unused-vars */
import { Booster } from '../booster'
import { Class, EntityInterface, ReducerMetadata, EventInterface } from '@boostercloud/framework-types'

/**
 * Decorator to register a class as an Entity
 * @constructor
 */
export function Entity<TEntity extends EntityInterface>(entityClass: Class<TEntity>): void {
  Booster.configure((config): void => {
    if (config.entities[entityClass.name]) {
      throw new Error(`An entity called ${entityClass.name} is already registered.`)
    }

    config.entities[entityClass.name] = {
      class: entityClass,
    }
  })
}

/**
 * Decorator to register an entity class method as a reducer function
 * for a specific event.
 *
 * @param eventClass The event that this method will react to
 */
export function Reduces<TEvent extends EventInterface>(
  eventClass: Class<TEvent>
): <TEntity>(
  entityClass: Class<TEntity>,
  methodName: string,
  methodDescriptor: ReducerMethod<TEvent, TEntity>
) => void {
  return (entityClass, methodName) => {
    registerReducer(eventClass.name, {
      class: entityClass,
      methodName: methodName,
    })
  }
}

function registerReducer(eventName: string, reducerMethod: ReducerMetadata): void {
  Booster.configure((config): void => {
    const reducerPath = config.reducers[eventName]
    if (reducerPath) {
      throw new Error(
        `Error registering reducer: The event ${eventName} was already registered to be reduced by method ${reducerPath.methodName} in the entity ${reducerPath.class.name}.`
      )
    }

    config.reducers[eventName] = reducerMethod
  })
}

type ReducerMethod<TEvent, TEntity> =
  | TypedPropertyDescriptor<(event: TEvent, entity: TEntity) => TEntity>
  | TypedPropertyDescriptor<(event: TEvent, entity?: TEntity) => TEntity>
