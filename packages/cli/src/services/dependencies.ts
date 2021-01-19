import { exec } from 'child-process-promise'
import { wrapExecError } from '../common/errors'

export async function installProductionDependencies(projectPath: string): Promise<void> {
  try {
    await exec('npm install --production --no-bin-links', { cwd: projectPath })
  } catch (e) {
    throw wrapExecError(e, 'Could not install production dependencies')
  }
}
