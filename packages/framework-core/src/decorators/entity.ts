/* eslint-disable @typescript-eslint/no-unused-vars */
import { Booster } from '../booster'
import {
  Class,
  EntityInterface,
  ReducerMetadata,
  PropertyMetadata,
  EventInterface,
} from '@boostercloud/framework-types'
import 'reflect-metadata'
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
      properties: getPropertiesMetadata(entityClass),
    }
  })
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function getPropertiesMetadata(classType: Class<any>): Array<PropertyMetadata> {
  const propertyNames = Object.getOwnPropertyNames(new classType())
  const propertyTypes = Reflect.getMetadata('design:paramtypes', classType)
  if (propertyNames.length != propertyTypes.length) {
    throw new Error(
      `Could not get metadata of entity ${classType.name}: the number of property names ` +
        'does not match the number of inferred property types'
    )
  }

  return propertyNames.map((propertyName, index) => ({
    name: propertyName,
    type: propertyTypes[index],
  }))
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
