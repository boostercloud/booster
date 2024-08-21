import {
  Class,
  ReadModelAuthorizer,
  ReadModelFilterHooks,
  ReadModelInterface,
  ReadModelRoleAccess,
} from '@boostercloud/framework-types'
import { Booster } from '../booster'
import { BoosterAuthorizer } from '../booster-authorizer'
import { getClassMetadata } from './metadata'

/**
 * Decorator to register a class as a ReadModel
 * @param attributes
 */
export function ReadModel(
  attributes: ReadModelRoleAccess & ReadModelFilterHooks
): (readModelClass: Class<ReadModelInterface>, context?: ClassDecoratorContext) => void {
  return (readModelClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.readModels[readModelClass.name]) {
        throw new Error(`A read model called ${readModelClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      const authorizer = BoosterAuthorizer.build(attributes) as ReadModelAuthorizer
      const classMetadata = getClassMetadata(readModelClass)
      const dynamicDependencies = Reflect.getMetadata('dynamic:dependencies', readModelClass) || {}

      // Combine properties with dynamic dependencies
      const properties = classMetadata.fields.map((field: any) => {
        return {
          ...field,
          dependencies: dynamicDependencies[field.name] || [],
        }
      })

      config.readModels[readModelClass.name] = {
        class: readModelClass,
        properties,
        authorizer,
        before: attributes.before ?? [],
      }
    })
  }
}

interface CalculatedFieldOptions {
  dependsOn: string[]
}

/**
 * Decorator to mark a property as a calculated field with dependencies.
 * @param options - A `CalculatedFieldOptions` object indicating the dependencies.
 */
export function CalculatedField(options: CalculatedFieldOptions): PropertyDecorator {
  return (target: object, propertyKey: string | symbol): void => {
    const existingDependencies = Reflect.getMetadata('dynamic:dependencies', target.constructor) || {}
    existingDependencies[propertyKey] = options.dependsOn
    Reflect.defineMetadata('dynamic:dependencies', existingDependencies, target.constructor)
  }
}
