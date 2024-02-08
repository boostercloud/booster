/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig } from '../config'
import { PlatformError } from '@effect/platform/Error'
import { CliApp, Command } from '@effect/cli'
import { RunMain } from '@effect/platform/Runtime'
import { Effect, Layer, Types } from 'effect'

export type CliContext = BoosterConfig | CliApp.CliApp.Environment
export type CliError = PlatformError

export interface Nexus {
  commands: readonly [NexusCommand, ...Array<NexusCommand>]
  runMain: RunMain
  contextProvider: Layer.Layer<never, never, CliApp.CliApp.Environment>
}

export type NexusCommand = Command.Command<any, CliContext, CliError, any>

export type NexusArgs<T extends Command.Command.Config> = Types.Simplify<Command.Command.ParseConfig<T>>

export const nexusCommand = <Name extends string, Args extends Command.Command.Config>(
  name: Name,
  args: Args,
  handler: (args: NexusArgs<Args>) => Effect.Effect<CliContext, CliError, void>
): NexusCommand => Command.make(name, args, handler)
