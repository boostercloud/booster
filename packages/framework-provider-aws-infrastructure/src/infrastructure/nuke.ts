import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { Mode, SDK } from 'aws-cdk'
import { emptyS3Bucket } from './s3utils'
import * as colors from 'colors'
import { getStackServiceConfiguration } from './stack-service-configuration'
import { AppStacks } from 'aws-cdk/lib/api/cxapp/stacks'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'

/**
 * Nuke all the resources used in the "AppStacks"
 */
export async function nuke(config: BoosterConfig, logger: Logger): Promise<void> {
  logger.info(colors.yellow('Destroying application') + ' ' + colors.blue(config.appName))
  const { aws, appStacks, cdkToolkit } = await getStackServiceConfiguration(config, [])

  await nukeToolkit(config, logger, aws)
  await nukeApplication(aws, appStacks, cdkToolkit, logger)
  logger.info('✅  ' + colors.blue(config.appName) + colors.red(': DESTROYED'))
}

/**
 * Nuke all the resources used in the "Toolkit Stack"
 */
async function nukeToolkit(config: BoosterConfig, logger: Logger, aws: SDK): Promise<void> {
  logger.info('Deleting the toolkit Stack...')
  const stackName = config.appName + '-toolkit'
  await emptyS3Bucket(logger, config.appName + '-toolkit-bucket', aws)
  await emptyS3Bucket(logger, config.resourceNames.staticWebsite, aws)
  const cloudFormation = await aws.cloudFormation(
    await aws.defaultAccount(),
    await aws.defaultRegion(),
    Mode.ForWriting
  )
  await cloudFormation.deleteStack({ StackName: stackName }).promise()
}

async function nukeApplication(aws: SDK, appStacks: AppStacks, cdkToolkit: CdkToolkit, logger: Logger): Promise<void> {
  logger.info('Destroying the application stack...')
  await cdkToolkit.destroy({
    stackNames: (await appStacks.listStacks()).map((s): string => s.stackName),
    exclusively: false,
    force: true,
    sdk: aws,
  })
}
