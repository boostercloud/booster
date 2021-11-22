import * as fsExtra from 'fs-extra'
import * as path from 'path'
import { BoosterApp, BoosterConfig } from '@boostercloud/framework-types'
import * as Mustache from 'mustache'

const MAX_TERRAFORM_SIZE_NAME = 24

export function renderToFile(config: BoosterConfig): (_: [Array<string>, string]) => Promise<void> {
  return async ([filepath, template]: [Array<string>, string]): Promise<void> => {
    const rendered = Mustache.render(template, config)
    const renderPath = path.join(process.cwd(), ...filepath)
    return fsExtra.outputFile(renderPath, rendered)
  }
}

export function toTerraformName(name: string, suffix?: string): string {
  const cleanName = name.toLowerCase().replace(/([-_])/gi, '')
  if (!suffix) {
    return cleanName.substr(0, MAX_TERRAFORM_SIZE_NAME)
  }
  const cleanSuffix = suffix.toLowerCase().replace(/([-_])/gi, '')
  const suffixLength = cleanSuffix.length
  if (suffixLength > MAX_TERRAFORM_SIZE_NAME) {
    throw new Error(`Error. Suffix could not be greater than ${MAX_TERRAFORM_SIZE_NAME}`)
  }
  const suffixEndSize = suffixLength > MAX_TERRAFORM_SIZE_NAME ? suffixLength - MAX_TERRAFORM_SIZE_NAME : suffixLength
  const validName = cleanName.slice(-suffixEndSize)

  const suffixSize = MAX_TERRAFORM_SIZE_NAME - validName.length
  const validSuffix = cleanSuffix.substr(0, suffixSize)
  return validName + validSuffix
}

export function toAzureName(name: string, maxSize = 30): string {
  return name.replace(/([-_])/gi, '').substr(0, maxSize - 1)
}

export function getDeployRegion(): string {
  const region = process.env['REGION']
  if (!region) {
    throw new Error(
      "REGION was not properly loaded and is required to run the deploy process. Check that you've set it in REGION environment variable."
    )
  }
  return region
}

export function buildAppPrefix(config: BoosterConfig): string {
  return toTerraformName(config.appName + config.environmentName)
}

export function readProjectConfig(userProjectPath: string): BoosterConfig {
  const userProject = loadUserProject(userProjectPath)
  const app: BoosterApp = userProject.Booster
  return app.config
}

function loadUserProject(userProjectPath: string): { Booster: BoosterApp } {
  const projectIndexJSPath = path.resolve(path.join(userProjectPath, 'dist', 'index.js'))
  return require(projectIndexJSPath)
}
