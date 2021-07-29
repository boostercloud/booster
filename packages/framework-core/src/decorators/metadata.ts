import { AnyClass } from '@boostercloud/framework-types'
import { ClassMetadata, PropertyMetadata } from 'metadata-booster'
import 'reflect-metadata'

export function getPropertiesMetadata(classType: AnyClass): Array<PropertyMetadata> {
  try {
    return getMetadata(classType).fields
  } catch (e) {
    console.log(e.message)
    return []
  }
}

export function getMethodsMetadata(classType: AnyClass): Array<PropertyMetadata> {
  try {
    return getMetadata(classType).methods
  } catch (e) {
    console.log(e.message)
    return []
  }
}

export function getMetadata(classType: AnyClass): ClassMetadata {
  const meta: ClassMetadata = Reflect.getMetadata('booster:typeinfo', classType)
  if (!meta) {
    throw new Error(`Could not get proper metadata information of ${classType.name}`)
  }
  return meta
}
