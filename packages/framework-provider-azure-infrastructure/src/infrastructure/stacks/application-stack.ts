import { BoosterConfig } from '@boostercloud/framework-types'
import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'
import { DeploymentExtended } from 'azure-arm-resource/lib/resource/models'
import webSiteManagement from 'azure-arm-website'
import { configuration } from '../params'
import { eventStorePartitionKeyAttribute } from '@boostercloud/framework-provider-azure'

const fs = require('fs')
const archiver = require('archiver')
const os = require('os')
const needle = require('needle')
const uuid = require('uuid')

export class ApplicationStackBuilder {
  public constructor(readonly config: BoosterConfig) {}

  public async buildOn(
    resourceManagementClient: ResourceManagementClient,
    webSiteManagementClient: webSiteManagement,
    resourceGroupName: string
  ): Promise<void> {
    const storageAccountDeployment = await this.buildResource(
      resourceManagementClient,
      resourceGroupName,
      {},
      '../templates/storage-account.json'
    )
    const functionAppDeployment = await this.buildResource(
      resourceManagementClient,
      resourceGroupName,
      {
        storageAccountName: {
          // @ts-ignore
          value: storageAccountDeployment.properties.outputs.storageAccountName.value,
        },
      },
      '../templates/function-app.json'
    )
    const credentials = await webSiteManagementClient.webApps.listPublishingCredentials(
      resourceGroupName,
      functionAppDeployment.properties?.outputs.functionAppName.value
    )

    const policy = `<policies>
        <inbound>
          <base />
      <set-backend-service base-url="https://${functionAppDeployment.properties?.outputs.functionAppName.value}.azurewebsites.net/api" />
      </inbound>
      <backend>
      <base />
      </backend>
      <outbound>
      <base />
      </outbound>
      <on-error>
      <base />
      </on-error>
      </policies>`

    const apiManagementServiceDeployment = await this.buildResource(
      resourceManagementClient,
      resourceGroupName,
      {
        publisherEmail: { value: configuration.publisherEmail },
        publisherName: { value: configuration.publisherName },
        apiName: { value: this.config.appName + '-rest-api' },
        apiDisplayName: { value: this.config.appName + '-rest-api' },
        apiPath: { value: '/' + this.config.environmentName },
        policy: { value: policy },
      },
      '../templates/api-management.json'
    )

    // get current app settings
    const appSettings = await webSiteManagementClient.webApps.listApplicationSettings(
      resourceGroupName,
      functionAppDeployment.properties?.outputs.functionAppName.value
    )

    // add new app settings
    // @ts-ignore
    appSettings.properties.BOOSTER_ENV = this.config.environmentName
    // @ts-ignore
    appSettings.properties.BOOSTER_REST_API_URL = `https://${apiManagementServiceDeployment.properties?.outputs.apiManagementServiceName.value}.azure-api.net/${this.config.environmentName}`

    // update app settings
    await webSiteManagementClient.webApps.updateApplicationSettings(
      resourceGroupName,
      functionAppDeployment.properties?.outputs.functionAppName.value,
      {
        properties: appSettings.properties,
      }
    )

    const zipPath = await this.packageAzureFunction('graphql', {
      bindings: [
        {
          authLevel: 'anonymous',
          type: 'httpTrigger',
          direction: 'in',
          name: 'rawRequest',
          methods: ['post'],
        },
        {
          type: 'http',
          direction: 'out',
          name: '$return',
        },
      ],
      scriptFile: '../dist/index.js',
      entryPoint: 'boosterServeGraphQL',
    })

    // @ts-ignore
    const deployResponse = await this.deployFunctionPackage(
      zipPath,
      credentials.publishingUserName,
      credentials.publishingPassword,
      credentials.name
    )

    await this.buildResource(
      resourceManagementClient,
      resourceGroupName,
      {
        databaseName: { value: this.config.resourceNames.applicationStack },
        containerName: { value: this.config.resourceNames.eventsStore },
        partitionKey: { value: eventStorePartitionKeyAttribute },
      },
      '../templates/cosmos-db-account.json'
    )

    // @ts-ignore
    console.log(appSettings.properties.BOOSTER_ENV)
    // @ts-ignore
    console.log(appSettings.properties.BOOSTER_REST_API_URL)
  }

  /**
   * Deploys an Azure resource to a resource group.
   *
   * @param {ResourceManagementClient} resourceManagementClient A ResourceManagementClient instance
   * @param {string} resourceGroupName The resource group where the resource will be deployed to
   * @param {object} parameters A JSON object with parameters for the ARM template
   * @param {string} templatePath The path of the ARM template JSON file
   *
   * @returns {Promise<DeploymentExtended>}
   */
  private async buildResource(
    resourceManagementClient: ResourceManagementClient,
    resourceGroupName: string,
    parameters: object,
    templatePath: string
  ): Promise<DeploymentExtended> {
    const template = require(templatePath)

    const deploymentParameters = {
      properties: {
        parameters: parameters,
        template: template,
        mode: 'Incremental',
      },
    }

    return resourceManagementClient.deployments.createOrUpdate(
      resourceGroupName,
      'booster-deployment-' + uuid.v1(),
      deploymentParameters
    )
  }

  private async packageAzureFunction(functionName: string, functionConfig: object): Promise<any> {
    const output = fs.createWriteStream(os.tmpdir() + '/example.zip')
    const archive = archiver('zip', {
      zlib: { level: 9 }, // Sets the compression level.
    })

    archive.pipe(output)
    archive.glob('**/*')
    archive.append(JSON.stringify(functionConfig, null, 2), { name: functionName + '/function.json' })
    archive.finalize()

    return new Promise((resolve, reject) => {
      output.on('close', () => {
        console.log(archive.pointer() + ' total bytes')
        console.log('archiver has been finalized and the output file descriptor has closed.')
        resolve(output.path)
      })

      output.on('end', () => {
        console.log('Data has been drained')
        resolve()
      })

      archive.on('warning', (err: any) => {
        if (err.code === 'ENOENT') {
          console.error(err.message)
          resolve()
        } else {
          reject(err)
        }
      })

      archive.on('error', (err: any) => {
        reject(err)
      })
    })
  }

  private async deployFunctionPackage(
    packagePath: string,
    username: string,
    password: string | undefined,
    functionAppName: string | undefined
  ): Promise<any> {
    return needle(
      'post',
      `https://${functionAppName}.scm.azurewebsites.net/api/zipDeploy`,
      fs.createReadStream(packagePath),
      {
        username: username,
        password: password,
      }
    )
  }
}
