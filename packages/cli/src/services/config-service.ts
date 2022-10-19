/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { BoosterApp, BoosterConfig } from '@boostercloud/framework-types'
import * as path from 'path'
import { guardError } from '../common/errors'
import { checkItIsABoosterProject } from './project-checker'
import { currentEnvironment } from './environment'
import { createSandboxProject, removeSandboxProject } from '../common/sandbox'
import { packageManagerInternals } from './package-manager'
import { gen, runWithLayer } from '@boostercloud/framework-types/src/effect'
import { LivePackageManager } from './package-manager/live.impl'

export const DEPLOYMENT_SANDBOX = '.deploy'

export async function createDeploymentSandbox(): Promise<string> {
  const config = await compileProjectAndLoadConfig(process.cwd())
  const sandboxRelativePath = createSandboxProject(DEPLOYMENT_SANDBOX, config.assets)
  // await installProductionDependencies(sandboxRelativePath)
  runWithLayer(packageManagerInternals.installProductionDependencies(), {
    layer: LivePackageManager,
    onError: guardError('Could not install production dependencies'),
  })
  return sandboxRelativePath
}

export async function cleanDeploymentSandbox(): Promise<void> {
  removeSandboxProject(DEPLOYMENT_SANDBOX)
}

export async function compileProjectAndLoadConfig(userProjectPath: string): Promise<BoosterConfig> {
  await checkItIsABoosterProject(userProjectPath)
  await compileProject(userProjectPath)
  return readProjectConfig(userProjectPath)
}

export async function compileProject(projectPath: string): Promise<void> {
  return runWithLayer(compileProjectEff(projectPath), {
    layer: LivePackageManager,
    onError: guardError('Project contains compilation errors'),
  })
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const compileProjectEff = (_projectPath: string) =>
  gen(function* ($) {
    yield* $(cleanProjectEff(_projectPath)) // FIXME: Run in projectPath
    yield* $(packageManagerInternals.runScript('build', [])) // FIXME: Run in projectPath
  })

export async function cleanProject(projectPath: string): Promise<void> {
  return runWithLayer(cleanProjectEff(projectPath), {
    layer: LivePackageManager,
    onError: guardError('Could not clean project'),
  })
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const cleanProjectEff = (_projectPath: string) =>
  // FIXME: Run in projectPath
  packageManagerInternals.runScript('clean', [])

function readProjectConfig(userProjectPath: string): Promise<BoosterConfig> {
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
  const projectIndexJSPath = path.resolve(path.join(userProjectPath, 'dist', 'index.js'))
  return require(projectIndexJSPath)
}

function checkEnvironmentWasConfigured(app: BoosterApp): void {
  if (app.configuredEnvironments.size == 0) {
    throw new Error(
      "You haven't configured any environment. Please make sure you have at least one environment configured by calling 'Booster.configure' method (normally done inside the folder 'src/config')"
    )
  }
  const currentEnv = currentEnvironment()
  if (!currentEnv) {
    throw new Error(
      "You haven't provided any environment. Please make sure you are using option '-e' with a valid environment name"
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
