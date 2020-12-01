import { exec } from 'child-process-promise'
import { wrapExecError } from '../common/errors'
import * as path from 'path'

export async function pruneDevDependencies(): Promise<void> {
  try {
    await exec('npx yarn install --production --no-bin-links', { cwd: projectDir() })
  } catch (e) {
    throw wrapExecError(e, 'Could not prune dev dependencies')
  }
}

export async function reinstallDependencies(skipRestoreDependencies: boolean): Promise<void> {
  try {
    if (!skipRestoreDependencies) {
      await exec('npx yarn install', { cwd: projectDir() })
    }
  } catch (e) {
    throw wrapExecError(e, 'Could not reinstall dependencies')
  }
}

function projectDir(): string {
  return path.join(process.cwd())
}
