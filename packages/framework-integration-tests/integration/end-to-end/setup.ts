import { setEnv } from '../providers/aws/utils'
import { ApplicationTester } from '@boostercloud/application-tester'
import { getProviderTestHelper } from '../helper/app-helper'

export let applicationUnderTest: ApplicationTester

before(async () => {
  await setEnv()
  applicationUnderTest = new ApplicationTester(await getProviderTestHelper())
})
