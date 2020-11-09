import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { ISDK } from 'aws-cdk'
import { emptyS3Bucket } from './s3utils'
import * as colors from 'colors'
import {
  getStackNames,
  getStackServiceConfiguration,
  getStackToolkitBucketName,
  getStackToolkitName,
} from './stack-tools'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { InfrastructureRocket } from '../rockets/infrastructure-rocket'
import { buildRocketUtils } from '../rockets/rocket-utils'

/**
 * Nuke all the resources used in the "AppStacks"
 */
export async function nuke(config: BoosterConfig, logger: Logger, rockets?: InfrastructureRocket[]): Promise<void> {
  logger.info(colors.yellow('Destroying application') + ' ' + colors.blue(config.appName))
  const { sdk, cdkToolkit } = await getStackServiceConfiguration(config, [])

  await nukeToolkit(logger, config, sdk)
  if (rockets) await nukeRockets(logger, sdk, rockets)
  await nukeApplication(logger, config, cdkToolkit)
  logger.info('✅  ' + colors.blue(config.appName) + colors.red(': DESTROYED'))
}

/**
 * Nuke all the resources used in the "Toolkit Stack"
 */
async function nukeToolkit(logger: Logger, config: BoosterConfig, sdk: ISDK): Promise<void> {
  const stackToolkitName = getStackToolkitName(config)
  logger.info(colors.blue(stackToolkitName) + colors.yellow(': destroying...'))
  await emptyS3Bucket(sdk, logger, getStackToolkitBucketName(config))

  await sdk
    .cloudFormation()
    .deleteStack({ StackName: stackToolkitName })
    .promise()
  logger.info('✅  ' + colors.blue(stackToolkitName) + colors.red(': DESTROYED'))
}

/**
 * Calls to the rockets unmount method to allow them remove any resources that can't be automatically deleted by the stack (like non-empty S3 buckets)
 */
async function nukeRockets(logger: Logger, sdk: ISDK, rockets: InfrastructureRocket[]): Promise<void> {
  logger.info('Deleting rockets resources...')
  const rocketUtils = buildRocketUtils(sdk, logger)
  rockets.forEach((rocket) => rocket.unmountStack?.(rocketUtils))
}

/**
 * Nuke the application resources
 */
async function nukeApplication(logger: Logger, config: BoosterConfig, cdkToolkit: CdkToolkit): Promise<void> {
  logger.info('Destroying the application stack...')
  await cdkToolkit.destroy({
    stackNames: getStackNames(config),
    exclusively: false,
    force: true,
  })
}
