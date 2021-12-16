import { BoosterConfig } from '@boostercloud/framework-types'
import { GraphqlFunction } from '../functions/graphql-function'
import { EventHandlerFunction } from '../functions/event-handler-function'
import { ScheduledFunctions } from '../functions/scheduled-functions'
import { FunctionDefinition } from '../types/functionDefinition'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'
import { ZipResource } from '../types/zip-resource'
import * as archiver from 'archiver'
import * as needle from 'needle'
import { azureCredentials, createWebSiteManagementClient } from './utils'
import { User } from 'azure-arm-website/lib/models'

export class FunctionZip {
  static async deployZip(
    functionAppName: string,
    resourceGroupName: string,
    zipResource: ZipResource
  ): Promise<ZipResource> {
    const credentials = await FunctionZip.getCredentials(resourceGroupName, functionAppName)
    await FunctionZip.deployFunctionPackage(
      zipResource.path,
      credentials.publishingUserName,
      credentials.publishingPassword ?? '',
      credentials.name ?? ''
    )
    return zipResource
  }

  static async copyZip(featuresDefinitions: Array<FunctionDefinition>): Promise<ZipResource> {
    const zipPath = await this.createZip(featuresDefinitions)
    const originFile = path.basename(zipPath)
    const destinationFile = path.join(process.cwd(), originFile)
    fs.copyFileSync(zipPath, destinationFile)

    return { name: 'functions', path: destinationFile, fileName: originFile }
  }

  private static async getCredentials(resourceGroupName: string, functionAppName: string): Promise<User> {
    const credentials = await azureCredentials()
    const webSiteManagementClient = await createWebSiteManagementClient(credentials)
    return await webSiteManagementClient.webApps.listPublishingCredentials(resourceGroupName, functionAppName)
  }

  private static async deployFunctionPackage(
    packagePath: string,
    username: string,
    password: string,
    functionAppName: string
  ): Promise<any> {
    needle.defaults({
      open_timeout: 0,
    })

    return needle('post', this.getZipDeployUrl(functionAppName), fs.createReadStream(packagePath), {
      username: username,
      password: password,
    })
  }

  static buildAzureFunctions(config: BoosterConfig): Array<FunctionDefinition> {
    const graphqlFunctionDefinition = new GraphqlFunction(config).getFunctionDefinition()
    const eventHandlerFunctionDefinition = new EventHandlerFunction(config).getFunctionDefinition()
    let featuresDefinitions = [graphqlFunctionDefinition, eventHandlerFunctionDefinition]
    const scheduledFunctionsDefinition = new ScheduledFunctions(config).getFunctionDefinitions()
    if (scheduledFunctionsDefinition) {
      featuresDefinitions = featuresDefinitions.concat(scheduledFunctionsDefinition)
    }
    return featuresDefinitions
  }

  private static async createZip(functionDefinitions: Array<FunctionDefinition>): Promise<any> {
    const output = fs.createWriteStream(path.join(os.tmpdir(), 'functionApp.zip'))

    const archive = archiver('zip', {
      zlib: { level: 9 }, // Sets the compression level.
    })

    archive.pipe(output)
    archive.directory('.deploy', false)
    functionDefinitions.forEach((functionDefinition: FunctionDefinition) => {
      archive.append(JSON.stringify(functionDefinition.config, null, 2), {
        name: functionDefinition.name + '/function.json',
      })
    })
    await archive.finalize()

    return new Promise((resolve, reject) => {
      output.on('close', () => {
        resolve(output.path)
      })

      output.on('end', () => {
        resolve(output.path)
      })

      archive.on('warning', (err: any) => {
        if (err.code === 'ENOENT') {
          resolve(output.path)
        } else {
          reject(err)
        }
      })

      archive.on('error', (err: any) => {
        reject(err)
      })
    })
  }

  private static getZipDeployUrl(functionAppName: string): string {
    return `https://${functionAppName}.scm.azurewebsites.net/api/zipDeploy`
  }
}
