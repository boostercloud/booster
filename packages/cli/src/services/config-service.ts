import { BoosterApp, BoosterConfig } from '@boostercloud/framework-types'
import * as path from 'path'
import { guardError } from '../common/errors'
import { checkItIsABoosterProject } from './project-checker'
import { currentEnvironment } from './environment'
import { createSandboxProject, removeSandboxProject } from '../common/sandbox'
import { PackageManagerService } from './package-manager'
import { gen, unsafeRunEffect } from '@boostercloud/framework-types/dist/effect'
import { LivePackageManager } from './package-manager/live.impl'

export const DEPLOYMENT_SANDBOX = path.join(process.cwd(), '.deploy')

export async function createDeploymentSandbox(): Promise<string> {
  const config = await compileProjectAndLoadConfig(process.cwd())
  const sandboxRelativePath = createSandboxProject(DEPLOYMENT_SANDBOX, config.assets)
  const effect = gen(function* ($) {
    const { setProjectRoot, installProductionDependencies } = yield* $(PackageManagerService)
    yield* $(setProjectRoot(sandboxRelativePath))
    yield* $(installProductionDependencies())
  })
  await unsafeRunEffect(effect, {
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
  return await unsafeRunEffect(compileProjectEff(projectPath), {
    layer: LivePackageManager,
    onError: guardError('Project contains compilation errors'),
  })
}

const compileProjectEff = (projectPath: string) =>
  gen(function* ($) {
    const { setProjectRoot, runScript } = yield* $(PackageManagerService)
    yield* $(setProjectRoot(projectPath))
    yield* $(cleanProjectEff(projectPath))
    return yield* $(runScript('build', []))
  })

export async function cleanProject(projectPath: string): Promise<void> {
  return unsafeRunEffect(cleanProjectEff(projectPath), {
    layer: LivePackageManager,
    onError: guardError('Could not clean project'),
  })
}

const cleanProjectEff = (projectPath: string) =>
  gen(function* ($) {
    const { setProjectRoot, runScript } = yield* $(PackageManagerService)
    yield* $(setProjectRoot(projectPath))
    yield* $(runScript('clean', []))
  })

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
