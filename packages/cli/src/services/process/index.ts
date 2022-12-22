import { Effect, tag } from '@boostercloud/framework-types/dist/effect'

export class ProcessError {
  readonly _tag = 'ProcessError'
  constructor(readonly error: Error) {}
}

export interface ProcessService {
  readonly exec: (command: string, cwd?: string) => Effect<unknown, ProcessError, string>
  readonly cwd: () => Effect<unknown, ProcessError, string>
}

export const ProcessService = tag<ProcessService>()
