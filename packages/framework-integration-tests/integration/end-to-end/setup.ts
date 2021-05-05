import { setEnv, applicationName } from '../providers/aws/utils'
import { ApplicationTester, ProviderTestHelper } from '@boostercloud/application-tester'
import { AWSTestHelper } from '@boostercloud/framework-provider-aws-infrastructure'

export let applicationUnderTest: ApplicationTester

before(async () => {
  // Make sure that the AWS environment is ready to run the tests
  await setEnv()
  applicationUnderTest = new ApplicationTester(await getProviderTestHelper())
})

async function getProviderTestHelper(): Promise<ProviderTestHelper> {
  const provider = process.env.E2E_PROVIDER
  const providerHelpers: Record<string, () => Promise<ProviderTestHelper>> = {
    AWS: () => AWSTestHelper.build(applicationName()),
  }
  const supportedProviders = Object.keys(providerHelpers)
  if (!provider || !supportedProviders.includes(provider)) {
    throw new Error(
      `Invalid provider to run tests. Environment variable E2E_PROVIDER is ${provider} and the supported ones are [${supportedProviders}]`
    )
  }

  return providerHelpers[provider]()
}
