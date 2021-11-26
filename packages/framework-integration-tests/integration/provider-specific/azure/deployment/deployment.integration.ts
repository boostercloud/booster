import { AzureTestHelper } from '@boostercloud/framework-provider-azure-infrastructure'
import { expect } from '../../../helper/expect'
import { applicationName } from '../../../helper/app-helper'

describe('After deployment', () => {
  describe('the ARM template', () => {
    it('has been created successfully', async () => {
      // The project must have been deployed by the cliHelper hook in setup.ts
      // that scripts uses the cli to do the deployment, so we just check here
      // that the resource group exists
      const environmentName = process.env.BOOSTER_ENV || ''
      await expect(AzureTestHelper.checkResourceGroup(applicationName(), environmentName)).to.be.eventually.fulfilled
    })
  })
})
