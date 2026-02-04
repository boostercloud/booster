import { BoosterConfig } from '@boostercloud/framework-types'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'
import { ZipResource } from '../types/zip-resource'
import { FunctionDefinition } from '../types/functionDefinition'
import * as archiver from 'archiver'
import * as needle from 'needle'
import { azureCredentials, createWebSiteManagementClient } from './utils'
import { User } from '@azure/arm-appservice'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { FunctionsCodeGenerator } from './functions-code-generator'

export class FunctionZip {
  static async deployZip(
    config: BoosterConfig,
    functionAppName: string,
    resourceGroupName: string,
    zipResource: ZipResource
  ): Promise<ZipResource> {
    const logger = getLogger(config, 'function-zip#deployZip')
    logger.info('Uploading zip file')
    const credentials = await FunctionZip.getCredentials(resourceGroupName, functionAppName)
    await FunctionZip.deployFunctionPackage(
      zipResource.path,
      credentials.publishingUserName ?? '',
      credentials.publishingPassword ?? '',
      credentials.name ?? ''
    )
    logger.info('Zip file uploaded')
    return zipResource
  }

  /**
   * Creates a ZIP file with the v4 programming model structure.
   * Instead of function.json files, we generate a functions.js file.
   */
  static async copyZip(config: BoosterConfig, fileName: string, hostJsonPath?: string): Promise<ZipResource> {
    const zipPath = await this.createZipV4(config, fileName, hostJsonPath)
    const originFile = path.basename(zipPath)
    const destinationFile = path.join(process.cwd(), originFile)
    fs.copyFileSync(zipPath, destinationFile)

    return { name: 'functions', path: destinationFile, fileName: originFile }
  }

  /**
   * Creates a ZIP file for consumer functions (Event Hub consumers).
   */
  static async copyConsumerZip(config: BoosterConfig, fileName: string, hostJsonPath?: string): Promise<ZipResource> {
    const zipPath = await this.createConsumerZipV4(config, fileName, hostJsonPath)
    const originFile = path.basename(zipPath)
    const destinationFile = path.join(process.cwd(), originFile)
    fs.copyFileSync(zipPath, destinationFile)

    return { name: 'consumer-functions', path: destinationFile, fileName: originFile }
  }

  /**
   * Creates a ZIP file for a rocket using the v4 programming model.
   * The rocket provides its own functions.js content.
   * @param config - Booster configuration
   * @param functionCode - The functions.js content provided by the rocket
   * @param fileName - The name of the ZIP file to create
   * @param hostJsonPath - Optional path to a custom host.json file
   * @returns A Promise that resolves to a ZipResource representing the created ZIP file
   */
  static async copyZipV4ForRocket(
    config: BoosterConfig,
    functionCode: string,
    fileName: string,
    hostJsonPath?: string
  ): Promise<ZipResource> {
    const zipPath = await this.createZipV4ForRocket(config, functionCode, fileName, hostJsonPath)
    const originFile = path.basename(zipPath)
    const destinationFile = path.join(process.cwd(), originFile)
    fs.copyFileSync(zipPath, destinationFile)

    return { name: 'functions', path: destinationFile, fileName: originFile }
  }

  /**
   * Creates a ZIP file for a rocket using the v4 programming model.
   * Instead of generating functions.js via FunctionsCodeGenerator, the rocket provides its own.
   * @param config - Booster configuration
   * @param functionCode - The functions.js content provided by the rocket
   * @param fileName - The name of the ZIP file to create
   * @param hostJsonPath - Optional path to a custom host.json file
   * @private
   * @returns A Promise that resolves to the path of the created ZIP file
   */
  private static async createZipV4ForRocket(
    config: BoosterConfig,
    functionCode: string,
    fileName: string,
    hostJsonPath?: string
  ): Promise<string> {
    const logger = getLogger(config, 'function-zip#createZipV4ForRocket')
    logger.info('Creating Azure Functions v4 ZIP package for rocket')

    const output = fs.createWriteStream(path.join(os.tmpdir(), fileName))

    const archive = archiver('zip', {
      zlib: { level: 9 },
    })

    archive.pipe(output)

    // Include the compiled code from .deploy folder, excluding files we'll generate
    archive.glob('**/*', {
      cwd: '.deploy',
      ignore: ['package.json', 'host.json', 'functions.js'],
      dot: true,
    })

    // Append the rocket-provided functions.js
    archive.append(functionCode, { name: 'functions.js' })

    // Include host.json
    if (hostJsonPath) {
      this.appendCustomHostConfig(archive, hostJsonPath)
    } else {
      this.appendDefaultHostConfig(archive)
    }

    // Include package.json with the correct main entry point for v4
    this.appendPackageJson(archive, config)

    await archive.finalize()

    return new Promise((resolve, reject) => {
      output.on('close', () => {
        logger.info('Azure Functions v4 ZIP package for rocket created')
        resolve(output.path as string)
      })

      output.on('end', () => {
        resolve(output.path as string)
      })

      archive.on('warning', (err: { code?: string }) => {
        if (err.code === 'ENOENT') {
          resolve(output.path as string)
        } else {
          reject(err)
        }
      })

      archive.on('error', (err: Error) => {
        reject(err)
      })
    })
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
  ): Promise<unknown> {
    needle.defaults({
      open_timeout: 0,
    })

    return needle('post', this.getZipDeployUrl(functionAppName), fs.createReadStream(packagePath), {
      username: username,
      password: password,
    })
  }

  /**
   * Creates a ZIP file using the v4 programming model.
   * - Includes the compiled code from .deploy folder
   * - Generates and includes functions.js for function registration
   * - Includes host.json
   */
  private static async createZipV4(config: BoosterConfig, fileName: string, hostJsonPath?: string): Promise<string> {
    const logger = getLogger(config, 'function-zip#createZipV4')
    logger.info('Creating Azure Functions v4 ZIP package')

    const output = fs.createWriteStream(path.join(os.tmpdir(), fileName))

    const archive = archiver('zip', {
      zlib: { level: 9 },
    })

    archive.pipe(output)

    // Include the compiled code from .deploy folder, excluding files we'll generate
    archive.glob('**/*', {
      cwd: '.deploy',
      ignore: ['package.json', 'host.json', 'functions.js'],
      dot: true,
    })

    // Generate and include the functions.js file
    const codeGenerator = new FunctionsCodeGenerator(config)
    const functionsCode = codeGenerator.generateFunctionsCode()
    archive.append(functionsCode, { name: 'functions.js' })

    // Include host.json
    if (hostJsonPath) {
      this.appendCustomHostConfig(archive, hostJsonPath)
    } else {
      this.appendDefaultHostConfig(archive)
    }

    // Include package.json with the correct main entry point for v4
    this.appendPackageJson(archive, config)

    await archive.finalize()

    return new Promise((resolve, reject) => {
      output.on('close', () => {
        logger.info('Azure Functions v4 ZIP package created')
        resolve(output.path as string)
      })

      output.on('end', () => {
        resolve(output.path as string)
      })

      archive.on('warning', (err: { code?: string }) => {
        if (err.code === 'ENOENT') {
          resolve(output.path as string)
        } else {
          reject(err)
        }
      })

      archive.on('error', (err: Error) => {
        reject(err)
      })
    })
  }

  /**
   * Creates a ZIP file for consumer functions using the v4 programming model.
   */
  private static async createConsumerZipV4(
    config: BoosterConfig,
    fileName: string,
    hostJsonPath?: string
  ): Promise<string> {
    const logger = getLogger(config, 'function-zip#createConsumerZipV4')
    logger.info('Creating Azure Consumer Functions v4 ZIP package')

    const output = fs.createWriteStream(path.join(os.tmpdir(), fileName))

    const archive = archiver('zip', {
      zlib: { level: 9 },
    })

    archive.pipe(output)

    // Include the compiled code from .deploy folder, excluding files we'll generate
    archive.glob('**/*', {
      cwd: '.deploy',
      ignore: ['package.json', 'host.json', 'functions.js'],
      dot: true,
    })

    // Generate and include the consumer functions.js file
    const codeGenerator = new FunctionsCodeGenerator(config)
    const functionsCode = codeGenerator.generateConsumerFunctionsCode()
    if (functionsCode) {
      archive.append(functionsCode, { name: 'functions.js' })
    }

    // Include host.json
    if (hostJsonPath) {
      this.appendCustomHostConfig(archive, hostJsonPath)
    } else {
      this.appendDefaultHostConfig(archive)
    }

    // Include package.json with the correct main entry point for v4
    this.appendPackageJson(archive, config)

    await archive.finalize()

    return new Promise((resolve, reject) => {
      output.on('close', () => {
        logger.info('Azure Consumer Functions v4 ZIP package created')
        resolve(output.path as string)
      })

      output.on('end', () => {
        resolve(output.path as string)
      })

      archive.on('warning', (err: { code?: string }) => {
        if (err.code === 'ENOENT') {
          resolve(output.path as string)
        } else {
          reject(err)
        }
      })

      archive.on('error', (err: Error) => {
        reject(err)
      })
    })
  }

  private static async createBaseZipFile(config: BoosterConfig, name: string): Promise<string> {
    const output = fs.createWriteStream(path.join(os.tmpdir(), `${name}.zip`))
    const archive = archiver('zip', {
      zlib: { level: 9 },
    })

    archive.pipe(output)

    // Include files from .deploy-base folder, excluding files we'll generate
    archive.glob('**/*', {
      cwd: '.deploy-base',
      ignore: ['package.json', 'host.json', 'functions.js'],
      dot: true,
    })

    if (config.enableSubscriptions) {
      // For base zip, generate minimal Web PubSub binding function
      const baseFunctionsCode = this.generateBaseWebPubSubFunction()
      archive.append(baseFunctionsCode, { name: 'functions.js' })
    }

    // Include host.json
    this.appendDefaultHostConfig(archive)

    // Include package.json with the correct main entry point for v4
    this.appendPackageJson(archive, config)

    await archive.finalize()
    return new Promise((resolve, reject) => {
      output.on('close', () => {
        resolve(output.path as string)
      })

      output.on('end', () => {
        resolve(output.path as string)
      })

      archive.on('warning', (err: { code?: string }) => {
        if (err.code === 'ENOENT') {
          resolve(output.path as string)
        } else {
          reject(err)
        }
      })

      archive.on('error', (err: Error) => {
        reject(err)
      })
    })
  }

  private static generateBaseWebPubSubFunction(): string {
    // IMPORTANT: The 'connection' property is required for the Azure Functions runtime
    // to properly load the WebPubSub extension and create the webpubsub_extension system key.
    // Without it, the extension doesn't initialize and the key is never created.
    return `
const { app, trigger } = require('@azure/functions')

const messagesTrigger = trigger.generic({
  type: 'webPubSubTrigger',
  name: 'request',
  hub: 'booster',
  eventType: 'user',
  eventName: 'message',
  connection: 'WebPubSubConnectionString'
})

app.generic('baseWebPubSubBinding', {
  trigger: messagesTrigger,
  handler: async (request, context) => {
    return { data: 'ok' }
  }
})
`
  }

  private static getZipDeployUrl(functionAppName: string): string {
    return `https://${functionAppName}.scm.azurewebsites.net/api/zipDeploy?isAsync=true`
  }

  private static appendDefaultHostConfig(archive: archiver.Archiver): void {
    const hostConfig = {
      version: '2.0',
      extensionBundle: {
        id: 'Microsoft.Azure.Functions.ExtensionBundle',
        version: '[4.*, 5.0.0)',
      },
    }
    const hostJson = JSON.stringify(hostConfig, null, 2)
    archive.append(hostJson, {
      name: 'host.json',
    })
  }

  private static appendCustomHostConfig(archive: archiver.Archiver, hostJsonPath: string): void {
    const hostJson = fs.readFileSync(hostJsonPath, 'utf8')
    if (hostJson) {
      archive.append(hostJson, {
        name: 'host.json',
      })
    }
  }

  /**
   * Generates a package.json for the deployed function app.
   * In v4, the main field must point to the functions registration file.
   */
  private static appendPackageJson(archive: archiver.Archiver, config: BoosterConfig): void {
    // Try to get the framework-core version from the project's package.json
    let frameworkCoreVersion = '^3.0.0' // fallback version
    try {
      const projectPackageJson = JSON.parse(fs.readFileSync(path.join(process.cwd(), 'package.json'), 'utf8'))
      const version = projectPackageJson.dependencies?.['@boostercloud/framework-core']
      if (version) {
        frameworkCoreVersion = version
      }
    } catch {
      // Ignore errors, use fallback version
    }

    const packageJson = {
      name: config.appName,
      version: '1.0.0',
      main: 'functions.js',
      dependencies: {
        '@azure/functions': '^4.0.0',
        '@boostercloud/framework-core': frameworkCoreVersion,
      },
    }
    archive.append(JSON.stringify(packageJson, null, 2), {
      name: 'package.json',
    })
  }

  /**
   * @deprecated This method uses the legacy v3 function.json approach.
   * Use copyZip(config, fileName) for v4 programming model.
   * This method is kept for backward compatibility with rockets.
   */
  static async copyZipLegacy(
    functionDefinitions: Array<FunctionDefinition>,
    fileName: string,
    hostJsonPath?: string
  ): Promise<ZipResource> {
    const zipPath = await this.createZipLegacy(functionDefinitions, fileName, hostJsonPath)
    const originFile = path.basename(zipPath)
    const destinationFile = path.join(process.cwd(), originFile)
    fs.copyFileSync(zipPath, destinationFile)

    return { name: 'functions', path: destinationFile, fileName: originFile }
  }

  /**
   * @deprecated Legacy v3 function.json approach for rockets
   */
  private static async createZipLegacy(
    functionDefinitions: Array<FunctionDefinition>,
    fileName: string,
    hostJsonPath?: string
  ): Promise<string> {
    const output = fs.createWriteStream(path.join(os.tmpdir(), fileName))

    const archive = archiver('zip', {
      zlib: { level: 9 },
    })

    archive.pipe(output)
    archive.directory('.deploy', false)
    functionDefinitions.forEach((functionDefinition: FunctionDefinition) => {
      archive.append(JSON.stringify(functionDefinition.config, null, 2), {
        name: functionDefinition.name + '/function.json',
      })
    })
    if (hostJsonPath) {
      this.appendCustomHostConfig(archive, hostJsonPath)
    } else {
      if (!fs.existsSync(path.join('.deploy', 'host.json'))) {
        this.appendDefaultHostConfig(archive)
      }
    }
    await archive.finalize()

    return new Promise((resolve, reject) => {
      output.on('close', () => {
        resolve(output.path as string)
      })

      output.on('end', () => {
        resolve(output.path as string)
      })

      archive.on('warning', (err: { code?: string }) => {
        if (err.code === 'ENOENT') {
          resolve(output.path as string)
        } else {
          reject(err)
        }
      })

      archive.on('error', (err: Error) => {
        reject(err)
      })
    })
  }
}
