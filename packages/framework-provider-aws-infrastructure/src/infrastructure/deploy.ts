import { RequireApproval } from 'aws-cdk/lib/diff'
import { Bootstrapper } from 'aws-cdk'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { EnvironmentUtils } from '@aws-cdk/cx-api'
import { Rocket } from '@boostercloud/rockets-base'
import { AWSProviderContext } from './provider-context/aws-provider-context'

/**
 * Deploys the application using the credentials located in ~/.aws
 */
export async function deploy(
  config: BoosterConfig,
  logger: Logger,
  rockets?: Rocket<AWSProviderContext>[]
): Promise<void> {
  const awsProviderContext = await AWSProviderContext.build(logger, config, rockets)
  const toolkitStackName = awsProviderContext.utils.stack.toolkitName

  logger.info('Bootstraping the following environment: ' + JSON.stringify(awsProviderContext.environment))
  await awsProviderContext.cdkToolkit.bootstrap(
    [EnvironmentUtils.format(awsProviderContext.environment.account, awsProviderContext.environment.region)],
    new Bootstrapper({ source: 'legacy' }),
    {
      toolkitStackName,
      parameters: {
        bucketName: awsProviderContext.utils.stack.toolkitBucketName,
      },
      terminationProtection: false,
    }
  )

  logger.info(`Deploying ${config.appName} on environment ${config.environmentName}`)
  await awsProviderContext.cdkToolkit.deploy({
    toolkitStackName,
    stackNames: awsProviderContext.utils.stack.names,
    requireApproval: RequireApproval.Never,
  })
}
