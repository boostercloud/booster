import { ApplicationTester } from '@boostercloud/application-tester'
import { getProviderTestHelper, setEnv } from '../helper/app-helper'

export let applicationUnderTest: ApplicationTester

before(async () => {
  await setEnv()
  applicationUnderTest = new ApplicationTester(await getProviderTestHelper())
})
