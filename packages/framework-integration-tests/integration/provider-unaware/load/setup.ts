import { getProviderTestHelper, setEnvironment } from '../../helper/app-helper'
import { ApplicationTester } from '@boostercloud/application-tester'
import * as path from 'path'
import { ArtilleryExecutor } from './artillery-executor'

const loadTestsFolder = path.join(__dirname, 'scripts')
export let scriptExecutor: ArtilleryExecutor
export let applicationUnderTest: ApplicationTester

before(async () => {
  await setEnvironment()
  const providerTestHelper = await getProviderTestHelper()
  scriptExecutor = new ArtilleryExecutor(loadTestsFolder, providerTestHelper)
  await scriptExecutor.ensureDeployed()
  applicationUnderTest = new ApplicationTester(providerTestHelper)
})
