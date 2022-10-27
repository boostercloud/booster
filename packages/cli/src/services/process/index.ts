import { Effect, tag } from '@boostercloud/framework-types/src/effect'

export class ProcessError {
  readonly _tag = 'ProcessError'
  public constructor(readonly reason: unknown) {}
}

export interface ProcessService {
  readonly exec: (command: string, cwd?: string) => Effect<unknown, ProcessError, string>
  readonly cwd: () => Effect<unknown, ProcessError, string>
}

export const ProcessService = tag<ProcessService>()
