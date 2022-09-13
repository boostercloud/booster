import * as fsExtra from 'fs-extra'
import * as path from 'path'
import { BoosterApp, BoosterConfig } from '@boostercloud/framework-types'
import * as Mustache from 'mustache'
import { configuration } from './params'
import { WebSiteManagementClient as WebSiteManagement } from '@azure/arm-appservice'
import { ResourceManagementClient } from '@azure/arm-resources'
import { TokenCredential, ClientSecretCredential } from '@azure/identity'

const MAX_TERRAFORM_SIZE_NAME = 24
const MAX_RESOURCE_GROUP_NAME_SIZE = 20

export function renderToFile(config: BoosterConfig): (_: [Array<string>, string]) => Promise<void> {
  return async ([filepath, template]: [Array<string>, string]): Promise<void> => {
    const renderPath = path.join(process.cwd(), ...filepath)
    if (fsExtra.existsSync(renderPath)) return
    const rendered = Mustache.render(template, config)
    return fsExtra.outputFile(renderPath, rendered)
  }
}

function terraformCleanText(text: string): string {
  return text.toLowerCase().replace(/([-_])/gi, '')
}

export function toTerraformName(name: string, suffix = ''): string {
  const cleanSuffix = terraformCleanText(suffix)
  if (cleanSuffix.length > MAX_TERRAFORM_SIZE_NAME) {
    throw new Error(`Error. Suffix could not be greater than ${MAX_TERRAFORM_SIZE_NAME}`)
  }

  const cleanName = terraformCleanText(name)
  const totalLength = cleanName.length + cleanSuffix.length
  if (totalLength <= MAX_TERRAFORM_SIZE_NAME) {
    return cleanName + cleanSuffix
  }

  if (cleanName.length <= cleanSuffix.length) {
    return (cleanName + cleanSuffix).substr(0, MAX_TERRAFORM_SIZE_NAME - 1)
  }

  const extraLength = totalLength - MAX_TERRAFORM_SIZE_NAME
  const fixedCleanName = cleanName.substr(extraLength)
  return fixedCleanName + cleanSuffix
}

export function toAzureName(name: string, maxSize = 24): string {
  return name.replace(/([-_])/gi, '').substr(0, maxSize)
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
  return terraformCleanText(config.appName + config.environmentName).substr(0, MAX_TERRAFORM_SIZE_NAME)
}

export function readProjectConfig(userProjectPath: string): BoosterConfig {
  const userProject = loadUserProject(userProjectPath)
  const app: BoosterApp = userProject.Booster
  return app.config
}

export async function createWebSiteManagementClient(credentials: TokenCredential): Promise<WebSiteManagement> {
  return new WebSiteManagement(credentials, configuration.subscriptionId)
}

export async function createResourceManagementClient(credentials: TokenCredential): Promise<ResourceManagementClient> {
  return new ResourceManagementClient(credentials, configuration.subscriptionId)
}

export async function azureCredentials(): Promise<TokenCredential> {
  const applicationTokenCredentials = new ClientSecretCredential(
    configuration.tenantId,
    configuration.appId,
    configuration.secret
  )

  if (!applicationTokenCredentials) {
    throw new Error(
      'Unable to login with Service Principal. Please verified provided appId, secret and subscription ID in .env file are correct.'
    )
  }

  return applicationTokenCredentials
}

export function createResourceGroupName(appName: string, environmentName: string): string {
  return `${toAzureName(appName + environmentName, MAX_RESOURCE_GROUP_NAME_SIZE)}rg`
}

export function createFunctionResourceGroupName(resourceGroupName: string): string {
  return `${resourceGroupName}func`
}

export function createApiManagementName(resourceGroupName: string): string {
  return `${resourceGroupName}apis`
}

function loadUserProject(userProjectPath: string): { Booster: BoosterApp } {
  const projectIndexJSPath = path.resolve(path.join(userProjectPath, 'dist', 'index.js'))
  return require(projectIndexJSPath)
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function sleep(ms: number): Promise<any> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * wait for a resource to be ready or reject if not ready after a timeout
 */
export async function waitForIt<TResult>(
  tryFunction: () => Promise<TResult>,
  checkResult: (result: TResult) => boolean,
  errorMessage: string,
  timeoutMs = 180000,
  tryEveryMs = 1000
): Promise<TResult> {
  console.debug('[waitForIt] start')
  const start = Date.now()
  return doWaitFor()

  async function doWaitFor(): Promise<TResult> {
    console.debug('.')
    const res = await tryFunction()
    const expectedResult = checkResult(res)
    if (expectedResult) {
      console.debug('[waitForIt] match')
      return res
    }
    const elapsed = Date.now() - start

    if (elapsed > timeoutMs) {
      throw new Error(errorMessage)
    }
    const nextExecutionDelay = (timeoutMs - elapsed) % tryEveryMs
    await sleep(nextExecutionDelay)
    return doWaitFor()
  }
}
