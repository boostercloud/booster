import { exec } from 'child-process-promise'
import { wrapExecError } from '../common/errors'

export async function installDependencies(path?: string): Promise<void> {
  try {
    await exec('npx yarn install', { cwd: path ?? process.cwd() })
  } catch (e) {
    throw wrapExecError(e, 'Could not install dependencies')
  }
}
