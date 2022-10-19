/* eslint-disable @typescript-eslint/explicit-function-return-type */
import * as childProcess from 'child-process-promise'
import * as process from 'process'
import { ProcessService, ProcessError } from '.'
import { Layer, tryCatch, tryCatchPromise } from '@boostercloud/framework-types/src/effect'

const exec = (command: string, cwd?: string) =>
  tryCatchPromise(
    async () => {
      const { stdout, stderr } = await childProcess.exec(command, { cwd })
      if (stderr) {
        throw new Error(stderr)
      }
      return stdout
    },
    (reason) => new ProcessError(reason)
  )

const cwd = () =>
  tryCatch(
    () => process.cwd(),
    (reason) => new ProcessError(reason)
  )

export const LiveProcess = Layer.fromValue(ProcessService)({ exec, cwd })
