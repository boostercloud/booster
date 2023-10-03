import { BoosterConfig } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { emptyS3Bucket } from './s3utils'
import * as colors from 'colors'
import { getStackNames, getStackToolkitBucketName, getStackToolkitName } from './stack-tools'
import { InfrastructureRocket } from '../rockets/infrastructure-rocket'
import { buildRocketUtils } from '../rockets/rocket-utils'
import { exec } from 'child-process-promise'

/**
 * Nuke all the resources used in the "AppStacks"
 */
export async function nuke(config: BoosterConfig, rockets?: InfrastructureRocket[]): Promise<void> {
  const logger = getLogger(config, 'nuke')
  logger.info(colors.yellow('Destroying application') + ' ' + colors.blue(config.appName))

  await nukeToolkit(config)
  if (rockets) await nukeRockets(config, rockets)
  await nukeApplication(config)
  logger.info('✅  ' + colors.blue(config.appName) + colors.red(': DESTROYED'))
}

/**
 * Nuke all the resources used in the "Toolkit Stack"
 */
async function nukeToolkit(config: BoosterConfig): Promise<void> {
  const logger = getLogger(config, 'nuke#nukeToolkit')
  const stackToolkitName = getStackToolkitName(config)
  logger.info(colors.blue(stackToolkitName) + colors.yellow(': destroying...'))
  await emptyS3Bucket(config, getStackToolkitBucketName(config))

  await exec(`npx cdk destroy ${stackToolkitName} -f`)
  logger.info('✅  ' + colors.blue(stackToolkitName) + colors.red(': DESTROYED'))
}

/**
 * Calls to the rockets unmount method to allow them remove any resources that can't be automatically deleted by the stack (like non-empty S3 buckets)
 */
async function nukeRockets(config: BoosterConfig, rockets: InfrastructureRocket[]): Promise<void> {
  const logger = getLogger(config, 'nuke#nukeRockets')
  logger.info('Deleting rockets resources...')
  const rocketUtils = buildRocketUtils(config)
  rockets.forEach((rocket) => rocket.unmountStack?.(rocketUtils))
}

/**
 * Nuke the application resources
 */
async function nukeApplication(config: BoosterConfig): Promise<void> {
  const logger = getLogger(config, 'nuke#nukeApplication')
  logger.info('Destroying the application stack...')

  const stackNames = getStackNames(config).join(' ')

  await exec(`npx cdk destroy ${stackNames} -f`)
  logger.info('✅  ' + colors.blue(config.appName) + colors.red(': DESTROYED'))
}
