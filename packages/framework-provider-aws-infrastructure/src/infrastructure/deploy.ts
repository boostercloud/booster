import { RequireApproval } from 'aws-cdk/lib/diff'
import { Bootstrapper } from 'aws-cdk'
import { BoosterConfig } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
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
export async function deploy(config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> {
  const logger = getLogger(config, 'deploy')
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
  const stackNames = getStackNames(config)
  await cdkToolkit.deploy({
    toolkitStackName,
    selector: { patterns: stackNames },
    requireApproval: RequireApproval.Never,
  })
}
