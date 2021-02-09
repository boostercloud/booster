import { readFileSync, existsSync, removeSync } from 'fs-extra'
import * as path from 'path'
import { projectDir, ProjectInitializerConfig } from './project-initializer'
import Brand from '../common/brand'
import { filePath, getResourceType } from './generator'
import { classNameToFileName } from '../common/filenames'
import { oraLogger } from './logger'
import { logger } from '../services/logger'
import Prompter from '../services/user-prompt'
import { updatePackageJsonDependencyVersions } from '../services/project-updater'

function checkIndexFileIsBooster(indexFilePath: string): void {
  const contents = readFileSync(indexFilePath)
  if (!contents.includes('Booster.start(')) {
    throw new Error(
      'The main application file does not start a Booster application. Verify you are in the right project'
    )
  }
}

export async function checkCurrentDirIsABoosterProject(): Promise<void> {
  return checkItIsABoosterProject(process.cwd())
}

export async function checkItIsABoosterProject(projectPath: string): Promise<void> {
  const projectAbsolutePath = path.resolve(projectPath)
  try {
    const tsConfigJsonContents = require(path.join(projectAbsolutePath, 'tsconfig.json'))
    const indexFilePath = path.normalize(
      path.join(projectAbsolutePath, tsConfigJsonContents.compilerOptions.rootDir, 'index.ts')
    )
    checkIndexFileIsBooster(indexFilePath)
  } catch (e) {
    throw new Error(
      `There was an error when recognizing the application. Make sure you are in the root path of a Booster project:\n${e.message}`
    )
  }
}

export async function checkProjectAlreadyExists(name: string): Promise<void> {
  const projectDirectoryPath = projectDir({ projectName: name } as ProjectInitializerConfig)
  const projectDirectoryExists = existsSync(projectDirectoryPath)

  if (projectDirectoryExists) {
    await Prompter.confirmPrompt({
      message: Brand.dangerize(`Project folder "${name}" already exists. Do you want to overwrite it?`),
    }).then((confirm) => {
      if (!confirm) throw new Error("The folder you're trying to use already exists. Please use another project name")
      removeSync(projectDirectoryPath)
    })
  }
}

export async function checkResourceExists(name: string, placementDir: string, extension: string): Promise<void> {
  const resourcePath = filePath({ name, placementDir, extension })
  const resourceExists = existsSync(resourcePath)
  const resourceName = classNameToFileName(name)
  const resourceType = getResourceType(placementDir)

  oraLogger.info(Brand.mellancholize('Checking if resource already exists...'))

  if (resourceExists) {
    await Prompter.confirmPrompt({
      message: Brand.dangerize(`Resource: "${resourceName}${extension}" already exists. Do you want to overwrite it?`),
    }).then((confirm) => {
      if (!confirm)
        throw new Error(
          `The '${resourceType}' resource "${resourceName}${extension}" already exists. Please use another resource name`
        )
      removeSync(resourcePath)
    })
  }
}

export async function checkCurrentDirBoosterVersion(userAgent: string): Promise<void> {
  return checkBoosterVersion(userAgent, process.cwd())
}

async function checkBoosterVersion(userAgent: string, projectPath: string): Promise<void> {
  const projectVersion = await getBoosterVersion(projectPath)
  const cliVersion = userAgent.split(' ')[0].split('/')[2]
  await compareVersionsAndDisplayMessages(cliVersion, projectVersion, projectPath)
}

async function getBoosterVersion(projectPath: string): Promise<string> {
  const projectAbsolutePath = path.resolve(projectPath)
  try {
    const packageJsonContents = require(path.join(projectAbsolutePath, 'package.json'))
    const version = packageJsonContents.dependencies['@boostercloud/framework-core']
    const versionParts = version.replace('^','').replace('.tgz','').split('-')
    return versionParts[versionParts.length-1]    
  } catch (e) {
    throw new Error(
      `There was an error when recognizing the application. Make sure you are in the root path of a Booster project:\n${e.message}`
    )
  }
}

async function compareVersionsAndDisplayMessages(cliVersion: string, projectVersion: string, projectPath: string): Promise<void> {
  if (cliVersion === projectVersion)  { return }
  const cliVersionParts = cliVersion.split('.').map((v) => parseInt(v,10))
  const projectVersionParts = projectVersion.split('.').map((v) => parseInt(v,10))
  if (cliVersionParts.length !== projectVersionParts.length) {
    throw new Error(`Versions must have the same length. CLI version: ${cliVersion}. Project Booster version: ${projectVersion}`)
  }
  if (projectVersionParts.length !== 3) {
    throw new Error(`Versions must follow semantic convention X.Y.Z | CLI version: ${cliVersion}. Project Booster version: ${projectVersion}`)
  }
  if (cliVersionParts[0] === projectVersionParts[0]) {
    if (cliVersionParts[1] === projectVersionParts[1]) {
      //differences in the 'fix' part
      if (cliVersionParts[2] !== projectVersionParts[2]) {
        logger.info(`WARNING: Project Booster version differs in the 'fix' section. CLI version: ${cliVersion}. Project Booster version: ${projectVersion}`)
      }
    } else if (cliVersionParts[1] > projectVersionParts[1]) {
      //cli higher than project in 'feat' section
      const promptMsg = `@boostercloud/cli version ${cliVersion} is higher than project version (${projectVersion}) in the 'feature' section. Do you want to upgrade your project dependencies?`
      const value: string = await new Prompter().defaultOrChoose(undefined,promptMsg,['Yes','No'])
      if (value == 'Yes') {
        await updatePackageJsonDependencyVersions(cliVersion, projectPath) //update package.json
        logger.info(`package.json Booster dependencies have been updated to version ^${cliVersion}.`)
      } else {
        throw new Error(`CLI version ${cliVersion} is higher than your project Booster version ${projectVersion}. Please upgrade your project dependencies.`)
      }
    } else {
      //cli lower than project in 'feat' section
      throw new Error(`CLI version ${cliVersion} is lower than your project Booster version ${projectVersion}. Please upgrade your @boostercloud/cli to the same version with npm`)
    }
  } else if (cliVersionParts[0] > projectVersionParts[0]) {
    //cli higher than project in 'breaking' section
    throw new Error(`CLI version ${cliVersion} is higher than your project Booster version (${projectVersion}). Please upgrade your project dependencies or install the same CLI version with \'npm install @boostercloud/cli@${projectVersion}\'.`)
  } else {
    //cli lower than project in 'breaking' section
    throw new Error(`CLI version ${cliVersion} is lower than your project Booster version ${projectVersion}. Please upgrade your @boostercloud/cli to the same version with npm`)
  }
}