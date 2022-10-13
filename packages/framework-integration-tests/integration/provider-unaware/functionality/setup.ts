import { ApplicationTester } from '@boostercloud/application-tester'
import { getProviderTestHelper, setEnvironment } from '../../helper/app-helper'

export let applicationUnderTest: ApplicationTester

before(async () => {
  await setEnvironment()
  applicationUnderTest = new ApplicationTester(await getProviderTestHelper())
})
