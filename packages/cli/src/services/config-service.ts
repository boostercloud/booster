import { BoosterApp, BoosterConfig } from '@boostercloud/framework-types'
import * as path from 'path'
import { exec } from 'child-process-promise'
import { wrapExecError } from '../common/errors'
import { checkItIsABoosterProject } from './project-checker'
import { currentEnvironment } from './environment'
import { pruneDevDependencies } from './dependencies'

type CompileAndLoadOptions = {
  production: boolean
}

export async function compileProjectAndLoadConfig(opts?: CompileAndLoadOptions): Promise<BoosterConfig> {
  const userProjectPath = process.cwd()
  await checkItIsABoosterProject()
  if (opts?.production) {
    await pruneDevDependencies()
  }
  await compileProject(userProjectPath)
  return readProjectConfig(userProjectPath)
}

async function compileProject(projectPath: string): Promise<void> {
  try {
    await exec('npx yarn clean && npx yarn compile', { cwd: projectPath })
  } catch (e) {
    throw wrapExecError(e, 'Project contains compilation errors')
  }
}

function readProjectConfig(userProjectPath: string): Promise<BoosterConfig> {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const userProject = loadUserProject(userProjectPath)
  return new Promise((resolve): void => {
    const app: BoosterApp = userProject.Booster
    app.configureCurrentEnv((config: BoosterConfig): void => {
      checkEnvironmentWasConfigured(app)
      resolve(config)
    })
  })
}

function loadUserProject(userProjectPath: string): { Booster: BoosterApp } {
  return require(path.join(userProjectPath, 'dist', 'index.js'))
}

function checkEnvironmentWasConfigured(app: BoosterApp): void {
  const currentEnv = currentEnvironment()
  if (!currentEnv) {
    throw new Error(
      "You haven't configured any environment. Please make sure you have at least one environment configured by calling 'Booster.configure' method (normally done inside the folder 'src/config')"
    )
  }
  if (!app.configuredEnvironments.has(currentEnv)) {
    throw new Error(
      `The environment '${currentEnv}' does not match any of the environments you used to configure your Booster project, which are: '${Array.from(
        app.configuredEnvironments
      ).join(', ')}'`
    )
  }
}
