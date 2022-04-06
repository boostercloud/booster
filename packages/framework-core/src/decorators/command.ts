/* eslint-disable @typescript-eslint/ban-types */
import { Booster } from '../booster'
import { CommandInterface, CommandFilterHooks, RoleAccess, Register, Class } from '@boostercloud/framework-types'
import { getPropertiesMetadata } from './metadata'

/**
 * Annotation to tell Booster which classes are your entities
 * @param attributes
 * @constructor
 */
export function Command(
  attributes: RoleAccess & CommandFilterHooks
): <TCommand>(commandClass: CommandInterface<TCommand>) => void {
  return (commandClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.commandHandlers[commandClass.name]) {
        throw new Error(`A command called ${commandClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      const returnClass = config.commandHandlerReturnTypes[commandClass.name]?.class ?? Boolean

      config.commandHandlers[commandClass.name] = {
        class: commandClass,
        authorizedRoles: attributes.authorize,
        before: attributes.before ?? [],
        after: attributes.after ?? [],
        onError: attributes.onError,
        properties: getPropertiesMetadata(commandClass),
        returnClass,
      }
    })
  }
}

/**
 * Decorator to register a command class method as a
 * command handler function.
 *
 * @param commandClass The command that this method will handle
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
  return (commandClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.commandHandlerReturnTypes[commandClass.name]) {
        throw new Error(`A command handler return type for the command ${commandClass.name} is already registered`)
      }

      config.commandHandlerReturnTypes[commandClass.name] = { class: returnClass }
    })
  }
}

type PrimitiveTypeOf<TReturn> = TReturn extends Boolean
  ? boolean
  : TReturn extends Number
  ? number
  : TReturn extends String
  ? string
  : TReturn
