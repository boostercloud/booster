import { Effect, tag } from '@boostercloud/framework-types/dist/effect'

export class ExecError {
  readonly _tag = 'ExecError'
  constructor(readonly error: Error) {}
}

export class CwdError {
  readonly _tag = 'CwdError'
  constructor(readonly error: Error) {}
}

export interface ProcessService {
  readonly exec: (command: string, cwd?: string) => Effect<unknown, ExecError, string>
  readonly cwd: () => Effect<unknown, CwdError, string>
}

export const ProcessService = tag<ProcessService>()
