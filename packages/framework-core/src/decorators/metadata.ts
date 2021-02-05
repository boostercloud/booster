import { PropertyMetadata, Class, AnyClass } from '@boostercloud/framework-types'
import 'reflect-metadata'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function getPropertiesMetadata(classType: Class<any>): Array<PropertyMetadata> {
  const propertyNames = Object.getOwnPropertyNames(new classType())
  const propertyTypes = Reflect.getMetadata('design:paramtypes', classType) ?? []
  if (propertyNames.length !== propertyTypes.length) {
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
