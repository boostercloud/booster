import { BoosterApp, BoosterConfig } from '@boostercloud/framework-types'
import * as path from 'path'
import { exec } from 'child-process-promise'
import { wrapExecError } from '../common/errors'
import { checkItIsABoosterProject } from './project-checker'
import { withinWorkingDirectory } from './executor-service'

export async function compileProjectAndLoadConfig(): Promise<BoosterConfig> {
  const userProjectPath = process.cwd()
  await checkItIsABoosterProject()
  await compileProject(userProjectPath)
  return readProjectConfig(userProjectPath)
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
    const projectBooster: BoosterApp = userProject.Booster
    projectBooster.configureCurrentEnv((config: BoosterConfig): void => {
      checkEnvironmentWasConfigured(config)
      resolve(config)
    })
  })
}

function checkEnvironmentWasConfigured(config: BoosterConfig): void {
  if (!config.configuredEnvironments.has(config.environmentName)) {
    const errorMessage = config.configuredEnvironments.size
      ? `The environment '${config.environmentName}' does not match any of the environments` +
        ` you used to configure your Booster project, which are: '${Array.from(config.configuredEnvironments).join(
          ', '
        )}'`
      : "You haven't configured any environment. Please make sure you have at least one environment configured by calling 'Booster.configure' method (normally done inside the folder 'src/config')"
    throw new Error(errorMessage)
  }
}
