import { readFileSync, existsSync, removeSync } from 'fs-extra'
import * as path from 'path'
import { projectDir, ProjectInitializerConfig } from './project-initializer'
import Brand from '../common/brand'
import { filePath, getResourceType } from './generator'
import { classNameToFileName } from '../common/filenames'
import { logger } from '../services/logger'
import Prompter from '../services/user-prompt'
import Semver from '../services/semver'

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

  logger.info(Brand.mellancholize('Checking if resource already exists...'))

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

export async function checkCurrentDirBoosterVersion(version: string): Promise<void> {
  return checkBoosterVersion(version, process.cwd())
}

async function checkBoosterVersion(cliVersion: string, projectPath: string): Promise<void> {
  const projectVersion = await getBoosterVersion(projectPath)
  await compareVersionsAndDisplayMessages(cliVersion, projectVersion)
}

async function getBoosterVersion(projectPath: string): Promise<string> {
  const projectAbsolutePath = path.resolve(projectPath)
  try {
    const packageJsonContents = require(path.join(projectAbsolutePath, 'package.json'))
    const version = packageJsonContents.dependencies['@boostercloud/framework-core']
    const versionParts = version
      .replace('workspace:', '') // We remove the workspace protocol in case we're in the Booster monorepo
      .replace('^', '')
      .replace('.tgz', '')
      .split('-')
    return versionParts[versionParts.length - 1]
  } catch (e) {
    throw new Error(
      `There was an error when recognizing the application. Make sure you are in the root path of a Booster project:\n${e.message}`
    )
  }
}

class HigherCliVersionError extends Error {
  constructor(public cliVersion: string, public projectVersion: string, public section: string) {
    super(
      `CLI version ${cliVersion} is higher than your project Booster version ${projectVersion} in the '${section}' section. Please upgrade your project Booster dependencies.`
    )
  }
}

class LowerCliVersionError extends Error {
  constructor(public cliVersion: string, public projectVersion: string, public section: string) {
    super(
      `CLI version ${cliVersion} is lower than your project Booster version ${projectVersion}. Please upgrade your @boostercloud/cli to the same version with "npm install -g @boostercloud/cli@${projectVersion}"`
    )
  }
}

async function compareVersionsAndDisplayMessages(cliVersion: string, projectVersion: string): Promise<void> {
  const cliSemVersion = new Semver(cliVersion)
  const projectSemVersion = new Semver(projectVersion)
  if (cliSemVersion.equals(projectSemVersion)) {
    return
  }
  if (cliSemVersion.equalsInBreakingSection(projectSemVersion)) {
    if (cliSemVersion.equalsInFeatureSection(projectSemVersion)) {
      if (!cliSemVersion.equalsInFixSection(projectSemVersion)) {
        //differences in the 'fix' part
        logger.info(
          `WARNING: Project Booster version differs in the 'fix' section. CLI version: ${cliVersion}. Project Booster version: ${projectVersion}`
        )
      }
    } else if (cliSemVersion.greaterInFeatureSectionThan(projectSemVersion)) {
      //cli higher than project in 'feat' section
      throw new HigherCliVersionError(cliVersion, projectVersion, 'feature')
    } else {
      //cli lower than project in 'feat' section
      throw new LowerCliVersionError(cliVersion, projectVersion, 'feature')
    }
  } else if (cliSemVersion.greaterInBreakingSectionThan(projectSemVersion)) {
    //cli higher than project in 'breaking' section
    throw new HigherCliVersionError(cliVersion, projectVersion, 'breaking')
  } else {
    //cli lower than project in 'breaking' section
    throw new LowerCliVersionError(cliVersion, projectVersion, 'breaking')
  }
}
