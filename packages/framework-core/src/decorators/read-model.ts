import { Class, AnyClass, ReadModelInterface, RoleAccess, PropertyMetadata } from '@boostercloud/framework-types'
import { Booster } from '../booster'

/**
 * Decorator to register a class as a ReadModel
 * @param attributes
 */
export function ReadModel(attributes: RoleAccess): (readModelClass: Class<ReadModelInterface>) => void {
  return (readModelClass) => {
    Booster.configure((config): void => {
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
    // eslint-disable-next-line prettier/prettier
    throw new Error(`Could not get proper metadata information of ${classType.name}. While inspecting the class, the following properties were found:
> ${propertyNames.join(', ')}
But its constructor parameters have the following types:
> ${propertyTypes.map((type: AnyClass) => type.name).join(', ')}
They mismatch. Make sure you define all properties as "constructor parameter properties" (see https://www.typescriptlang.org/docs/handbook/classes.html#parameter-properties)
`)
  }

  return propertyNames.map((propertyName, index) => ({
    name: propertyName,
    type: propertyTypes[index],
  }))
}
