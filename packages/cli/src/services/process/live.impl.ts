import * as childProcess from 'child-process-promise'
import * as process from 'process'
import { CwdError, ExecError, ProcessService } from '.'
import { Layer, tryCatch, tryCatchPromise } from '@boostercloud/framework-types/dist/effect'

const exec = (command: string, cwd?: string) =>
  tryCatchPromise(
    async () => {
      const { stdout, stderr } = await childProcess.exec(command, { cwd })
      const result = `
${stderr ? `There were some issues running the command: ${stderr}` : ''}
${stdout}
`
      return result
    },
    (reason) =>
      new ExecError(
        new Error(`There were some issues running the command ${command}:

    ${reason}`)
      )
  )

const cwd = () =>
  tryCatch(
    () => process.cwd(),
    (reason) =>
      new CwdError(
        new Error(`There were some issues getting the current working directory:

    ${reason}`)
      )
  )

export const LiveProcess = Layer.fromValue(ProcessService)({ exec, cwd })
