import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'
import { DeploymentExtended } from 'azure-arm-resource/lib/resource/models'

import * as fs from 'fs'
import * as os from 'os'
import * as uuid from 'uuid'
import * as path from 'path'
import * as archiver from 'archiver'
import * as needle from 'needle'

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
 * @param {string} armTemplate ARM template JSON
 *
 * @returns {Promise<DeploymentExtended>}
 */
export async function buildResource(
  resourceManagementClient: ResourceManagementClient,
  resourceGroupName: string,
  parameters: object,
  armTemplate: object
): Promise<DeploymentExtended> {
  const deploymentParameters = {
    properties: {
      parameters: parameters,
      template: armTemplate,
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
  archive.finalize()

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

export async function deployFunctionPackage(
  packagePath: string,
  username: string,
  password: string,
  functionAppName: string
): Promise<any> {
  needle.defaults({
    open_timeout: 0,
  })

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
