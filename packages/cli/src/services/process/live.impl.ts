import * as childProcess from 'child-process-promise'
import * as process from 'process'
import { ProcessService, ProcessError } from '.'
import { Layer, tryCatch, tryCatchPromise } from '@boostercloud/framework-types/src/effect'

const exec = (command: string, cwd?: string) =>
  tryCatchPromise(
    async () => {
      console.log('Executing command', command, 'in', cwd)
      const { stdout, stderr } = await childProcess.exec(command, { cwd })
      const result = `
${stderr ? `There were some issues running the command: ${stderr}` : ''}
${stdout}
`
      console.log('RESULT:', result)
      return result
    },
    (reason) => new ProcessError(reason)
  )

const cwd = () =>
  tryCatch(
    () => process.cwd(),
    (reason) => new ProcessError(reason)
  )

export const LiveProcess = Layer.fromValue(ProcessService)({ exec, cwd })
