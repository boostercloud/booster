import * as fs from 'fs-extra'
import { Provider } from '@boostercloud/framework-types'
import { exec } from 'child-process-promise'
import * as Mustache from 'mustache'
import * as path from 'path'
import * as tsConfig from '../templates/project/tsconfig-json'
import * as esLintIgnore from '../templates/project/eslintignore'
import * as gitIgnore from '../templates/project/gitignore'
import * as packageJson from '../templates/project/package-json'
import * as configTs from '../templates/project/config-ts'
import * as indexTs from '../templates/project/index-ts'
import { wrapExecError } from '../common/errors'
import { withinWorkingDirectory } from "./executor-service";

export async function generateConfigFiles(config: ProjectInitializerConfig): Promise<void> {
  await Promise.all(filesToGenerate.map(renderToFile(config)))
}

export async function installDependencies(config: ProjectInitializerConfig): Promise<void> {
  try {
    await withinWorkingDirectory(projectDir(config), () => {
      return exec('npm install')
    })
  } catch (e) {
    throw wrapExecError(e, 'Could not install dependencies')
  }
}

export async function generateRootDirectory(config: ProjectInitializerConfig): Promise<void> {
  const srcDir = path.join(projectDir(config), 'src')
  const dirs = [
    [srcDir, 'commands'],
    [srcDir, 'common'],
    [srcDir, 'config'],
    [srcDir, 'entities'],
    [srcDir, 'events'],
  ]
  await Promise.all(dirs.map(createDirectory))
}

export interface ProjectInitializerConfig {
  projectName: string
  description: string
  version: string
  author: string
  homepage: string
  license: string
  repository: string
  provider: Provider
  boosterVersion: string
}

function renderToFile(templateData: ProjectInitializerConfig): (_: [Array<string>, string]) => Promise<void> {
  return async ([filepath, template]: [Array<string>, string]): Promise<void> => {
    const rendered = Mustache.render(template, templateData)
    const renderPath = path.join(process.cwd(), templateData.projectName, ...filepath)
    return fs.outputFile(renderPath, rendered)
  }
}

function projectDir(config: ProjectInitializerConfig): string {
  return path.join(process.cwd(), config.projectName)
}

async function createDirectory(paths: string[]): Promise<void> {
  return fs.mkdirs(path.join(...paths))
}

const filesToGenerate: Array<[Array<string>, string]> = [
  [['.eslintignore'], esLintIgnore.template],
  [['.gitignore'], gitIgnore.template],
  [['package.json'], packageJson.template],
  [['tsconfig.json'], tsConfig.template],
  [['src', 'config', 'config.ts'], configTs.template],
  [['src', 'index.ts'], indexTs.template],
]
