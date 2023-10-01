/* eslint-disable @typescript-eslint/ban-types */
import { Booster } from '../booster'
import {
  Class,
  CommandAuthorizer,
  CommandFilterHooks,
  CommandInterface,
  CommandRoleAccess,
  Register,
} from '@boostercloud/framework-types'
import { getClassMetadata } from './metadata'
import { BoosterAuthorizer } from '../booster-authorizer'

/**
 * Annotation to tell Booster which classes are your entities
 * @param attributes
 * @constructor
 */
export function Command(
  attributes: CommandRoleAccess & CommandFilterHooks
): <TCommand>(commandClass: CommandInterface<TCommand>) => void {
  return (commandClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.commandHandlers[commandClass.name]) {
        throw new Error(`A command called ${commandClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      const metadata = getClassMetadata(commandClass)
      config.commandHandlers[commandClass.name] = {
        class: commandClass,
        authorizer: BoosterAuthorizer.build(attributes) as CommandAuthorizer,
        before: attributes.before ?? [],
        properties: metadata.fields,
        methods: metadata.methods,
      }
    })
  }
}

/**
 * Decorator to register a command class method as a
 * command handler function.
 *
 * @param commandClass The command that this method will handle
 *
 * @deprecated The method is not needed anymore and will be removed in future versions
 *
 * TODO Remove this method as it's not needed
 */
export function Returns<TReturn>(
  returnClass: Class<TReturn>
): <TCommand>(
  commandClass: Class<TCommand>,
  methodName: string,
  methodDescriptor: TypedPropertyDescriptor<
    (command: TCommand, register: Register) => Promise<PrimitiveTypeOf<TReturn>>
  >
) => void {
  console.error(`
    The usage of the '@Returns' annotation is deprecated,
    You may return any type without annotating the method

    For more information, check out the docs:

    https://docs.boosterframework.com/architecture/command#returning-a-value
  `)
  return (commandClass) => {}
}

type PrimitiveTypeOf<TReturn> = TReturn extends Boolean
  ? boolean
  : TReturn extends Number
  ? number
  : TReturn extends String
  ? string
  : TReturn
