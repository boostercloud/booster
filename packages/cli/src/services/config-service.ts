import { BoosterConfig } from '@boostercloud/framework-types'
import * as path from 'path'
import { exec } from 'child-process-promise'
import { wrapExecError } from '../common/errors'
import { checkItIsABoosterProject } from './project-checker'
import { withinWorkingDirectory } from './executor-service'
import { Environments } from '@boostercloud/framework-types/dist/environment'

export async function compileProjectAndLoadConfig(selectedEnvironment: keyof Environments): Promise<BoosterConfig> {
  const userProjectPath = process.cwd()
  await checkItIsABoosterProject()
  await compileProject(userProjectPath)
  const userConfig = await readProjectConfig(userProjectPath)
  userConfig.selectedEnvironment = selectedEnvironment
  return userConfig
}

async function compileProject(projectPath: string): Promise<void> {
  try {
    await withinWorkingDirectory(projectPath, () => {
      return exec('npm run compile')
    })
  } catch (e) {
    throw wrapExecError(e, 'Project contains compilation errors')
  }
}

function readProjectConfig(userProjectPath: string): Promise<BoosterConfig> {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const userProject = require(path.join(userProjectPath, 'dist', 'index.js'))
  return new Promise((resolve): void => {
    userProject.Booster.configure((config: BoosterConfig): void => {
      resolve(config)
    })
  })
}
