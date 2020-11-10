import { ISDK, Mode, SdkProvider } from 'aws-cdk'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { BoosterConfig } from '@boostercloud/framework-types'
import { App } from '@aws-cdk/core'
import { ApplicationStackBuilder } from './stacks/application-stack'
import { CloudAssembly, Environment } from '@aws-cdk/cx-api'
import { InfrastructureRocket } from '../rockets/infrastructure-rocket'
import { Configuration } from 'aws-cdk/lib/settings'
import { CloudExecutable } from 'aws-cdk/lib/api/cxapp/cloud-executable'
import { CloudFormationDeployments } from 'aws-cdk/lib/api/cloudformation-deployments'

interface StackServiceConfiguration {
  sdk: ISDK
  environment: Environment
  cdkToolkit: CdkToolkit
}

function assemble(config: BoosterConfig, rockets?: InfrastructureRocket[]): CloudAssembly {
  const boosterApp = new App()
  new ApplicationStackBuilder(config).buildOn(boosterApp, rockets)
  // Here we could add other optional stacks like one with a lot of dashboards for analytics, etc.

  return boosterApp.synth()
}

export function getStackNames(config: BoosterConfig): Array<string> {
  return [config.resourceNames.applicationStack]
}

export function getStackToolkitName(config: BoosterConfig): string {
  return config.appName + '-toolkit'
}

export function getStackToolkitBucketName(config: BoosterConfig): string {
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

// Configure the SDK and CDKToolkit that contains all the information
// about the application we want to deploy
export async function getStackServiceConfiguration(
  config: BoosterConfig,
  rockets?: InfrastructureRocket[]
): Promise<StackServiceConfiguration> {
  const sdkProvider = await SdkProvider.withAwsCliCompatibleDefaults()
  const environment = await getEnvironment(sdkProvider)
  const sdk = await sdkProvider.forEnvironment(environment, Mode.ForWriting)
  const configuration = await new Configuration().load()
  const cloudExecutable = new CloudExecutable({
    configuration,
    sdkProvider,
    synthesizer: async (): Promise<CloudAssembly> => assemble(config, rockets),
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
