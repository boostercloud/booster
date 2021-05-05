import { ApplicationTester } from '@boostercloud/application-tester'
import { applicationName, setEnv } from '../../../helper/app-helper'
import { AWSTestHelper } from '@boostercloud/framework-provider-aws-infrastructure'

export let applicationUnderTest: ApplicationTester

before(async () => {
  await setEnv()
  applicationUnderTest = new ApplicationTester(await AWSTestHelper.build(applicationName()))
})
