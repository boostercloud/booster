import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'
import { DeploymentExtended } from 'azure-arm-resource/lib/resource/models'

const fs = require('fs')
const archiver = require('archiver')
const os = require('os')
const needle = require('needle')
const uuid = require('uuid')

interface FunctionDefinition {
  name: string
  config: object
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
export async function buildResource(
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

export async function packageAzureFunction(functionDefinitions: Array<FunctionDefinition>): Promise<any> {
  const output = fs.createWriteStream(os.tmpdir() + '/example.zip')
  const archive = archiver('zip', {
    zlib: { level: 9 }, // Sets the compression level.
  })

  archive.pipe(output)
  archive.glob('**/*')
  functionDefinitions.forEach((functionDefinition) => {
    archive.append(JSON.stringify(functionDefinition.config, null, 2), {
      name: functionDefinition.name + '/function.json',
    })
  })
  archive.finalize()

  return new Promise((resolve, reject) => {
    output.on('close', () => {
      resolve(output.path)
    })

    output.on('end', () => {
      resolve()
    })

    archive.on('warning', (err: any) => {
      if (err.code === 'ENOENT') {
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

export async function deployFunctionPackage(
  packagePath: string,
  username: string,
  password: string,
  functionAppName: string
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
