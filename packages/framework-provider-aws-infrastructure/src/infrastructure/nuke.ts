import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import * as colors from 'colors'
import { Rocket } from '@boostercloud/rockets-base'
import { AWSProviderContext } from './provider-context/aws-provider-context'

/**
 * Nuke all the resources used in the "AppStacks"
 */
export async function nuke(
  config: BoosterConfig,
  logger: Logger,
  rockets?: Rocket<AWSProviderContext>[]
): Promise<void> {
  logger.info(colors.yellow('Destroying application') + ' ' + colors.blue(config.appName))
  const awsProviderContext = await AWSProviderContext.build(logger, config, rockets)
  await nukeToolkit(logger, config, awsProviderContext)
  if (rockets) await nukeRockets(logger, awsProviderContext, rockets)
  await nukeApplication(logger, config, awsProviderContext)
  logger.info('✅  ' + colors.blue(config.appName) + colors.red(': DESTROYED'))
}

/**
 * Nuke all the resources used in the "Toolkit Stack"
 */
async function nukeToolkit(
  logger: Logger,
  config: BoosterConfig,
  awsProviderContext: AWSProviderContext
): Promise<void> {
  const stackToolkitName = awsProviderContext.utils.stack.toolkitName
  logger.info(colors.blue(stackToolkitName) + colors.yellow(': destroying...'))
  await awsProviderContext.utils.s3.emptyBucket(awsProviderContext.utils.stack.toolkitBucketName)

  await awsProviderContext.sdk.cloudFormation().deleteStack({ StackName: stackToolkitName }).promise()
  logger.info('✅  ' + colors.blue(stackToolkitName) + colors.red(': DESTROYED'))
}

/**
 * Calls to the rockets unmount method to allow them remove any resources that can't be automatically deleted by the stack (like non-empty S3 buckets)
 */
async function nukeRockets(
  logger: Logger,
  awsProviderContext: AWSProviderContext,
  rockets: Rocket<AWSProviderContext>[]
): Promise<void> {
  logger.info('Deleting rockets resources...')
  rockets.forEach((rocket) => rocket.unmount?.(awsProviderContext))
}

/**
 * Nuke the application resources
 */
async function nukeApplication(
  logger: Logger,
  config: BoosterConfig,
  awsProviderContext: AWSProviderContext
): Promise<void> {
  logger.info('Destroying the application stack...')
  if (!awsProviderContext.cdkToolkit) {
    throw 'It was not possible to initialize '
  }
  await awsProviderContext.cdkToolkit.destroy({
    stackNames: awsProviderContext.utils.stack.names,
    exclusively: false,
    force: true,
  })
}
