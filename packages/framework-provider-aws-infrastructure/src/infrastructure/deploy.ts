import { RequireApproval } from 'aws-cdk/lib/diff'
import { Bootstrapper } from 'aws-cdk'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { EnvironmentUtils } from '@aws-cdk/cx-api'
import {
  getStackNames,
  getStackServiceConfiguration,
  getStackToolkitBucketName,
  getStackToolkitName,
} from './stack-tools'
import { InfrastructureRocket } from '../rockets/infrastructure-rocket'

/**
 * Deploys the application using the credentials located in ~/.aws
 */
export async function deploy(config: BoosterConfig, logger: Logger, rockets?: InfrastructureRocket[]): Promise<void> {
  const { environment: env, cdkToolkit } = await getStackServiceConfiguration(config, rockets)
  const toolkitStackName = getStackToolkitName(config)

  logger.info('Bootstraping the following environment: ' + JSON.stringify(env))
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

  logger.info(`Deploying ${config.appName} on environment ${config.environmentName}`)
  await cdkToolkit.deploy({
    toolkitStackName,
    stackNames: getStackNames(config),
    requireApproval: RequireApproval.Never,
  })
}
