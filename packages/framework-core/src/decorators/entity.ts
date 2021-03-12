/* eslint-disable @typescript-eslint/no-unused-vars */
import { Booster } from '../booster'
import {
  Class,
  EntityInterface,
  ReducerMetadata,
  EventInterface,
  RoleAccess,
  AnyClass,
} from '@boostercloud/framework-types'
import 'reflect-metadata'

interface EntityAttributes {
  authorizeReadEvents: RoleAccess['authorize']
}

// The craziness with the following both types (the param and return types of the @Entity decorator) is to achieve that either:
// - The @Entity decorator doesn't have parenthesis: THEN the decorator needs to accept a class as a parameter and return void
// - The @Entity decorator have parenthesis: THEN it needs to accept an object with attributes and return a function accepting a class as a parameter
type EntityDecoratorParam = AnyClass | EntityAttributes
type EntityDecoratorResult<TEntity, TParam> = TParam extends EntityAttributes
  ? (entityClass: Class<TEntity>) => void
  : void

/**
 * Decorator to register a class as an Entity
 * @constructor
 */
export function Entity<TEntity extends EntityInterface, TParam extends EntityDecoratorParam>(
  classOrAttributes: TParam
): EntityDecoratorResult<TEntity, TParam> {
  let authorizeReadEvents: RoleAccess['authorize'] = []
  // This function will be either returned or executed, depending on the parameters passed to the decorator
  const mainLogicFunction = (entityClass: Class<TEntity>) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.entities[entityClass.name]) {
        throw new Error(`An entity called ${entityClass.name} is already registered
        If you think that this is an error, try performing a clean build..`)
      }

      config.entities[entityClass.name] = {
        class: entityClass,
        authorizeReadEvents,
      }
    })
  }

  if (isEntityAttributes(classOrAttributes)) {
    authorizeReadEvents = classOrAttributes.authorizeReadEvents
    return mainLogicFunction as EntityDecoratorResult<TEntity, TParam>
  }

  return mainLogicFunction(classOrAttributes as Class<TEntity>) as EntityDecoratorResult<TEntity, TParam>
}

function isEntityAttributes(param: EntityDecoratorParam): param is EntityAttributes {
  return 'authorizeReadEvents' in param
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
  Booster.configureCurrentEnv((config): void => {
    const reducerPath = config.reducers[eventName]
    if (reducerPath) {
      throw new Error(
        `Error registering reducer: The event ${eventName} was already registered to be reduced by method ${reducerPath.methodName} in the entity ${reducerPath.class.name}.
        If you think that this is an error, try performing a clean build.`
      )
    }

    config.reducers[eventName] = reducerMethod
  })
}

type ReducerMethod<TEvent, TEntity> =
  | TypedPropertyDescriptor<(event: TEvent, entity: TEntity) => TEntity>
  | TypedPropertyDescriptor<(event: TEvent, entity?: TEntity) => TEntity>
