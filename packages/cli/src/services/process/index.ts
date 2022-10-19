/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { Effect, tag } from '@boostercloud/framework-types/src/effect'

export class ProcessError {
  readonly _tag = 'ProcessError'
  readonly error: Error
  public constructor(readonly reason: unknown) {
    this.error = reason instanceof Error ? reason : new Error(String(reason))
  }
}

export interface ProcessService {
  readonly exec: (command: string, cwd?: string) => Effect<unknown, ProcessError, string>
  readonly cwd: () => Effect<unknown, ProcessError, string>
}

export const ProcessService = tag<ProcessService>()
