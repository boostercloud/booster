/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig } from '@boostercloud/framework-types'
import { PlatformError } from '@effect/platform/Error'
import { CliApp, Command } from '@effect/cli'
import { RunMain } from '@effect/platform/Runtime'
import { Effect, Layer, Types } from 'effect'

export type Context = BoosterConfig | CliApp.CliApp.Environment
export type Error = PlatformError

export interface Injectable {
  commands: readonly [Command, ...Array<Command>]
  runMain?: RunMain
  contextProvider?: Layer.Layer<never, never, CliApp.CliApp.Environment>
}

export type Command = Command.Command<any, Context, Error, any>

export type Args<T extends Command.Command.Config> = Types.Simplify<Command.Command.ParseConfig<T>>

export type Handler<T extends Command.Command.Config> = (args: Args<T>) => Effect.Effect<Context, Error, void>

export const command = <TName extends string, TArgs extends Command.Command.Config>(
  name: TName,
  args: TArgs,
  handler: (args: Args<TArgs>) => Effect.Effect<Context, Error, void>
): Command => Command.make(name, args, handler)

export const handler = <T extends Command.Command.Config>(_: T, handler: Handler<T>): Handler<T> => handler
