import { exec } from 'child-process-promise'
import { wrapExecError } from '../common/errors'

export async function installProductionDependencies(projectPath: string): Promise<void> {
  try {
    await exec('npm install --production --no-bin-links --no-optional', { cwd: projectPath })
  } catch (err) {
    const e = err as Error
    throw wrapExecError(e, 'Could not install production dependencies')
  }
}

export async function installAllDependencies(path?: string): Promise<void> {
  try {
    await exec('npm install', { cwd: path ?? process.cwd() })
  } catch (err) {
    const e = err as Error
    throw wrapExecError(e, 'Could not install dependencies')
  }
}
