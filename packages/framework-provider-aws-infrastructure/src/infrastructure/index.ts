import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { ApplicationStackBuilder } from './stacks/application-stack'
import { App } from '@aws-cdk/core'
import { CloudAssembly, Environment } from '@aws-cdk/cx-api'
import { AppStacks } from 'aws-cdk/lib/api/cxapp/stacks'
import { Configuration } from 'aws-cdk/lib/settings'
import { bootstrapEnvironment, DeployStackResult, Mode, SDK } from 'aws-cdk'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { CloudFormationDeploymentTarget } from 'aws-cdk/lib/api/deployment-target'
import { RequireApproval } from 'aws-cdk/lib/diff'
import * as colors from 'colors'
import { emptyS3Bucket } from './s3utils'
import { InfrastructurePlugin } from '@boostercloud/framework-provider-aws-infrastructure/src/infrastructure-plugin'

interface StackServiceConfiguration {
  aws: SDK
  appStacks: AppStacks
  cdkToolkit: CdkToolkit
}

async function getEnvironment(aws: SDK): Promise<Environment> {
  const account = await aws.defaultAccount()
  const region = await aws.defaultRegion()

  if (!account) {
    throw new Error(
      'Unable to load default AWS account. Check that you have properly set your AWS credentials in `~/.aws/credentials` file or the corresponding environment variables. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-credentials-node.html'
    )
  }
  if (!region) {
    throw new Error(
      'Unable to determine default region. Check that you have set it in your `~/.aws/config` file or AWS_REGION environment variable. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-region.html#setting-region-order-of-precedence'
    )
  }

  return {
    name: 'Default environment',
    account,
    region,
  }
}

function bootstrapResultToMessage(result: DeployStackResult): string {
  return `Environment ${result.stackArn} bootstrapped${result.noOp ? ' (no changes).' : '.'}`
}

async function bootstrap(logger: Logger, config: BoosterConfig, aws: SDK): Promise<string> {
  const toolkitStackName: string = config.appName + '-toolkit'

  const env: Environment = await getEnvironment(aws)
  logger.info('Bootstraping the following environment: ' + JSON.stringify(env))
  const result = await bootstrapEnvironment(env, aws, toolkitStackName, undefined, {
    bucketName: config.appName + '-toolkit-bucket',
  })
  logger.info(bootstrapResultToMessage(result))
  return toolkitStackName
}

// Exported for testing
export function assemble(config: BoosterConfig, plugins?: InfrastructurePlugin[]): CloudAssembly {
  const boosterApp = new App()
  new ApplicationStackBuilder(config, plugins).buildOn(boosterApp)
  // Here we could add other optional stacks like one with a lot of dashboards for analytics, etc.

  return boosterApp.synth()
}

/**
 * Deploys the application using the credentials located in ~/.aws
 */
async function deployApp(config: BoosterConfig, logger: Logger, plugins?: InfrastructurePlugin[]): Promise<void> {
  const { aws, appStacks, cdkToolkit } = await getStackServiceConfiguration(config, plugins)

  const toolkitStackName = await bootstrap(logger, config, aws)

  return cdkToolkit.deploy({
    toolkitStackName: toolkitStackName,
    stackNames: (await appStacks.listStacks()).map((s): string => s.stackName),
    requireApproval: RequireApproval.Never,
    sdk: aws,
  })
}

// Configure the SDK and the "AppStacks" that contains all the information
// about the application we want to deploy
async function getStackServiceConfiguration(
  config: BoosterConfig,
  plugins?: InfrastructurePlugin[]
): Promise<StackServiceConfiguration> {
  const aws = new SDK()
  const configuration = await new Configuration().load()
  const appStacks = new AppStacks({
    configuration,
    aws,
    synthesizer: async (): Promise<CloudAssembly> => assemble(config, plugins),
  })
  const cdkToolkit = new CdkToolkit({
    appStacks,
    provisioner: new CloudFormationDeploymentTarget({ aws }),
  })
  return {
    aws,
    appStacks,
    cdkToolkit,
  }
}

/**
 * Nuke all the resources used in the "AppStacks"
 */
async function nukeApp(config: BoosterConfig, logger: Logger): Promise<void> {
  const { aws, appStacks, cdkToolkit } = await getStackServiceConfiguration(config, [])
  const toolkit = nukeToolkit(config, logger, aws)
  const app = cdkToolkit.destroy({
    stackNames: (await appStacks.listStacks()).map((s): string => s.stackName),
    exclusively: false,
    force: true,
    sdk: aws,
  })
  await Promise.all([toolkit, app])
}

/**
 * Nuke all the resources used in the "Toolkit Stack"
 */
async function nukeToolkit(config: BoosterConfig, logger: Logger, aws: SDK): Promise<void> {
  const stackName = config.appName + '-toolkit'
  logger.info(colors.blue(stackName) + colors.yellow(': destroying...'))
  await emptyS3Bucket(logger, config.appName + '-toolkit-bucket', aws)
  await emptyS3Bucket(logger, config.resourceNames.staticWebsite, aws)
  const cloudFormation = await aws.cloudFormation(
    await aws.defaultAccount(),
    await aws.defaultRegion(),
    Mode.ForWriting
  )
  await cloudFormation.deleteStack({ StackName: stackName }).promise()
  logger.info('✅  ' + colors.blue(stackName) + colors.red(': DESTROYED'))
}

export const deploy = (config: BoosterConfig, logger: Logger, plugins?: InfrastructurePlugin[]): Promise<void> =>
  deployApp(config, logger, plugins).catch(logger.error)

export const nuke = (config: BoosterConfig, logger: Logger): Promise<void> =>
  nukeApp(config, logger).catch(logger.error)
