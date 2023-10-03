import { BoosterConfig } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { getStackToolkitBucketName, getStackToolkitName } from './stack-tools'
import { InfrastructureRocket } from '../rockets/infrastructure-rocket'
import { exec } from 'child-process-promise'
import { App } from 'aws-cdk-lib'
import { ApplicationStack } from './stacks/application-stack'

/**
 * Deploys the application using the credentials located in ~/.aws
 */
export async function deploy(config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> {
  const logger = getLogger(config, 'aws-deploy#deploy')

  const boosterApp = new App()
  const stack = new ApplicationStack(config, boosterApp, rockets)

  await bootstrapEnvironment(config, stack)

  logger.info(`Deploying ${config.appName} on environment ${config.environmentName}`)
  boosterApp.synth()
}

/**
 * Bootstraps the AWS environment using the CDK v2 CLI.
 */
async function bootstrapEnvironment(config: BoosterConfig, stack: ApplicationStack): Promise<void> {
  const logger = getLogger(config, 'aws-deploy#bootstrap')
  const toolkitStackName = getStackToolkitName(config)
  const toolkitBucketName = getStackToolkitBucketName(config)

  logger.info(`Bootstrapping environment ${config.environmentName}...`)
  const bootstrapCommand =
    `cdk bootstrap aws://${stack.account}/${stack.region} ` +
    `--toolkit-stack-name ${toolkitStackName} ` +
    `--bootstrap-bucket-name ${toolkitBucketName}`

  const bootstrapResult = await exec(bootstrapCommand)
  logger.info(`Bootstrap output: ${bootstrapResult.stdout}`)
}
