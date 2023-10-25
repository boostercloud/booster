import { ProviderTestHelper } from '@boostercloud/application-tester'
import { AWSTestHelper } from '@boostercloud/framework-provider-aws-infrastructure'
import { AzureTestHelper } from '@boostercloud/framework-provider-azure-infrastructure'
import { LocalTestHelper } from '@boostercloud/framework-provider-local-infrastructure'
import * as util from 'util'

const exec = util.promisify(require('child_process').exec)

export function applicationName(): string {
  return `my-store-${process.env.BOOSTER_APP_SUFFIX}`
}

export async function getProviderTestHelper(): Promise<ProviderTestHelper> {
  const provider = process.env.TESTED_PROVIDER
  const environmentName = checkAndGetCurrentEnv()
  const providerHelpers: Record<string, () => Promise<ProviderTestHelper>> = {
    AWS: () => AWSTestHelper.build(applicationName()),
    AZURE: () => AzureTestHelper.build(applicationName(), environmentName),
    LOCAL: () => LocalTestHelper.build(applicationName()),
  }
  const supportedProviders = Object.keys(providerHelpers)
  if (!provider || !supportedProviders.includes(provider)) {
    throw new Error(
      `Invalid provider to run tests. Environment variable TESTED_PROVIDER is ${provider} and the supported ones are [${supportedProviders}]`
    )
  }

  return providerHelpers[provider]()
}

export async function setEnv(): Promise<void> {
  if (!process.env.BOOSTER_APP_SUFFIX) {
    // If the user doesn't set an app name suffix, use the current git commit hash
    // to build a unique suffix for the application name to avoid collisions
    // between tests from different branches.
    const { stdout } = await exec('git rev-parse HEAD')
    process.env['BOOSTER_APP_SUFFIX'] = stdout.trim().substring(0, 7)
    console.log('setting BOOSTER_APP_SUFFIX=' + process.env.BOOSTER_APP_SUFFIX)
  }
}

export function checkAndGetCurrentEnv(): string {
  const env = process.env.BOOSTER_ENV
  if (!env || env.trim().length == 0) {
    throw new Error(
      'Booster environment is missing. You need to provide an environment to configure your Booster project'
    )
  }
  return env
}
