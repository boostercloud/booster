import * as childProcess from 'child-process-promise'
import { Component } from '../../common/component'
import { Logger } from '@boostercloud/framework-types'
import * as process from 'process'
import { Process } from '.'
import { CliError } from '../../common/errors'

@Component({ throws: CliError })
export class LocalProcess implements Process {
  constructor(readonly logger: Logger) {}

  async catch(e: unknown): Promise<CliError> {
    if (e instanceof CliError) return e
    return new CliError('ProcessError', 'An unknown error occurred', e)
  }

  async getEnvironmentVariable(name: string): Promise<string | undefined> {
    return process.env[name]?.trim()
  }

  async getEnvironmentVariableOrDefault(name: string, defaultValue: string): Promise<string> {
    const envVar = await this.getEnvironmentVariable(name)
    return envVar ?? defaultValue
  }

  async setEnvironmentVariable(name: string, value: string): Promise<void> {
    process.env[name] = value
  }

  async exec(command: string, cwd?: string): Promise<string> {
    try {
      const { stdout, stderr } = await childProcess.exec(command, { cwd })
      const result = `
${stderr ? `There were some issues running the command: ${stderr}\n` : ''}
${stdout}
`
      return result
    } catch (error) {
      if (error instanceof CliError) throw error
      throw new CliError('ProcessError', `There were some issues running the command ${command}: ${error}`, error)
    }
  }

  async cwd(): Promise<string> {
    try {
      return process.cwd()
    } catch (error) {
      if (error instanceof CliError) throw error
      throw new CliError(
        'ProcessError',
        `There were some issues getting the current working directory: ${error}`,
        error
      )
    }
  }

  async chdir(path: string): Promise<void> {
    return process.chdir(path)
  }
}
