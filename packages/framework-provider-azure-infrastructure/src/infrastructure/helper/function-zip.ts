import { BoosterConfig } from '@boostercloud/framework-types'
import { GraphqlFunction } from '../functions/graphql-function'
import { EventHandlerFunction } from '../functions/event-handler-function'
import { ScheduledFunctions } from '../functions/scheduled-functions'
import { SubscriptionsNotifierFunction } from '../functions/subscriptions-notifier-function'
import { FunctionDefinition } from '../types/functionDefinition'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'
import { ZipResource } from '../types/zip-resource'
import * as archiver from 'archiver'
import * as needle from 'needle'
import { azureCredentials, createWebSiteManagementClient } from './utils'
import { User } from '@azure/arm-appservice'
import { WebsocketConnectFunction } from '../functions/websocket-connect-function'
import { WebsocketDisconnectFunction } from '../functions/websocket-disconnect-function'
import { WebsocketMessagesFunction } from '../functions/websocket-messages-function'
import { SensorHealthFunction } from '../functions/sensor-health-function'

export class FunctionZip {
  static async deployZip(
    functionAppName: string,
    resourceGroupName: string,
    zipResource: ZipResource
  ): Promise<ZipResource> {
    const credentials = await FunctionZip.getCredentials(resourceGroupName, functionAppName)
    await FunctionZip.deployFunctionPackage(
      zipResource.path,
      credentials.publishingUserName ?? '',
      credentials.publishingPassword ?? '',
      credentials.name ?? ''
    )
    return zipResource
  }

  static async copyZip(featuresDefinitions: Array<FunctionDefinition>, fileName: string): Promise<ZipResource> {
    const zipPath = await this.createZip(featuresDefinitions, fileName)
    const originFile = path.basename(zipPath)
    const destinationFile = path.join(process.cwd(), originFile)
    fs.copyFileSync(zipPath, destinationFile)

    return { name: 'functions', path: destinationFile, fileName: originFile }
  }

  static async copyBaseZip(config: BoosterConfig): Promise<string> {
    const zipPath = await FunctionZip.createBaseZipFile(config, 'baseWebPubSubBinding')
    const originFile = path.basename(zipPath)
    const destinationFile = path.join(process.cwd(), originFile)
    fs.copyFileSync(zipPath, destinationFile)
    return destinationFile
  }

  private static async getCredentials(resourceGroupName: string, functionAppName: string): Promise<User> {
    const credentials = await azureCredentials()
    const webSiteManagementClient = await createWebSiteManagementClient(credentials)
    const poller = await webSiteManagementClient.webApps.beginListPublishingCredentials(
      resourceGroupName,
      functionAppName
    )
    return await poller.pollUntilDone()
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
    const sensorHealthHandlerFunctionDefinition = new SensorHealthFunction(config).getFunctionDefinition()
    let featuresDefinitions = [
      graphqlFunctionDefinition,
      eventHandlerFunctionDefinition,
      sensorHealthHandlerFunctionDefinition,
    ]
    if (config.enableSubscriptions) {
      const messagesFunctionDefinition = new WebsocketMessagesFunction(config).getFunctionDefinition()
      const disconnectFunctionDefinition = new WebsocketDisconnectFunction(config).getFunctionDefinition()
      const connectFunctionDefinition = new WebsocketConnectFunction(config).getFunctionDefinition()
      const subscriptionsNotifierFunctionDefinition = new SubscriptionsNotifierFunction(config).getFunctionDefinition()
      const subscriptionsFeaturesDefinitions = [
        connectFunctionDefinition,
        disconnectFunctionDefinition,
        messagesFunctionDefinition,
      ]
      featuresDefinitions.push(...subscriptionsFeaturesDefinitions, ...subscriptionsNotifierFunctionDefinition)
    }
    const scheduledFunctionsDefinition = new ScheduledFunctions(config).getFunctionDefinitions()
    if (scheduledFunctionsDefinition) {
      featuresDefinitions = featuresDefinitions.concat(scheduledFunctionsDefinition)
    }
    return featuresDefinitions
  }

  private static async createZip(functionDefinitions: Array<FunctionDefinition>, fileName: string): Promise<any> {
    const output = fs.createWriteStream(path.join(os.tmpdir(), fileName))

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
    if (!fs.existsSync(path.join('.deploy', 'host.json'))) {
      this.appendDefaultHostConfig(archive)
    }
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
    return `https://${functionAppName}.scm.azurewebsites.net/api/zipDeploy?isAsync=true`
  }

  private static async createBaseZipFile(config: BoosterConfig, name: string): Promise<string> {
    const output = fs.createWriteStream(path.join(os.tmpdir(), `${name}.zip`))
    const archive = archiver('zip', {
      zlib: { level: 9 }, // Sets the compression level.
    })

    archive.pipe(output)
    archive.directory('.deploy-base', false)

    if (config.enableSubscriptions) {
      this.appendBaseFunction(config, archive, name)
    }
    this.appendDefaultHostConfig(archive)
    await archive.finalize()
    return new Promise((resolve, reject) => {
      output.on('close', () => {
        resolve(output.path as string)
      })

      output.on('end', () => {
        resolve(output.path as string)
      })

      archive.on('warning', (err: any) => {
        if (err.code === 'ENOENT') {
          resolve(output.path as string)
        } else {
          reject(err)
        }
      })

      archive.on('error', (err: any) => {
        reject(err)
      })
    })
  }

  private static appendBaseFunction(config: BoosterConfig, archive: archiver.Archiver, name: string): void {
    const functionDefinition = new WebsocketMessagesFunction(config).getFunctionDefinition()
    const content = JSON.stringify(
      {
        bindings: [...functionDefinition.config.bindings],
      },
      null,
      2
    )
    archive.append(content, {
      name: name + '/function.json',
    })
  }

  private static appendDefaultHostConfig(archive: archiver.Archiver): void {
    const hostConfig = {
      version: '2.0',
      extensionBundle: {
        id: 'Microsoft.Azure.Functions.ExtensionBundle',
        version: '[4.*, 5.0.0)',
      },
    }
    archive.append(JSON.stringify(hostConfig, null, 2), {
      name: 'host.json',
    })
  }
}
