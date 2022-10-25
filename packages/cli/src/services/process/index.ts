/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { deriveLifted, Effect, tag } from '@boostercloud/framework-types/src/effect'

export class ProcessError {
  readonly _tag = 'ProcessError'
  public constructor(readonly reason: unknown) {}
}

export interface ProcessService {
  readonly exec: (command: string, cwd?: string) => Effect<unknown, ProcessError, string>
  readonly cwd: () => Effect<unknown, ProcessError, string>
}

export const ProcessService = tag<ProcessService>()

/**
 * Helper SDK to be able to run service methods outside of the layers
 */
export const processInternals = deriveLifted(ProcessService)(
  // Functions to export from the service
  ['exec', 'cwd'],
  // Constants to export from the service
  [],
  // Values returned from side effects in the service
  []
)
