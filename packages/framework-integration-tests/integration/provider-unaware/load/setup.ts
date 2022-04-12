import { getProviderTestHelper, setEnv } from '../../helper/app-helper'
import { ApplicationTester } from '@boostercloud/application-tester'
import * as path from 'path'
import { ArtilleryExecutor } from './artillery-executor'

const loadTestsFolder = path.join(__dirname, 'scripts')
export let scriptExecutor: ArtilleryExecutor
export let applicationUnderTest: ApplicationTester

before(async () => {
  await setEnv()
  const providerTestHelper = await getProviderTestHelper()
  scriptExecutor = new ArtilleryExecutor(loadTestsFolder, providerTestHelper.outputs.graphqlURL)
  await scriptExecutor.ensureDeployed()
  applicationUnderTest = new ApplicationTester(providerTestHelper)
})
