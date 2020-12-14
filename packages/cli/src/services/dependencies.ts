import { exec } from 'child-process-promise'
import { wrapExecError } from '../common/errors'

export async function pruneDevDependencies(): Promise<void> {
  try {
    await exec('npx yarn install --production --no-bin-links')
  } catch (e) {
    throw wrapExecError(e, 'Could not prune dev dependencies')
  }
}

export async function installAllDependencies(path?: string): Promise<void> {
  try {
    await exec('npx yarn install', { cwd: path ?? process.cwd() })
  } catch (e) {
    throw wrapExecError(e, 'Could not install dependencies')
  }
}
