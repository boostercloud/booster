import * as execa from 'execa'
import * as process from 'process'
import { ProcessError, ProcessService } from '.'
import { Layer, tryCatch, tryCatchPromise } from '@boostercloud/framework-types/dist/effect'
import { unknownToError } from '../../common/errors'

const exec = (command: string, cwd?: string) =>
  tryCatchPromise(
    async () => {
      const { stdout, stderr } = await execa.command(command, { cwd })
      const result = `
${stderr ? `There were some issues running the command: ${stderr}\n` : ''}
${stdout}
`
      return result
    },
    (reason) => new ProcessError(unknownToError(reason))
  )

const cwd = () =>
  tryCatch(
    () => process.cwd(),
    (reason) => new ProcessError(unknownToError(reason))
  )

export const LiveProcess = Layer.fromValue(ProcessService)({ exec, cwd })
