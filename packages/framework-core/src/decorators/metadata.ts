import { AnyClass } from '@boostercloud/framework-types'
import { ClassMetadata, PropertyMetadata } from 'metadata-booster'
import 'reflect-metadata'

export function getPropertiesMetadata(classType: AnyClass): Array<PropertyMetadata> {
  const meta: ClassMetadata = Reflect.getMetadata('booster:typeinfo', classType)
  if (!meta) {
    console.log(`Couldn't get proper metadata information of ${classType.name}`)
    return []
  }
  return meta.fields
}
