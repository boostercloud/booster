import { Class, ReadModelInterface, RoleAccess, PropertyMetadata } from '@boostercloud/framework-types'
import { Booster } from '../booster'

/**
 * Decorator to register a class as a ReadModel
 * @param attributes
 */
export function ReadModel(attributes: RoleAccess): (readModelClass: Class<ReadModelInterface>) => void {
  return (readModelClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.readModels[readModelClass.name]) {
        throw new Error(`A read model called ${readModelClass.name} is already registered.`)
      }

      config.readModels[readModelClass.name] = {
        class: readModelClass,
        properties: getPropertiesMetadata(readModelClass),
        authorizedRoles: attributes.authorize,
      }
    })
  }
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