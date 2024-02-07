import { BoosterConfig } from '../config'
import { PlatformError } from '@effect/platform/Error'
import { CliApp, Command } from '@effect/cli'
import { RunMain } from '@effect/platform/Runtime'
import { Layer } from 'effect'

export type CliContext = BoosterConfig | CliApp.CliApp.Environment
export type CliError = PlatformError

export interface Flux {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  execute: () => Command.Command<any, CliContext, CliError, any>
  runMain: RunMain
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  contextProvider: Layer.Layer<never, never, any>
}
