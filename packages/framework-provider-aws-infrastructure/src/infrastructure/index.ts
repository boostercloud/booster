import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { ApplicationStackBuilder } from './stacks/application-stack'
import { App } from '@aws-cdk/core'
import { CloudAssembly, Environment, EnvironmentUtils } from '@aws-cdk/cx-api'
import { Configuration } from 'aws-cdk/lib/settings'
import { Bootstrapper, ISDK, Mode, SdkProvider } from 'aws-cdk'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { RequireApproval } from 'aws-cdk/lib/diff'
import * as colors from 'colors'
import { emptyS3Bucket } from './s3utils'
import { CloudExecutable } from 'aws-cdk/lib/api/cxapp/cloud-executable'
import { CloudFormationDeployments } from 'aws-cdk/lib/api/cloudformation-deployments'

interface StackServiceConfiguration {
  sdk: ISDK
  environment: Environment
  cdkToolkit: CdkToolkit
}

function getStackNames(config: BoosterConfig): Array<string> {
  return [config.resourceNames.applicationStack]
}

function getStackToolkitName(config: BoosterConfig): string {
  return config.appName + '-toolkit'
}

function getStackToolkitBucketName(config: BoosterConfig): string {
  return config.appName + '-toolkit-bucket'
}

async function getEnvironment(sdkProvider: SdkProvider): Promise<Environment> {
  const account = await sdkProvider.defaultAccount()
  const region = sdkProvider.defaultRegion

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
    account: account.accountId,
    region,
  }
}

function assemble(config: BoosterConfig): CloudAssembly {
  const boosterApp = new App()
  new ApplicationStackBuilder(config).buildOn(boosterApp)
  // Here we could add other optional stacks like one with a lot of dashboards for analytics, etc.

  return boosterApp.synth()
}

/**
 * Deploys the application using the credentials located in ~/.aws
 */
async function deployApp(logger: Logger, config: BoosterConfig): Promise<void> {
  const { environment: env, cdkToolkit } = await getStackServiceConfiguration(config)
  const toolkitStackName = getStackToolkitName(config)
  await cdkToolkit.bootstrap(
    [EnvironmentUtils.format(env.account, env.region)],
    new Bootstrapper({ source: 'legacy' }),
    {
      toolkitStackName,
      parameters: {
        bucketName: getStackToolkitBucketName(config),
      },
      terminationProtection: false,
    }
  )

  return cdkToolkit.deploy({
    toolkitStackName,
    stackNames: getStackNames(config),
    requireApproval: RequireApproval.Never,
  })
}

// Configure the SDK and CDKToolkit that contains all the information
// about the application we want to deploy
async function getStackServiceConfiguration(config: BoosterConfig): Promise<StackServiceConfiguration> {
  const sdkProvider = await SdkProvider.withAwsCliCompatibleDefaults()
  const environment = await getEnvironment(sdkProvider)
  const sdk = await sdkProvider.forEnvironment(environment, Mode.ForWriting)
  const configuration = await new Configuration().load()
  const cloudExecutable = new CloudExecutable({
    configuration,
    sdkProvider,
    synthesizer: (): Promise<CloudAssembly> => Promise.resolve(assemble(config)),
  })
  const cdkToolkit = new CdkToolkit({
    sdkProvider,
    cloudExecutable,
    cloudFormation: new CloudFormationDeployments({ sdkProvider }),
    configuration,
  })
  return {
    sdk,
    environment,
    cdkToolkit,
  }
}

/**
 * Nuke all the resources used in the "AppStacks"
 */
async function nukeApp(logger: Logger, config: BoosterConfig): Promise<void> {
  const { sdk, cdkToolkit } = await getStackServiceConfiguration(config)
  const toolkit = nukeToolkit(logger, config, cdkToolkit, sdk)
  const app = cdkToolkit.destroy({
    stackNames: getStackNames(config),
    exclusively: false,
    force: true,
  })
  await Promise.all([toolkit, app])
}

/**
 * Nuke all the resources used in the "Toolkit Stack"
 */
async function nukeToolkit(logger: Logger, config: BoosterConfig, cdkToolkit: CdkToolkit, sdk: ISDK): Promise<void> {
  const stackToolkitName = getStackToolkitName(config)
  logger.info(colors.blue(stackToolkitName) + colors.yellow(': destroying...'))
  await emptyS3Bucket(logger, getStackToolkitBucketName(config), sdk)
  await emptyS3Bucket(logger, config.resourceNames.staticWebsite, sdk)

  await sdk
    .cloudFormation()
    .deleteStack({ StackName: stackToolkitName })
    .promise()
  logger.info('✅  ' + colors.blue(stackToolkitName) + colors.red(': DESTROYED'))
}

export const deploy = (config: BoosterConfig, logger: Logger): Promise<void> =>
  deployApp(logger, config).catch(logger.error)

export const nuke = (config: BoosterConfig, logger: Logger): Promise<void> =>
  nukeApp(logger, config).catch(logger.error)
