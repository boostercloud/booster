import { Class } from '@boostercloud/framework-types'
import { ClassMetadata, PropertyMetadata } from 'metadata-booster'
import 'reflect-metadata'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function getPropertiesMetadata(classType: Class<any>): Array<PropertyMetadata> {
  const meta: ClassMetadata = Reflect.getMetadata('booster:typeinfo', classType)
  if (!meta) {
    throw new Error(`Could not get proper metadata information of ${classType.name}`)
  }

  return meta.fields
}
