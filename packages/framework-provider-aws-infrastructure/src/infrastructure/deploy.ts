import { RequireApproval } from 'aws-cdk/lib/diff'
import { bootstrapEnvironment, SDK, DeployStackResult } from 'aws-cdk'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { Environment } from '@aws-cdk/cx-api'
import { getStackServiceConfiguration } from './stack-service-configuration'
import { InfrastructureRocket } from '../rockets/infrastructure-rocket'

/**
 * Deploys the application using the credentials located in ~/.aws
 */
export async function deploy(config: BoosterConfig, logger: Logger, rockets?: InfrastructureRocket[]): Promise<void> {
  const { aws, appStacks, cdkToolkit } = await getStackServiceConfiguration(config, rockets)

  const toolkitStackName = await bootstrap(logger, config, aws)

  await cdkToolkit.deploy({
    toolkitStackName: toolkitStackName,
    stackNames: (await appStacks.listStacks()).map((s): string => s.stackName),
    requireApproval: RequireApproval.Never,
    sdk: aws,
  })
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
